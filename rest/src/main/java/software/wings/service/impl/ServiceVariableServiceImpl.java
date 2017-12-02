package software.wings.service.impl;

import static org.mongodb.morphia.mapping.Mapper.ID_KEY;
import static software.wings.beans.Base.GLOBAL_ENV_ID;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.ErrorCode.INVALID_ARGUMENT;
import static software.wings.beans.SearchFilter.Builder.aSearchFilter;
import static software.wings.beans.ServiceVariable.Type.ENCRYPTED_TEXT;
import static software.wings.dl.PageRequest.Builder.aPageRequest;
import static software.wings.service.impl.security.KmsServiceImpl.SECRET_MASK;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;

import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import software.wings.beans.EntityType;
import software.wings.beans.SearchFilter.Operator;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.yaml.Change.ChangeType;
import software.wings.beans.yaml.GitFileChange;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.exception.WingsException;
import software.wings.security.encryption.EncryptedData;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.yaml.EntityUpdateService;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.utils.ExpressionEvaluator;
import software.wings.utils.Validator;
import software.wings.yaml.gitSync.YamlGitConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.validation.Valid;

/**
 * Created by peeyushaggarwal on 9/14/16.
 */
@Singleton
public class ServiceVariableServiceImpl implements ServiceVariableService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private EntityUpdateService entityUpdateService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private YamlDirectoryService yamlDirectoryService;
  @Inject private AppService appService;
  @Inject private YamlChangeSetService yamlChangeSetService;
  @Inject private ExecutorService executorService;

  @Override
  public PageResponse<ServiceVariable> list(PageRequest<ServiceVariable> request) {
    return list(request, false);
  }

  @Override
  public PageResponse<ServiceVariable> list(PageRequest<ServiceVariable> request, boolean maskEncryptedFields) {
    PageResponse<ServiceVariable> response = wingsPersistence.query(ServiceVariable.class, request);
    if (maskEncryptedFields) {
      response.getResponse().forEach(
          serviceVariable -> processEncryptedServiceVariable(maskEncryptedFields, serviceVariable));
    }
    return response;
  }

  @Override
  public ServiceVariable save(@Valid ServiceVariable serviceVariable) {
    if (!Arrays.asList(SERVICE, EntityType.SERVICE_TEMPLATE, EntityType.ENVIRONMENT, EntityType.HOST)
             .contains(serviceVariable.getEntityType())) {
      throw new WingsException(
          INVALID_ARGUMENT, "args", "Service setting not supported for entityType " + serviceVariable.getEntityType());
    }

    ExpressionEvaluator.isValidVariableName(serviceVariable.getName());

    // TODO:: revisit. for environment envId can be specific
    String envId =
        serviceVariable.getEntityType().equals(SERVICE) || serviceVariable.getEntityType().equals(ENVIRONMENT)
        ? GLOBAL_ENV_ID
        : serviceTemplateService.get(serviceVariable.getAppId(), serviceVariable.getTemplateId()).getEnvId();

    serviceVariable.setEnvId(envId);

    ServiceVariable newServiceVariable = Validator.duplicateCheck(
        () -> wingsPersistence.saveAndGet(ServiceVariable.class, serviceVariable), "name", serviceVariable.getName());

    if (newServiceVariable == null) {
      return null;
    }

    executorService.submit(() -> queueServiceVariableYamlChangeSet(serviceVariable));
    return newServiceVariable;
  }

  @Override
  public ServiceVariable get(@NotEmpty String appId, @NotEmpty String settingId) {
    return get(appId, settingId, false);
  }

  @Override
  public ServiceVariable get(String appId, String settingId, boolean maskEncryptedFields) {
    ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, appId, settingId);
    Validator.notNullCheck("ServiceVariable", serviceVariable);
    if (maskEncryptedFields) {
      processEncryptedServiceVariable(maskEncryptedFields, serviceVariable);
    }
    return serviceVariable;
  }

  @Override
  public ServiceVariable update(@Valid ServiceVariable serviceVariable) {
    ServiceVariable savedServiceVariable = get(serviceVariable.getAppId(), serviceVariable.getUuid());
    Validator.notNullCheck("Service variable", savedServiceVariable);

    ExpressionEvaluator.isValidVariableName(serviceVariable.getName());

    wingsPersistence.updateFields(ServiceVariable.class, serviceVariable.getUuid(),
        ImmutableMap.of("value", serviceVariable.getValue(), "type", serviceVariable.getType()));

    ServiceVariable updatedServiceVariable = get(serviceVariable.getAppId(), serviceVariable.getUuid());

    if (updatedServiceVariable == null) {
      return null;
    }

    executorService.submit(() -> queueServiceVariableYamlChangeSet(serviceVariable));

    return updatedServiceVariable;
  }

  private void updateVariableNameForServiceAndOverrides(ServiceVariable existingServiceVariable, String name) {
    List<Object> templateIds = serviceTemplateService
                                   .getTemplateRefKeysByService(
                                       existingServiceVariable.getAppId(), existingServiceVariable.getEntityId(), null)
                                   .stream()
                                   .map(Key::getId)
                                   .collect(Collectors.toList());

    Query<ServiceVariable> query = wingsPersistence.createQuery(ServiceVariable.class)
                                       .field("appId")
                                       .equal(existingServiceVariable.getAppId())
                                       .field("name")
                                       .equal(existingServiceVariable.getName());
    query.or(query.criteria("entityId").equal(existingServiceVariable.getEntityId()),
        query.criteria("templateId").in(templateIds));

    UpdateOperations<ServiceVariable> updateOperations =
        wingsPersistence.createUpdateOperations(ServiceVariable.class).set("name", name);
    wingsPersistence.update(query, updateOperations);
  }

  @Override
  public void delete(@NotEmpty String appId, @NotEmpty String settingId) {
    ServiceVariable serviceVariable = get(appId, settingId);
    boolean deleted = wingsPersistence.delete(
        wingsPersistence.createQuery(ServiceVariable.class).field("appId").equal(appId).field(ID_KEY).equal(settingId));

    if (deleted) {
      executorService.submit(() -> queueServiceVariableYamlChangeSet(serviceVariable));
    }
  }

  private void queueServiceVariableYamlChangeSet(ServiceVariable serviceVariable) {
    String accountId = appService.getAccountIdByAppId(serviceVariable.getAppId());
    if (serviceVariable.getEntityType() == EntityType.SERVICE) {
      Service service = serviceResourceService.get(serviceVariable.getAppId(), serviceVariable.getEntityId());
      YamlGitConfig ygs = yamlDirectoryService.weNeedToPushChanges(accountId);
      if (ygs != null) {
        List<GitFileChange> changeSet = new ArrayList<>();
        changeSet.add(entityUpdateService.getServiceGitSyncFile(accountId, service, ChangeType.MODIFY));

        yamlChangeSetService.queueChangeSet(ygs, changeSet);
      }
    }
  }

  @Override
  public List<ServiceVariable> getServiceVariablesForEntity(
      String appId, String entityId, boolean maskEncryptedFields) {
    PageRequest<ServiceVariable> request =
        aPageRequest()
            .addFilter(aSearchFilter().withField("appId", Operator.EQ, appId).build())
            .addFilter(aSearchFilter().withField("entityId", Operator.EQ, entityId).build())
            .build();
    List<ServiceVariable> variables = wingsPersistence.query(ServiceVariable.class, request).getResponse();
    variables.forEach(serviceVariable -> processEncryptedServiceVariable(maskEncryptedFields, serviceVariable));
    return variables;
  }

  @Override
  public List<ServiceVariable> getServiceVariablesByTemplate(
      String appId, String envId, ServiceTemplate serviceTemplate, boolean maskEncryptedFields) {
    PageRequest<ServiceVariable> request =
        aPageRequest()
            .addFilter(aSearchFilter().withField("appId", Operator.EQ, appId).build())
            .addFilter(aSearchFilter().withField("envId", Operator.EQ, envId).build())
            .addFilter(aSearchFilter().withField("templateId", Operator.EQ, serviceTemplate.getUuid()).build())
            .build();
    List<ServiceVariable> variables = wingsPersistence.query(ServiceVariable.class, request).getResponse();
    variables.forEach(serviceVariable -> processEncryptedServiceVariable(maskEncryptedFields, serviceVariable));
    return variables;
  }

  @Override
  public void deleteByTemplateId(String appId, String serviceTemplateId) {
    wingsPersistence.delete(wingsPersistence.createQuery(ServiceVariable.class)
                                .field("appId")
                                .equal(appId)
                                .field("templateId")
                                .equal(serviceTemplateId));
  }

  @Override
  public void deleteByEntityId(String appId, String entityId) {
    wingsPersistence.delete(wingsPersistence.createQuery(ServiceVariable.class)
                                .field("appId")
                                .equal(appId)
                                .field("entityId")
                                .equal(entityId));
  }

  private void processEncryptedServiceVariable(boolean maskEncryptedFields, ServiceVariable serviceVariable) {
    if (serviceVariable.getType() == ENCRYPTED_TEXT) {
      if (maskEncryptedFields) {
        serviceVariable.setValue(SECRET_MASK.toCharArray());
      }
      EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, serviceVariable.getEncryptedValue());
      Preconditions.checkNotNull(encryptedData, "no encrypted ref found for " + serviceVariable.getUuid());
      serviceVariable.setSecretTextName(encryptedData.getName());
    }
  }
}
