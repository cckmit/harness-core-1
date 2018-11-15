package software.wings.service.impl.instance;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static software.wings.beans.container.Label.Builder.aLabel;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import software.wings.api.CommandStepExecutionSummary;
import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.ContainerDeploymentInfoWithNames;
import software.wings.api.ContainerServiceData;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.HelmSetupExecutionSummary;
import software.wings.api.KubernetesSteadyStateCheckExecutionSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.container.Label;
import software.wings.beans.infrastructure.instance.ContainerDeploymentInfo;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.ContainerDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.common.Constants;
import software.wings.exception.HarnessException;
import software.wings.service.impl.ContainerMetadata;
import software.wings.service.impl.instance.sync.ContainerSync;
import software.wings.service.impl.instance.sync.request.ContainerFilter;
import software.wings.service.impl.instance.sync.request.ContainerSyncRequest;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.sm.ExecutionContext;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.StepExecutionSummary;
import software.wings.utils.Validator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author rktummala on 02/03/18
 */
@Singleton
public class ContainerInstanceHandler extends InstanceHandler {
  @Inject private ContainerSync containerSync;

  @Override
  public void syncInstances(String appId, String infraMappingId) throws HarnessException {
    // Key - containerSvcName, Value - Instances
    syncInstancesInternal(appId, infraMappingId, ArrayListMultimap.create(), null, false);
  }

  private ContainerInfrastructureMapping getContainerInfraMapping(String appId, String inframappingId)
      throws HarnessException {
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, inframappingId);
    Validator.notNullCheck("Infra mapping is null for id:" + inframappingId, infrastructureMapping);

    if (!(infrastructureMapping instanceof ContainerInfrastructureMapping)) {
      String msg = "Incompatible infra mapping type. Expecting container type. Found:"
          + infrastructureMapping.getInfraMappingType();
      logger.error(msg);
      throw new HarnessException(msg);
    }

