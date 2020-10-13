package software.wings.sm.states.azure;

import static io.harness.azure.model.AzureConstants.STEADY_STATE_TIMEOUT_REGEX;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.azure.AzureVMAuthType.SSH_PUBLIC_KEY;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static software.wings.beans.Log.Builder.aLog;
import static software.wings.beans.ServiceVariable.Type.ENCRYPTED_TEXT;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;

import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.harness.beans.EncryptedData;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.context.ContextElementType;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO;
import io.harness.delegate.beans.azure.AzureVMAuthDTO;
import io.harness.delegate.beans.azure.AzureVMAuthType;
import io.harness.delegate.beans.azure.GalleryImageDefinitionDTO;
import io.harness.delegate.task.azure.response.AzureVMInstanceData;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.deployment.InstanceDetails;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import software.wings.annotation.EncryptableSetting;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Log;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.SettingAttribute;
import software.wings.beans.VMSSAuthType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.container.UserDataSpecification;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.ServiceVersionConvention;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class AzureVMSSStateHelper {
  public static final String VIRTUAL_MACHINE_SCALE_SET_ID_PATTERN =
      "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachineScaleSets/%s";
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ActivityService activityService;
  @Inject private AzureStateHelper azureStateHelper;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SettingsService settingsService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private SecretManager secretManager;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private LogService logService;

  public boolean isBlueGreenWorkflow(ExecutionContext context) {
    return BLUE_GREEN == context.getOrchestrationWorkflowType();
  }

  public AzureMachineImageArtifactDTO getAzureMachineImageArtifactDTO(
      DeploymentExecutionContext context, String serviceId) {
    Artifact artifact = getArtifact(context, serviceId);
    ArtifactStream artifactStream = getArtifactStream(artifact.getArtifactStreamId());
    ArtifactStreamAttributes artifactStreamAttributes = artifactStream.fetchArtifactStreamAttributes();

    String osType = artifactStreamAttributes.getOsType();
    String imageType = artifactStreamAttributes.getImageType();
    String galleryName = artifactStreamAttributes.getAzureImageGalleryName();
    String imageDefinitionName = artifactStreamAttributes.getAzureImageDefinition();
    String imageVersion = artifact.getRevision();

    return AzureMachineImageArtifactDTO.builder()
        .imageOSType(AzureMachineImageArtifactDTO.OSType.valueOf(osType))
        .imageType(AzureMachineImageArtifactDTO.ImageType.valueOf(imageType))
        .imageDefinition(GalleryImageDefinitionDTO.builder()
                             .definitionName(imageDefinitionName)
                             .galleryName(galleryName)
                             .version(imageVersion)
                             .build())
        .build();
  }

  public Artifact getArtifact(DeploymentExecutionContext context, String serviceId) {
    return Optional.ofNullable(context.getDefaultArtifactForService(serviceId))
        .orElseThrow(
            () -> new InvalidRequestException(format("Unable to find artifact for service id: %s", serviceId)));
  }

  public ArtifactStream getArtifactStream(String artifactStreamId) {
    return Optional.ofNullable(artifactStreamService.get(artifactStreamId))
        .orElseThrow(()
                         -> new InvalidRequestException(
                             format("Unable to find artifact stream for artifact stream id: %s", artifactStreamId)));
  }

  public ManagerExecutionLogCallback getExecutionLogCallback(Activity activity) {
    String commandUnitName = activity.getCommandUnits().get(0).getName();
    Log.Builder logBuilder =
        aLog().appId(activity.getAppId()).activityId(activity.getUuid()).commandUnitName(commandUnitName);
    return new ManagerExecutionLogCallback(logService, logBuilder, activity.getUuid());
  }

  public Service getServiceByAppId(ExecutionContext context, String appId) {
    String serviceId = getServiceId(context);
    return serviceResourceService.getWithDetails(appId, serviceId);
  }

  private String getServiceId(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    return phaseElement.getServiceElement().getUuid();
  }

  public Application getApplication(ExecutionContext context) {
    return Optional.of(getWorkflowStandardParams(context))
        .map(WorkflowStandardParams::getApp)
        .orElseThrow(
            ()
                -> new InvalidRequestException(
                    format("Application can't be null or empty, accountId: %s", context.getAccountId()), USER));
  }

  public Environment getEnvironment(ExecutionContext context) {
    return Optional.of(getWorkflowStandardParams(context))
        .map(WorkflowStandardParams::getEnv)
        .orElseThrow(()
                         -> new InvalidRequestException(
                             format("Env can't be null or empty, accountId: %s", context.getAccountId()), USER));
  }

  @NotNull
  public WorkflowStandardParams getWorkflowStandardParams(ExecutionContext context) {
    return Optional.ofNullable((WorkflowStandardParams) context.getContextElement(ContextElementType.STANDARD))
        .orElseThrow(
            ()
                -> new InvalidRequestException(
                    format("WorkflowStandardParams can't be null or empty, accountId: %s", context.getAccountId()),
                    USER));
  }

  public Activity createAndSaveActivity(ExecutionContext context, Artifact artifact, String commandName,
      String commandType, CommandUnitDetails.CommandUnitType commandUnitType, List<CommandUnit> commandUnits) {
    WorkflowStandardParams workflowStandardParams = getWorkflowStandardParams(context);
    Application app = getApplication(context);
    Environment env = getEnvironment(context);
    Service service = getServiceByAppId(context, app.getUuid());

    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .appId(app.getAppId())
                                          .environmentId(env.getUuid())
                                          .environmentName(env.getName())
                                          .environmentType(env.getEnvironmentType())
                                          .serviceId(service.getUuid())
                                          .serviceName(service.getName())
                                          .commandName(commandName)
                                          .commandType(commandType)
                                          .type(Activity.Type.Command)
                                          .workflowExecutionId(context.getWorkflowExecutionId())
                                          .workflowId(context.getWorkflowId())
                                          .workflowType(context.getWorkflowType())
                                          .workflowExecutionName(context.getWorkflowExecutionName())
                                          .stateExecutionInstanceId(context.getStateExecutionInstanceId())
                                          .stateExecutionInstanceName(context.getStateExecutionInstanceName())
                                          .commandUnitType(commandUnitType)
                                          .commandUnits(commandUnits)
                                          .status(ExecutionStatus.RUNNING)
                                          .triggeredBy(TriggeredBy.builder()
                                                           .email(workflowStandardParams.getCurrentUser().getEmail())
                                                           .name(workflowStandardParams.getCurrentUser().getName())
                                                           .build());

    if (artifact != null) {
      activityBuilder.artifactName(artifact.getDisplayName()).artifactId(artifact.getUuid());
      activityBuilder.artifactStreamName(artifact.getDisplayName()).artifactStreamId(artifact.getUuid());
    }

    return activityService.save(activityBuilder.build());
  }

  public Command getCommand(String appId, String serviceId, String envId, String commandName) {
    return serviceResourceService.getCommandByName(appId, serviceId, envId, commandName).getCommand();
  }

  public List<CommandUnit> getCommandUnitList(String appId, String serviceId, String envId, String commandName) {
    return serviceResourceService.getFlattenCommandUnitList(appId, serviceId, envId, commandName);
  }

  public void updateActivityStatus(String appId, String activityId, ExecutionStatus executionStatus) {
    activityService.updateStatus(activityId, appId, executionStatus);
  }

  public int renderTimeoutExpressionOrGetDefault(String timeout, ExecutionContext context, int defaultValue) {
    timeout = timeout.replaceAll(STEADY_STATE_TIMEOUT_REGEX, EMPTY);
    int value = renderExpressionOrGetDefault(timeout, context, defaultValue);
    return value <= 0 ? defaultValue : value;
  }

  public int renderExpressionOrGetDefault(String expr, ExecutionContext context, int defaultValue) {
    int retVal = defaultValue;
    if (isNotEmpty(expr)) {
      try {
        retVal = Integer.parseInt(context.renderExpression(expr));
      } catch (NumberFormatException e) {
        logger.error(format("Number format Exception while evaluating: [%s]", expr), e);
        retVal = defaultValue;
      }
    }
    return retVal;
  }

  public String getBase64EncodedUserData(ExecutionContext context, String appId, String serviceId) {
    return Optional.ofNullable(serviceResourceService.getUserDataSpecification(appId, serviceId))
        .map(UserDataSpecification::getData)
        .map(context::renderExpression)
        .map(EncodingUtils::encodeBase64)
        .orElse(null);
  }

  public String fixNamePrefix(ExecutionContext context, final String name, final String appName,
      final String serviceName, final String envName) {
    return isBlank(name) ? Misc.normalizeExpression(ServiceVersionConvention.getPrefix(appName, serviceName, envName))
                         : Misc.normalizeExpression(context.renderExpression(name));
  }

  public AzureVMSSInfrastructureMapping getAzureVMSSInfrastructureMapping(String infraMappingId, String appId) {
    notNullCheck("Infrastructure Mapping id is null or empty", infraMappingId);
    notNullCheck("Application id is null or empty", appId);
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (!(infrastructureMapping instanceof AzureVMSSInfrastructureMapping)) {
      throw new InvalidRequestException(
          format("Infrastructure Mapping is not instance of AzureVMSSInfrastructureMapping, infrastructureMapping: %s",
              infrastructureMapping));
    }
    return (AzureVMSSInfrastructureMapping) infrastructureMappingService.get(appId, infraMappingId);
  }

  public AzureConfig getAzureConfig(final String computeProviderSettingId) {
    SettingAttribute settingAttribute = settingsService.get(computeProviderSettingId);
    return (AzureConfig) settingAttribute.getValue();
  }

  public Integer getAzureVMSSStateTimeoutFromContext(ExecutionContext context) {
    AzureVMSSSetupContextElement azureVMSSSetupContextElement =
        context.getContextElement(ContextElementType.AZURE_VMSS_SETUP);
    return Optional.ofNullable(azureVMSSSetupContextElement)
        .map(AzureVMSSSetupContextElement::getAutoScalingSteadyStateVMSSTimeout)
        .filter(autoScalingSteadyStateVMSSTimeout -> autoScalingSteadyStateVMSSTimeout > 0)
        .map(autoScalingSteadyStateVMSSTimeout
            -> Ints.checkedCast(TimeUnit.MINUTES.toMillis(autoScalingSteadyStateVMSSTimeout.longValue())))
        .orElse(null);
  }

  public void setNewInstance(List<InstanceElement> newInstanceElements, boolean newInstance) {
    newInstanceElements.forEach(instanceElement -> instanceElement.setNewInstance(newInstance));
  }

  @NotNull
  public List<InstanceStatusSummary> getInstanceStatusSummaries(
      ExecutionStatus executionStatus, List<InstanceElement> newInstanceElements) {
    return newInstanceElements.stream()
        .map(instanceElement
            -> anInstanceStatusSummary().withInstanceElement(instanceElement).withStatus(executionStatus).build())
        .collect(toList());
  }

  public List<InstanceElement> generateInstanceElements(ExecutionContext context,
      AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping, List<AzureVMInstanceData> vmInstances) {
    return azureStateHelper.generateInstanceElements(context, azureVMSSInfrastructureMapping, vmInstances);
  }

  public void saveInstanceInfoToSweepingOutput(ExecutionContext context, List<InstanceElement> instanceElements) {
    if (isNotEmpty(instanceElements)) {
      // This sweeping element will be used by verification or other consumers.
      List<InstanceDetails> instanceDetails = azureStateHelper.generateAzureVMSSInstanceDetails(instanceElements);
      sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                     .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
                                     .value(InstanceInfoVariables.builder()
                                                .instanceElements(instanceElements)
                                                .instanceDetails(instanceDetails)
                                                .skipVerification(isEmpty(instanceDetails))
                                                .build())
                                     .build());
    }
  }

  public ExecutionStatus getExecutionStatus(AzureVMSSTaskExecutionResponse executionResponse) {
    return executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS ? ExecutionStatus.SUCCESS
                                                                                           : ExecutionStatus.FAILED;
  }

  public AzureVMSSStateData populateStateData(ExecutionContext context) {
    Application application = getApplication(context);
    Service service = getServiceByAppId(context, application.getUuid());
    String serviceId = getServiceId(context);
    Artifact artifact = getArtifact((DeploymentExecutionContext) context, service.getUuid());
    Environment environment = getEnvironment(context);
    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping =
        getAzureVMSSInfrastructureMapping(context.fetchInfraMappingId(), application.getUuid());
    AzureConfig azureConfig = getAzureConfig(azureVMSSInfrastructureMapping.getComputeProviderSettingId());
    List<EncryptedDataDetail> encryptedDataDetails =
        getEncryptedDataDetails(context, azureVMSSInfrastructureMapping.getComputeProviderSettingId());

    return AzureVMSSStateData.builder()
        .application(application)
        .service(service)
        .serviceId(serviceId)
        .artifact(artifact)
        .environment(environment)
        .infrastructureMapping(azureVMSSInfrastructureMapping)
        .azureConfig(azureConfig)
        .azureEncryptedDataDetails(encryptedDataDetails)
        .build();
  }

  public AzureConfigDTO createAzureConfigDTO(AzureConfig azureConfig) {
    return AzureConfigDTO.builder()
        .clientId(azureConfig.getClientId())
        .key(new SecretRefData(azureConfig.getEncryptedKey(), Scope.ACCOUNT, null))
        .tenantId(azureConfig.getTenantId())
        .build();
  }

  public AzureVMAuthDTO createVMAuthDTO(AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping) {
    VMSSAuthType vmssAuthType = azureVMSSInfrastructureMapping.getVmssAuthType();
    if (VMSSAuthType.PASSWORD != vmssAuthType && VMSSAuthType.SSH_PUBLIC_KEY != vmssAuthType) {
      throw new InvalidRequestException(
          format("Unsupported Azure VMSS Auth type, %s", azureVMSSInfrastructureMapping.getVmssAuthType()));
    }

    String passwordSecretTextName = azureVMSSInfrastructureMapping.getPasswordSecretTextName();
    String hostConnectionAttrs = azureVMSSInfrastructureMapping.getHostConnectionAttrs();
    String userName = azureVMSSInfrastructureMapping.getUserName();
    String secretRefIdentifier = vmssAuthType == VMSSAuthType.PASSWORD ? passwordSecretTextName : hostConnectionAttrs;

    return AzureVMAuthDTO.builder()
        .userName(userName)
        .azureVmAuthType(AzureVMAuthType.valueOf(vmssAuthType.name()))
        .secretRef(new SecretRefData(secretRefIdentifier, Scope.ACCOUNT, null))
        .build();
  }

  public List<EncryptedDataDetail> getVMAuthDTOEncryptionDetails(
      ExecutionContext context, AzureVMAuthDTO azureVmAuthDTO, String envId) {
    AzureVMAuthType azureVmAuthType = azureVmAuthDTO.getAzureVmAuthType();
    String secretIdentifier = azureVmAuthDTO.getSecretRef().getIdentifier();

    return SSH_PUBLIC_KEY == azureVmAuthType ? getEncryptedDataDetails(context, secretIdentifier)
                                             : getServiceVariableEncryptedDataDetails(context, secretIdentifier, envId);
  }

  public List<EncryptedDataDetail> getEncryptedDataDetails(ExecutionContext context, final String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    return secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());
  }

  private List<EncryptedDataDetail> getServiceVariableEncryptedDataDetails(
      ExecutionContext context, final String passwordSecretTextName, final String envId) {
    String appId = context.getAppId();
    ServiceVariable serviceVariable =
        buildEncryptedServiceVariable(context.getAccountId(), appId, envId, passwordSecretTextName);
    return secretManager.getEncryptionDetails(serviceVariable, appId, context.getWorkflowExecutionId());
  }

  private ServiceVariable buildEncryptedServiceVariable(
      final String accountId, final String appId, final String envId, final String secretTextName) {
    EncryptedData encryptedData = secretManager.getSecretMappedToAppByName(accountId, appId, envId, secretTextName);
    if (encryptedData == null) {
      throw new InvalidRequestException(format("No secret found with name: %s", secretTextName), USER);
    }
    return ServiceVariable.builder()
        .accountId(accountId)
        .type(ENCRYPTED_TEXT)
        .encryptedValue(encryptedData.getUuid())
        .secretTextName(secretTextName)
        .build();
  }

  public void updateEncryptedDataDetailSecretFieldName(
      AzureVMAuthDTO azureVmAuthDTO, List<EncryptedDataDetail> vmAuthDTOEncryptionDetails) {
    String secretRefFieldName = azureVmAuthDTO.getSecretRefFieldName();
    for (EncryptedDataDetail encryptedDataDetail : vmAuthDTOEncryptionDetails) {
      encryptedDataDetail.setFieldName(secretRefFieldName);
    }
  }

  public String getVMSSIdFromName(
      String subscriptionId, String resourceGroupName, String newVirtualMachineScaleSetName) {
    return isNotBlank(newVirtualMachineScaleSetName)
        ? format(VIRTUAL_MACHINE_SCALE_SET_ID_PATTERN, subscriptionId, resourceGroupName, newVirtualMachineScaleSetName)
        : EMPTY;
  }
}
