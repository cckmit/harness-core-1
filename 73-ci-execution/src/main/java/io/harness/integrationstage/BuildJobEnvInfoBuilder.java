package io.harness.integrationstage;

import static io.harness.common.CIExecutionConstants.DEFAULT_STEP_LIMIT_MEMORY_MIB;
import static io.harness.common.CIExecutionConstants.DEFAULT_STEP_LIMIT_MILLI_CPU;
import static io.harness.common.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.common.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.common.CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS;
import static io.harness.common.CIExecutionConstants.PVC_DEFAULT_STORAGE_SIZE;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static java.util.stream.Collectors.toMap;
import static software.wings.common.CICommonPodConstants.POD_NAME;
import static software.wings.common.CICommonPodConstants.STEP_EXEC;

import com.google.inject.Singleton;

import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.pod.PodSetupInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStage;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.PublishStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.publish.artifact.Artifact;
import io.harness.beans.yaml.extended.CustomVariables;
import io.harness.beans.yaml.extended.container.ContainerResource;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.ImageDetails;
import io.harness.stateutils.buildstate.providers.StepContainerUtils;
import io.harness.util.PortFinder;
import io.harness.yaml.core.ParallelStepElement;
import io.harness.yaml.core.StepElement;
import io.harness.yaml.core.auxiliary.intfc.ExecutionWrapper;
import software.wings.beans.ci.pod.CIContainerType;
import software.wings.beans.ci.pod.ContainerResourceParams;
import software.wings.beans.ci.pod.EncryptedVariableWithType;
import software.wings.beans.ci.pod.PVCParams;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class BuildJobEnvInfoBuilder {
  private static final String IMAGE_PATH_SPLIT_REGEX = ":";
  private static final SecureRandom random = new SecureRandom();

  public BuildJobEnvInfo getCIBuildJobEnvInfo(IntegrationStage integrationStage, CIExecutionArgs ciExecutionArgs,
      List<ExecutionWrapper> steps, boolean isFirstPod, String buildNumber) {
    // TODO Only kubernetes is supported currently
    if (integrationStage.getInfrastructure().getType().equals("kubernetes-direct")) {
      return K8BuildJobEnvInfo.builder()
          .podsSetupInfo(getCIPodsSetupInfo(integrationStage, ciExecutionArgs, steps, isFirstPod, buildNumber))
          .workDir(integrationStage.getWorkingDirectory())
          .publishStepConnectorIdentifier(getPublishStepConnectorIdentifier(integrationStage))
          .build();
    } else {
      throw new IllegalArgumentException("Input infrastructure type is not of type kubernetes");
    }
  }

  private K8BuildJobEnvInfo.PodsSetupInfo getCIPodsSetupInfo(IntegrationStage integrationStage,
      CIExecutionArgs ciExecutionArgs, List<ExecutionWrapper> steps, boolean isFirstPod, String buildNumber) {
    List<PodSetupInfo> pods = new ArrayList<>();
    String podName = generatePodName(integrationStage);
    List<ContainerDefinitionInfo> containerDefinitionInfos =
        createStepsContainerDefinition(steps, integrationStage, ciExecutionArgs);

    Integer stageMemoryRequest = getStageMemoryRequest(steps);
    Integer stageCpuRequest = getStageCpuRequest(steps);
    pods.add(PodSetupInfo.builder()
                 .podSetupParams(
                     PodSetupInfo.PodSetupParams.builder().containerDefinitionInfos(containerDefinitionInfos).build())
                 .name(podName)
                 .pvcParams(createPVCParams(isFirstPod, buildNumber))
                 .stageCpuRequest(stageCpuRequest)
                 .stageMemoryRequest(stageMemoryRequest)
                 .build());
    return K8BuildJobEnvInfo.PodsSetupInfo.builder().podSetupInfoList(pods).build();
  }

  private PVCParams createPVCParams(boolean isFirstPod, String buildNumber) {
    return PVCParams.builder()
        .claimName(buildNumber)
        .volumeName(STEP_EXEC)
        .isPresent(!isFirstPod)
        .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
        .storageClass(PVC_DEFAULT_STORAGE_CLASS)
        .build();
  }

  private List<ContainerDefinitionInfo> createStepsContainerDefinition(
      List<ExecutionWrapper> steps, IntegrationStage integrationStage, CIExecutionArgs ciExecutionArgs) {
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    if (steps == null) {
      return containerDefinitionInfos;
    }

    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    steps.forEach(executionWrapper -> {
      if (executionWrapper instanceof StepElement) {
        ContainerDefinitionInfo containerDefinitionInfo = createStepContainerDefinition(
            (StepElement) executionWrapper, integrationStage, ciExecutionArgs, portFinder);
        if (containerDefinitionInfo != null) {
          containerDefinitionInfos.add(containerDefinitionInfo);
        }
      } else if (executionWrapper instanceof ParallelStepElement) {
        ParallelStepElement parallel = (ParallelStepElement) executionWrapper;
        if (parallel.getSections() != null) {
          containerDefinitionInfos.addAll(
              parallel.getSections()
                  .stream()
                  .filter(executionWrapperInParallel -> executionWrapperInParallel instanceof StepElement)
                  .map(executionWrapperInParallel -> (StepElement) executionWrapperInParallel)
                  .map(executionWrapperInParallel
                      -> createStepContainerDefinition(
                          executionWrapperInParallel, integrationStage, ciExecutionArgs, portFinder))
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()));
        }
      }
    });
    return containerDefinitionInfos;
  }

  private ContainerDefinitionInfo createStepContainerDefinition(StepElement stepElement,
      IntegrationStage integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder) {
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return null;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return createRunStepContainerDefinition(
            (RunStepInfo) ciStepInfo, integrationStage, ciExecutionArgs, portFinder);
      case PLUGIN:
        return createPluginStepContainerDefinition((PluginStepInfo) ciStepInfo, ciExecutionArgs, portFinder);
      default:
        return null;
    }
  }

  private ContainerDefinitionInfo createRunStepContainerDefinition(RunStepInfo runStepInfo,
      IntegrationStage integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder) {
    Integer port = portFinder.getNextPort();
    runStepInfo.setPort(port);

    Map<String, String> stepEnvVars = new HashMap<>();
    stepEnvVars.putAll(getEnvVariables(integrationStage));
    stepEnvVars.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    return ContainerDefinitionInfo.builder()
        .name(runStepInfo.getIdentifier())
        .commands(StepContainerUtils.getCommand())
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .encryptedSecrets(getSecretVariables(integrationStage))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(getImageInfo(runStepInfo.getImage()))
                                   .connectorIdentifier(runStepInfo.getConnector())
                                   .build())
        .containerResourceParams(getStepContainerResource(runStepInfo.getResources()))
        .ports(Collections.singletonList(port))
        .containerType(CIContainerType.RUN)
        .build();
  }

  private ContainerDefinitionInfo createPluginStepContainerDefinition(
      PluginStepInfo pluginStepInfo, CIExecutionArgs ciExecutionArgs, PortFinder portFinder) {
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    if (!isEmpty(pluginStepInfo.getSettings())) {
      for (Map.Entry<String, String> entry : pluginStepInfo.getSettings().entrySet()) {
        String key = PLUGIN_ENV_PREFIX + entry.getKey().toUpperCase();
        envVarMap.put(key, entry.getValue());
      }
    }
    Integer port = portFinder.getNextPort();
    pluginStepInfo.setPort(port);
    return ContainerDefinitionInfo.builder()
        .name(pluginStepInfo.getIdentifier())
        .commands(StepContainerUtils.getCommand())
        .args(StepContainerUtils.getArguments(port))
        .envVars(envVarMap)
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(getImageInfo(pluginStepInfo.getImage()))
                                   .connectorIdentifier(pluginStepInfo.getConnector())
                                   .build())
        .containerResourceParams(getStepContainerResource(pluginStepInfo.getResources()))
        .ports(Collections.singletonList(port))
        .containerType(CIContainerType.PLUGIN)
        .build();
  }

  private ContainerResourceParams getStepContainerResource(ContainerResource resource) {
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
        .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
        .resourceLimitMilliCpu(getContainerCpuLimit(resource))
        .resourceLimitMemoryMiB(getContainerMemoryLimit(resource))
        .build();
  }

  private String generatePodName(IntegrationStage integrationStage) {
    // TODO Use better pod naming strategy after discussion with PM, attach build number in future
    return POD_NAME + "-" + integrationStage.getIdentifier() + random.nextInt(100000000);
  }

  private Map<String, EncryptedVariableWithType> getSecretVariables(IntegrationStage integrationStage) {
    if (isEmpty(integrationStage.getCustomVariables())) {
      return Collections.emptyMap();
    }

    return integrationStage.getCustomVariables()
        .stream()
        .filter(customVariables
            -> customVariables.getType().equals(
                "secret")) // Todo instead of hard coded secret use variable type once we have type in cdng
        .collect(toMap(CustomVariables::getName,
            customVariables -> EncryptedVariableWithType.builder().build())); // Todo Empty EncryptedDataDetail has to
    // be replaced with
    // encrypted values once cdng secret apis are ready
  }

  private Set<String> getPublishStepConnectorIdentifier(IntegrationStage integrationStage) {
    List<ExecutionWrapper> executionWrappers = integrationStage.getExecution().getSteps();
    if (isEmpty(executionWrappers)) {
      return Collections.emptySet();
    }

    Set<String> set = new HashSet<>();
    for (ExecutionWrapper executionSection : executionWrappers) {
      if (executionSection instanceof ParallelStepElement) {
        for (ExecutionWrapper executionWrapper : ((ParallelStepElement) executionSection).getSections()) {
          if (executionWrapper instanceof StepElement) {
            StepElement stepElement = (StepElement) executionWrapper;
            if (stepElement.getStepSpecType() instanceof PublishStepInfo) {
              List<Artifact> publishArtifacts = ((PublishStepInfo) stepElement.getStepSpecType()).getPublishArtifacts();
              for (Artifact artifact : publishArtifacts) {
                String connector = artifact.getConnector().getConnector();
                set.add(connector);
              }
            }
          }
        }
      } else if (executionSection instanceof StepElement) {
        if (((StepElement) executionSection).getStepSpecType() instanceof PublishStepInfo) {
          List<Artifact> publishArtifacts =
              ((PublishStepInfo) ((StepElement) executionSection).getStepSpecType()).getPublishArtifacts();
          for (Artifact artifact : publishArtifacts) {
            set.add(artifact.getConnector().getConnector());
          }
        }
      }
    }
    return set;
  }

  private Map<String, String> getEnvVariables(IntegrationStage integrationStage) {
    if (isEmpty(integrationStage.getCustomVariables())) {
      return Collections.emptyMap();
    }

    return integrationStage.getCustomVariables()
        .stream()
        .filter(customVariables
            -> customVariables.getType().equals(
                "text")) // Todo instead of hard coded text use variable type once we have type in cdng
        .collect(toMap(CustomVariables::getName, CustomVariables::getValue));
  }

  private ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
      String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
      if (subTokens.length > 2) {
        throw new InvalidRequestException("Image should not contain multiple tags");
      }
      if (subTokens.length == 2) {
        name = subTokens[0];
        tag = subTokens[1];
      }
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }

  private Integer getContainerMemoryLimit(ContainerResource resource) {
    Integer memoryLimit = DEFAULT_STEP_LIMIT_MEMORY_MIB;
    if (resource != null && resource.getLimit() != null && resource.getLimit().getMemory() > 0) {
      memoryLimit = resource.getLimit().getMemory();
    }
    return memoryLimit;
  }

  private Integer getContainerCpuLimit(ContainerResource resource) {
    Integer cpuLimit = DEFAULT_STEP_LIMIT_MILLI_CPU;
    if (resource != null && resource.getLimit() != null && resource.getLimit().getCpu() > 0) {
      cpuLimit = resource.getLimit().getCpu();
    }
    return cpuLimit;
  }

  private Integer getStageMemoryRequest(List<ExecutionWrapper> steps) {
    Integer stageMemoryRequest = 0;
    for (ExecutionWrapper step : steps) {
      Integer executionWrapperMemoryRequest = getExecutionWrapperMemoryRequest(step);
      stageMemoryRequest = Math.max(stageMemoryRequest, executionWrapperMemoryRequest);
    }
    return stageMemoryRequest;
  }

  private Integer getExecutionWrapperMemoryRequest(ExecutionWrapper executionWrapper) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperMemoryRequest = 0;
    if (executionWrapper instanceof StepElement) {
      executionWrapperMemoryRequest = getStepMemoryLimit((StepElement) executionWrapper);
    } else if (executionWrapper instanceof ParallelStepElement) {
      ParallelStepElement parallel = (ParallelStepElement) executionWrapper;
      if (parallel.getSections() != null) {
        for (ExecutionWrapper wrapper : parallel.getSections()) {
          executionWrapperMemoryRequest += getExecutionWrapperMemoryRequest(wrapper);
        }
      }
    }
    return executionWrapperMemoryRequest;
  }

  private Integer getStepMemoryLimit(StepElement stepElement) {
    Integer zeroMemory = 0;
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return zeroMemory;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return getContainerMemoryLimit(((RunStepInfo) ciStepInfo).getResources());
      case PLUGIN:
        return getContainerMemoryLimit(((PluginStepInfo) ciStepInfo).getResources());
      default:
        return zeroMemory;
    }
  }

  private Integer getStageCpuRequest(List<ExecutionWrapper> steps) {
    Integer stageCpuRequest = 0;
    for (ExecutionWrapper step : steps) {
      Integer executionWrapperCpuRequest = getExecutionWrapperCpuRequest(step);
      stageCpuRequest = Math.max(stageCpuRequest, executionWrapperCpuRequest);
    }
    return stageCpuRequest;
  }

  private Integer getExecutionWrapperCpuRequest(ExecutionWrapper executionWrapper) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperCpuRequest = 0;
    if (executionWrapper instanceof StepElement) {
      executionWrapperCpuRequest = getStepCpuLimit((StepElement) executionWrapper);
    } else if (executionWrapper instanceof ParallelStepElement) {
      ParallelStepElement parallel = (ParallelStepElement) executionWrapper;
      if (parallel.getSections() != null) {
        for (ExecutionWrapper wrapper : parallel.getSections()) {
          executionWrapperCpuRequest += getExecutionWrapperCpuRequest(wrapper);
        }
      }
    }
    return executionWrapperCpuRequest;
  }

  private Integer getStepCpuLimit(StepElement stepElement) {
    Integer zeroCpu = 0;
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return zeroCpu;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return getContainerCpuLimit(((RunStepInfo) ciStepInfo).getResources());
      case PLUGIN:
        return getContainerCpuLimit(((PluginStepInfo) ciStepInfo).getResources());
      default:
        return zeroCpu;
    }
  }
}