    return (ContainerInfrastructureMapping) infrastructureMapping;
  }

  private void syncInstancesInternal(String appId, String infraMappingId,
      Multimap<ContainerMetadata, Instance> containerMetadataInstanceMap,
      List<DeploymentSummary> newDeploymentSummaries, boolean rollback) throws HarnessException {
    ContainerInfrastructureMapping containerInfraMapping = getContainerInfraMapping(appId, infraMappingId);

    Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap =
        getDeploymentSummaryMap(newDeploymentSummaries, containerMetadataInstanceMap, containerInfraMapping);

    loadContainerSvcNameInstanceMap(appId, infraMappingId, containerMetadataInstanceMap);

    logger.info("Found {} containerSvcNames for app {} and infraMapping {} ",
        containerMetadataInstanceMap != null ? containerMetadataInstanceMap.size() : 0, appId, infraMappingId);

    if (containerMetadataInstanceMap == null) {
      return;
    }

    // This is to handle the case of the instances stored in the new schema.
    if (containerMetadataInstanceMap.size() > 0) {
      for (ContainerMetadata containerMetadata : containerMetadataInstanceMap.keySet()) {
        // Get all the instances for the given containerSvcName (In kubernetes, this is replication Controller and in
        // ECS it is taskDefinition)
        ContainerSyncResponse instanceSyncResponse =
            containerSync.getInstances(containerInfraMapping, singletonList(containerMetadata));
        Validator.notNullCheck("InstanceSyncResponse is null for containerSvcName: "
                + containerMetadata.getContainerServiceName() + " for infraMappingId: " + infraMappingId,
            instanceSyncResponse);

        List<ContainerInfo> latestContainerInfoList =
            Optional.ofNullable(instanceSyncResponse.getContainerInfoList()).orElse(emptyList());
        logger.info("Found {} instances from remote server for app {} , infraMapping {} and containerSvcName {}",
            latestContainerInfoList.size(), appId, infraMappingId, containerMetadata.getContainerServiceName());

        // Key - containerId(taskId in ECS / podId in Kubernetes), Value - ContainerInfo
        Map<String, ContainerInfo> latestContainerInfoMap = latestContainerInfoList.stream().collect(toMap(containerInfo
            -> containerInfo instanceof KubernetesContainerInfo ? ((KubernetesContainerInfo) containerInfo).getPodName()
                                                                : ((EcsContainerInfo) containerInfo).getTaskArn(),
            containerInfo -> containerInfo));

        Collection<Instance> instancesInDB = Optional.ofNullable(containerMetadataInstanceMap.get(containerMetadata))
                                                 .orElse(emptyList())
                                                 .stream()
                                                 .filter(Objects::nonNull)
                                                 .collect(toList());
        logger.info("Found {} instances in DB for app {} , infraMapping {} and containerSvcName {}",
            instancesInDB.size(), appId, infraMappingId, containerMetadata.getContainerServiceName());

        // Key - containerId (taskId in ECS / podId in Kubernetes), Value - Instance
        Map<String, Instance> instancesInDBMap = Maps.newHashMap();

        // If there are prior instances in db already
        for (Instance instance : instancesInDB) {
          ContainerInstanceKey containerInstanceKey = instance.getContainerInstanceKey();
          instancesInDBMap.put(containerInstanceKey.getContainerId(), instance);
        }

        // Find the instances that were yet to be added to db
        SetView<String> instancesToBeAdded =
            Sets.difference(latestContainerInfoMap.keySet(), instancesInDBMap.keySet());

        SetView<String> instancesToBeDeleted =
            Sets.difference(instancesInDBMap.keySet(), latestContainerInfoMap.keySet());

        Set<String> instanceIdsToBeDeleted = new HashSet<>();
        for (String containerId : instancesToBeDeleted) {
          Instance instance = instancesInDBMap.get(containerId);
          if (instance != null) {
            instanceIdsToBeDeleted.add(instance.getUuid());
          }
        }

        logger.info(
            "Total number of Container instances found in DB for ContainerSvcName: {}, Namespace {}, InfraMappingId: {} and AppId: {}, "
                + "No of instances in DB: {}, No of Running instances: {}, "
                + "No of instances to be Added: {}, No of instances to be deleted: {}",
            containerMetadata.getContainerServiceName(), containerMetadata.getNamespace(), infraMappingId, appId,
            instancesInDB.size(), latestContainerInfoMap.keySet().size(), instancesToBeAdded.size(),
            instanceIdsToBeDeleted.size());
        if (isNotEmpty(instanceIdsToBeDeleted)) {
          instanceService.delete(instanceIdsToBeDeleted);
        }

        DeploymentSummary deploymentSummary;
        if (isNotEmpty(instancesToBeAdded)) {
          // newDeploymentInfo would be null in case of sync job.
          if (!deploymentSummaryMap.containsKey(containerMetadata) && isNotEmpty(instancesInDB)) {
            Optional<Instance> instanceWithExecutionInfoOptional = getInstanceWithExecutionInfo(instancesInDB);
            if (!instanceWithExecutionInfoOptional.isPresent()) {
              logger.warn("Couldn't find an instance from a previous deployment for inframapping {}", infraMappingId);
              continue;
            }

            DeploymentSummary deploymentSummaryFromPrevious =
                DeploymentSummary.builder().deploymentInfo(ContainerDeploymentInfoWithNames.builder().build()).build();
            // We pick one of the existing instances from db for the same controller / task definition
            generateDeploymentSummaryFromInstance(
                instanceWithExecutionInfoOptional.get(), deploymentSummaryFromPrevious);
            deploymentSummary = deploymentSummaryFromPrevious;
          } else {
            deploymentSummary =
                getDeploymentSummaryForInstanceCreation(deploymentSummaryMap.get(containerMetadata), rollback);
          }

          for (String containerId : instancesToBeAdded) {
            ContainerInfo containerInfo = latestContainerInfoMap.get(containerId);
            Instance instance = buildInstanceFromContainerInfo(containerInfraMapping, containerInfo, deploymentSummary);
            instanceService.save(instance);
          }
        }
      }
    }
  }

  private Map<ContainerMetadata, DeploymentSummary> getDeploymentSummaryMap(
      List<DeploymentSummary> newDeploymentSummaries, Multimap<ContainerMetadata, Instance> containerInstances,
      ContainerInfrastructureMapping containerInfraMapping) {
    if (EmptyPredicate.isEmpty(newDeploymentSummaries)) {
      return emptyMap();
    }

    Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap = new HashMap<>();

    if (newDeploymentSummaries.stream().iterator().next().getDeploymentInfo()
            instanceof ContainerDeploymentInfoWithLabels) {
      Map<String, String> labelMap = new HashMap<>();
      for (DeploymentSummary deploymentSummary : newDeploymentSummaries) {
        ((ContainerDeploymentInfoWithLabels) deploymentSummary.getDeploymentInfo())
            .getLabels()
            .forEach(labelEntry -> labelMap.put(labelEntry.getName(), labelEntry.getValue()));

        String namespace = containerInfraMapping.getNamespace();
        if (ExpressionEvaluator.containsVariablePattern(namespace)) {
          namespace = ((ContainerDeploymentInfoWithLabels) deploymentSummary.getDeploymentInfo()).getNamespace();
        }
        Set<String> controllerNames = containerSync.getControllerNames(containerInfraMapping, labelMap, namespace);

        logger.info(
            "Number of controllers returned for executionId [{}], inframappingId [{}], appId [{}] from labels: {}",
            newDeploymentSummaries.iterator().next().getWorkflowExecutionId(), containerInfraMapping.getUuid(),
            newDeploymentSummaries.iterator().next().getAppId(), controllerNames.size());

        for (String controllerName : controllerNames) {
          ContainerMetadata containerMetadata =
              ContainerMetadata.builder().containerServiceName(controllerName).namespace(namespace).build();
          deploymentSummaryMap.put(containerMetadata, deploymentSummary);
          containerInstances.put(containerMetadata, null);
        }
      }
    } else {
      newDeploymentSummaries.forEach(deploymentSummary -> {
        ContainerDeploymentInfoWithNames deploymentInfo =
            (ContainerDeploymentInfoWithNames) deploymentSummary.getDeploymentInfo();
        deploymentSummaryMap.put(ContainerMetadata.builder()
                                     .containerServiceName(deploymentInfo.getContainerSvcName())
                                     .namespace(deploymentInfo.getNamespace())
                                     .build(),
            deploymentSummary);
      });
    }

    return deploymentSummaryMap;
  }
  private void loadContainerSvcNameInstanceMap(
      String appId, String infraMappingId, Multimap<ContainerMetadata, Instance> instanceMap) throws HarnessException {
    List<Instance> instanceListInDBForInfraMapping = getInstances(appId, infraMappingId);
    logger.info("Found {} instances for app {} and infraMapping {} ", instanceListInDBForInfraMapping.size(), appId,
        infraMappingId);
    for (Instance instance : instanceListInDBForInfraMapping) {
      InstanceInfo instanceInfo = instance.getInstanceInfo();
      if (instanceInfo instanceof ContainerInfo) {
        ContainerInfo containerInfo = (ContainerInfo) instanceInfo;
        String containerSvcName = getContainerSvcName(containerInfo);
        String namespace = containerInfo instanceof KubernetesContainerInfo
            ? ((KubernetesContainerInfo) containerInfo).getNamespace()
            : null;
        instanceMap.put(
            ContainerMetadata.builder().containerServiceName(containerSvcName).namespace(namespace).build(), instance);
      } else {
        throw new HarnessException("UnSupported instance deploymentInfo type" + instance.getInstanceType().name());
      }
    }
  }

  @Override
  public void handleNewDeployment(List<DeploymentSummary> deploymentSummaries, boolean rollback)
      throws HarnessException {
    Multimap<ContainerMetadata, Instance> containerSvcNameInstanceMap = ArrayListMultimap.create();

    if (isEmpty(deploymentSummaries)) {
      return;
    }

    String infraMappingId = deploymentSummaries.iterator().next().getInfraMappingId();
    String appId = deploymentSummaries.iterator().next().getAppId();
    String workflowExecutionId = deploymentSummaries.iterator().next().getWorkflowExecutionId();
    logger.info("Handling new container deployment for executionId [{}], inframappingId [{}], appId [{}]",
        workflowExecutionId, infraMappingId, appId);
    validateDeploymentInfos(deploymentSummaries);

    if (deploymentSummaries.iterator().next().getDeploymentInfo() instanceof ContainerDeploymentInfoWithNames) {
      deploymentSummaries.forEach(deploymentSummary -> {
        ContainerDeploymentInfoWithNames deploymentInfo =
            (ContainerDeploymentInfoWithNames) deploymentSummary.getDeploymentInfo();
        containerSvcNameInstanceMap.put(ContainerMetadata.builder()
                                            .containerServiceName(deploymentInfo.getContainerSvcName())
                                            .namespace(deploymentInfo.getNamespace())
                                            .build(),
            null);
      });
    }

    syncInstancesInternal(appId, infraMappingId, containerSvcNameInstanceMap, deploymentSummaries, rollback);
  }

  private void validateDeploymentInfos(List<DeploymentSummary> deploymentSummaries) throws HarnessException {
    for (DeploymentSummary deploymentSummary : deploymentSummaries) {
      DeploymentInfo deploymentInfo = deploymentSummary.getDeploymentInfo();
      if (!(deploymentInfo instanceof ContainerDeploymentInfoWithNames)
          && !(deploymentInfo instanceof ContainerDeploymentInfoWithLabels)) {
        throw new HarnessException("Incompatible deployment info type: " + deploymentInfo);
      }
    }
  }

  public boolean isContainerDeployment(InfrastructureMapping infrastructureMapping) {
    return infrastructureMapping instanceof ContainerInfrastructureMapping;
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();

    if (phaseStepExecutionSummary == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("PhaseStepExecutionSummary is null for stateExecutionInstanceId: " + stateExecutionInstanceId);
      }
      return Optional.empty();
    }
    List<StepExecutionSummary> stepExecutionSummaryList = phaseStepExecutionSummary.getStepExecutionSummaryList();
    // This was observed when the "deploy containers" step was executed in rollback and no commands were
    // executed since setup failed.
    if (stepExecutionSummaryList == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("StepExecutionSummaryList is null for stateExecutionInstanceId: " + stateExecutionInstanceId);
      }
      return Optional.empty();
    }

    for (StepExecutionSummary stepExecutionSummary : stepExecutionSummaryList) {
      if (stepExecutionSummary != null) {
        if (stepExecutionSummary instanceof CommandStepExecutionSummary) {
          CommandStepExecutionSummary commandStepExecutionSummary = (CommandStepExecutionSummary) stepExecutionSummary;
          String clusterName = commandStepExecutionSummary.getClusterName();
          Set<String> containerSvcNameSet = Sets.newHashSet();

          if (checkIfContainerServiceDataAvailable(
                  stateExecutionInstanceId, commandStepExecutionSummary, containerSvcNameSet)) {
            return Optional.empty();
          }

          List<DeploymentInfo> containerDeploymentInfoWithNames = getContainerDeploymentInfos(
              clusterName, commandStepExecutionSummary.getNamespace(), commandStepExecutionSummary);

          return Optional.of(containerDeploymentInfoWithNames);

        } else if (stepExecutionSummary instanceof HelmSetupExecutionSummary
            || stepExecutionSummary instanceof KubernetesSteadyStateCheckExecutionSummary) {
          if (!(infrastructureMapping instanceof ContainerInfrastructureMapping)) {
            logger.warn("Inframapping is not container type. cannot proceed for state execution instance: {}",
                stateExecutionInstanceId);
            return Optional.empty();
          }

          String clusterName = ((ContainerInfrastructureMapping) infrastructureMapping).getClusterName();

          List<Label> labels = new ArrayList<>();

          DeploymentInfo deploymentInfo;
          if (stepExecutionSummary instanceof HelmSetupExecutionSummary) {
            HelmSetupExecutionSummary helmSetupExecutionSummary = (HelmSetupExecutionSummary) stepExecutionSummary;
            labels.add(aLabel().withName("release").withValue(helmSetupExecutionSummary.getReleaseName()).build());
            deploymentInfo = getContainerDeploymentInfosWithLabelsForHelm(
                clusterName, helmSetupExecutionSummary.getNamespace(), labels, helmSetupExecutionSummary);
          } else {
            KubernetesSteadyStateCheckExecutionSummary kubernetesSteadyStateCheckExecutionSummary =
                (KubernetesSteadyStateCheckExecutionSummary) stepExecutionSummary;
            labels.addAll(kubernetesSteadyStateCheckExecutionSummary.getLabels());
            deploymentInfo = getContainerDeploymentInfosWithLabels(
                clusterName, kubernetesSteadyStateCheckExecutionSummary.getNamespace(), labels);
          }

          return Optional.of(singletonList(deploymentInfo));
        }
      }
    }
    return Optional.empty();
  }

  private boolean checkIfContainerServiceDataAvailable(String stateExecutionInstanceId,
      CommandStepExecutionSummary commandStepExecutionSummary, Set<String> containerSvcNameSet) {
    if (commandStepExecutionSummary.getOldInstanceData() != null) {
      containerSvcNameSet.addAll(commandStepExecutionSummary.getOldInstanceData()
                                     .stream()
                                     .map(ContainerServiceData::getName)
                                     .collect(toList()));
    }

    if (commandStepExecutionSummary.getNewInstanceData() != null) {
      List<String> newcontainerSvcNames = commandStepExecutionSummary.getNewInstanceData()
                                              .stream()
                                              .map(ContainerServiceData::getName)
                                              .collect(toList());
      containerSvcNameSet.addAll(newcontainerSvcNames);
    }

    // Filter out null values
    List<String> serviceNames = containerSvcNameSet.stream().filter(EmptyPredicate::isNotEmpty).collect(toList());

    if (isEmpty(serviceNames)) {
      logger.warn(
          "Both old and new container services are empty. Cannot proceed for phase step for state execution instance: {}",
          stateExecutionInstanceId);
      return true;
    }
    return false;
  }

  private DeploymentInfo getContainerDeploymentInfosWithLabels(
      String clusterName, String namespace, List<Label> labels) {
    return ContainerDeploymentInfoWithLabels.builder()
        .clusterName(clusterName)
        .namespace(namespace)
        .labels(labels)
        .build();
  }

  private DeploymentInfo getContainerDeploymentInfosWithLabelsForHelm(
      String clusterName, String namespace, List<Label> labels, HelmSetupExecutionSummary executionSummary) {
    Integer version = executionSummary.getRollbackVersion() == null ? executionSummary.getNewVersion()
                                                                    : executionSummary.getRollbackVersion();
    return ContainerDeploymentInfoWithLabels.builder()
        .clusterName(clusterName)
        .namespace(namespace)
        .labels(labels)
        .newVersion(version.toString())
        .build();
  }

  private List<DeploymentInfo> getContainerDeploymentInfos(
      String clusterName, String namespace, CommandStepExecutionSummary commandStepExecutionSummary) {
    List<DeploymentInfo> containerDeploymentInfoWithNames = new ArrayList<>();
    addToDeploymentInfoWithNames(
        clusterName, namespace, commandStepExecutionSummary.getNewInstanceData(), containerDeploymentInfoWithNames);
    addToDeploymentInfoWithNames(
        clusterName, namespace, commandStepExecutionSummary.getOldInstanceData(), containerDeploymentInfoWithNames);

    return containerDeploymentInfoWithNames;
  }

  private void addToDeploymentInfoWithNames(String clusterName, String namespace,
      List<ContainerServiceData> containerServiceDataList, List<DeploymentInfo> containerDeploymentInfoWithNames) {
    if (isNotEmpty(containerServiceDataList)) {
      containerServiceDataList.forEach(containerServiceData
          -> containerDeploymentInfoWithNames.add(ContainerDeploymentInfoWithNames.builder()
                                                      .containerSvcName(containerServiceData.getName())
                                                      .uniqueNameIdentifier(containerServiceData.getUniqueIdentifier())
                                                      .clusterName(clusterName)
                                                      .namespace(namespace)
                                                      .build()));
    }
  }

  private Instance buildInstanceFromContainerInfo(
      InfrastructureMapping infraMapping, ContainerInfo containerInfo, DeploymentSummary deploymentSummary) {
    InstanceBuilder builder = buildInstanceBase(null, infraMapping, deploymentSummary);
    builder.containerInstanceKey(generateInstanceKeyForContainer(containerInfo));
    builder.instanceInfo(containerInfo);

    return builder.build();
  }

  private ContainerInstanceKey generateInstanceKeyForContainer(ContainerInfo containerInfo) {
    ContainerInstanceKey containerInstanceKey;

    if (containerInfo instanceof KubernetesContainerInfo) {
      KubernetesContainerInfo kubernetesContainerInfo = (KubernetesContainerInfo) containerInfo;
      containerInstanceKey = ContainerInstanceKey.builder().containerId(kubernetesContainerInfo.getPodName()).build();
    } else if (containerInfo instanceof EcsContainerInfo) {
      EcsContainerInfo ecsContainerInfo = (EcsContainerInfo) containerInfo;
      containerInstanceKey = ContainerInstanceKey.builder().containerId(ecsContainerInfo.getTaskArn()).build();
    } else {
      String msg = "Unsupported container instance type:" + containerInfo;
      logger.error(msg);
      throw new WingsException(msg);
    }

    return containerInstanceKey;
  }

  private String getContainerSvcName(ContainerInfo containerInfo) {
    if (containerInfo instanceof KubernetesContainerInfo) {
      return ((KubernetesContainerInfo) containerInfo).getControllerName();
    } else if (containerInfo instanceof EcsContainerInfo) {
      return ((EcsContainerInfo) containerInfo).getServiceName();
    } else {
      throw new WingsException(
          "Unsupported container deploymentInfo type:" + containerInfo.getClass().getCanonicalName());
    }
  }

  private ContainerSyncResponse getLatestInstancesFromContainerServer(
      Collection<software.wings.beans.infrastructure.instance.ContainerDeploymentInfo>
          containerDeploymentInfoCollection,
      InstanceType instanceType) {
    ContainerFilter containerFilter =
        ContainerFilter.builder().containerDeploymentInfoCollection(containerDeploymentInfoCollection).build();

    ContainerSyncRequest instanceSyncRequest = ContainerSyncRequest.builder().filter(containerFilter).build();
    if (instanceType == InstanceType.KUBERNETES_CONTAINER_INSTANCE
        || instanceType == InstanceType.ECS_CONTAINER_INSTANCE) {
      return containerSync.getInstances(instanceSyncRequest);
    } else {
      String msg = "Unsupported container instance type:" + instanceType;
      logger.error(msg);
      throw new WingsException(msg);
    }
  }

  public Set<ContainerMetadata> getContainerServiceNames(
      ExecutionContext context, String serviceId, String infraMappingId) {
    Set<ContainerMetadata> containerMetadataSet = Sets.newHashSet();
    List<StateExecutionInstance> executionDataList = workflowExecutionService.getStateExecutionData(context.getAppId(),
        context.getWorkflowExecutionId(), serviceId, infraMappingId, StateType.PHASE_STEP, Constants.DEPLOY_CONTAINERS);
    executionDataList.forEach(stateExecutionData -> {
      List<StateExecutionData> deployPhaseStepList =
          stateExecutionData.getStateExecutionMap()
              .entrySet()
              .stream()
              .filter(entry -> entry.getKey().equals(Constants.DEPLOY_CONTAINERS))
              .map(Entry::getValue)
              .collect(toList());
      deployPhaseStepList.forEach(phaseStep -> {
        PhaseStepExecutionSummary phaseStepExecutionSummary =
            ((PhaseStepExecutionData) phaseStep).getPhaseStepExecutionSummary();
        Preconditions.checkNotNull(
            phaseStepExecutionSummary, "PhaseStepExecutionSummary is null for stateExecutionInstanceId: " + phaseStep);
        List<StepExecutionSummary> stepExecutionSummaryList = phaseStepExecutionSummary.getStepExecutionSummaryList();
        Preconditions.checkNotNull(
            stepExecutionSummaryList, "stepExecutionSummaryList null for " + phaseStepExecutionSummary);

        for (StepExecutionSummary stepExecutionSummary : stepExecutionSummaryList) {
          if (stepExecutionSummary instanceof CommandStepExecutionSummary) {
            CommandStepExecutionSummary commandStepExecutionSummary =
                (CommandStepExecutionSummary) stepExecutionSummary;
            if (commandStepExecutionSummary.getOldInstanceData() != null) {
              containerMetadataSet.addAll(commandStepExecutionSummary.getOldInstanceData()
                                              .stream()
                                              .map(containerServiceData
                                                  -> ContainerMetadata.builder()
                                                         .containerServiceName(containerServiceData.getName())
                                                         .namespace(commandStepExecutionSummary.getNamespace())
                                                         .build())
                                              .collect(toList()));
            }

            if (commandStepExecutionSummary.getNewInstanceData() != null) {
              containerMetadataSet.addAll(commandStepExecutionSummary.getNewInstanceData()
                                              .stream()
                                              .map(containerServiceData
                                                  -> ContainerMetadata.builder()
                                                         .containerServiceName(containerServiceData.getName())
                                                         .namespace(commandStepExecutionSummary.getNamespace())
                                                         .build())
                                              .collect(toList()));
            }

            Preconditions.checkState(!containerMetadataSet.isEmpty(),
                "Both old and new container services are empty. Cannot proceed for phase step "
                    + commandStepExecutionSummary.getServiceId());
          }
        }
      });
    });

    return containerMetadataSet;
  }

  public List<ContainerInfo> getContainerInfoForService(Set<ContainerMetadata> containerMetadataSet,
      ExecutionContext context, String infrastructureMappingId, String serviceId) {
    Preconditions.checkState(!containerMetadataSet.isEmpty(), "empty for " + context.getWorkflowExecutionId());
    InfrastructureMapping infrastructureMapping = infraMappingService.get(context.getAppId(), infrastructureMappingId);
    InstanceType instanceType = instanceUtil.getInstanceType(infrastructureMapping.getInfraMappingType());
    Preconditions.checkNotNull(instanceType, "Null for " + infrastructureMappingId);

    String containerSvcNameNoRevision =
        getcontainerSvcNameNoRevision(instanceType, containerMetadataSet.iterator().next().getContainerServiceName());
    Map<String, ContainerDeploymentInfo> containerSvcNameDeploymentInfoMap =
        instanceService.getContainerDeploymentInfoList(containerSvcNameNoRevision, context.getAppId())
            .stream()
            .collect(toMap(ContainerDeploymentInfo::getContainerSvcName,
                currentContainerDeploymentInDB -> currentContainerDeploymentInDB));

    for (ContainerMetadata containerMetadata : containerMetadataSet) {
      ContainerDeploymentInfo containerDeploymentInfo =
          containerSvcNameDeploymentInfoMap.get(containerMetadata.getContainerServiceName());
      if (containerDeploymentInfo == null) {
        containerDeploymentInfo = ContainerDeploymentInfo.builder()
                                      .appId(context.getAppId())
                                      .containerSvcName(containerMetadata.getContainerServiceName())
                                      .infraMappingId(infrastructureMappingId)
                                      .workflowId(context.getWorkflowId())
                                      .workflowExecutionId(context.getWorkflowExecutionId())
                                      .serviceId(serviceId)
                                      .namespace(containerMetadata.getNamespace())
                                      .build();

        containerSvcNameDeploymentInfoMap.put(containerMetadata.getContainerServiceName(), containerDeploymentInfo);
      }
    }
    ContainerSyncResponse instanceSyncResponse =
        getLatestInstancesFromContainerServer(containerSvcNameDeploymentInfoMap.values(), instanceType);
    Preconditions.checkNotNull(instanceSyncResponse, "InstanceSyncResponse");

    return instanceSyncResponse.getContainerInfoList();
  }

  private String getcontainerSvcNameNoRevision(InstanceType instanceType, String containerSvcName) {
    String delimiter;
    if (instanceType == InstanceType.ECS_CONTAINER_INSTANCE) {
      delimiter = "__";
    } else if (instanceType == InstanceType.KUBERNETES_CONTAINER_INSTANCE) {
      delimiter = ".";
    } else {
      String msg = "Unsupported container instance type:" + instanceType;
      logger.error(msg);
      throw new WingsException(msg);
    }

    if (containerSvcName == null) {
      return null;
    }

    int index = containerSvcName.lastIndexOf(delimiter);
    if (index == -1) {
      return containerSvcName;
    }
    return containerSvcName.substring(0, index);
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    if (deploymentInfo instanceof ContainerDeploymentInfoWithNames) {
      ContainerDeploymentInfoWithNames deploymentInfoWithNames = (ContainerDeploymentInfoWithNames) deploymentInfo;
      String keyName = isNotEmpty(deploymentInfoWithNames.getUniqueNameIdentifier())
          ? deploymentInfoWithNames.getUniqueNameIdentifier()
          : deploymentInfoWithNames.getContainerSvcName();

      return ContainerDeploymentKey.builder().containerServiceName(keyName).build();
    } else if (deploymentInfo instanceof ContainerDeploymentInfoWithLabels) {
      ContainerDeploymentInfoWithLabels info = (ContainerDeploymentInfoWithLabels) deploymentInfo;
      ContainerDeploymentKey key = ContainerDeploymentKey.builder().labels(info.getLabels()).build();

      // For Helm
      if (EmptyPredicate.isNotEmpty(info.getNewVersion())) {
        key.setNewVersion(info.getNewVersion());
      }
      return key;
    } else {
      throw new WingsException("Unsupported DeploymentINfo type for Container: " + deploymentInfo);
    }
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    if (deploymentKey instanceof ContainerDeploymentKey) {
      deploymentSummary.setContainerDeploymentKey((ContainerDeploymentKey) deploymentKey);
    } else {
      throw new WingsException("Invalid deploymentKey passed for ContainerDeploymentKey" + deploymentKey);
    }
  }
}
