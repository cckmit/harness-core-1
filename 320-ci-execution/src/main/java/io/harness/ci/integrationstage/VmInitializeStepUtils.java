package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.common.CIExecutionConstants.STEP_WORK_DIR;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.environment.VmBuildJobInfo;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class VmInitializeStepUtils {
  public BuildJobEnvInfo getInitializeStepInfoBuilder(StageElementConfig stageElementConfig,
      CIExecutionArgs ciExecutionArgs, List<ExecutionWrapperConfig> steps, String accountId) {
    ArrayList<String> connectorIdentifiers = new ArrayList<>();
    for (ExecutionWrapperConfig executionWrapper : steps) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
        validateConfig(stepElementConfig);
        String identifier = getConnectorIdentifier(stepElementConfig);
        if (identifier != null) {
          connectorIdentifiers.add(identifier);
        }
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig =
            IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
        if (isNotEmpty(parallelStepElementConfig.getSections())) {
          for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
            if (executionWrapperInParallel.getStep() == null || executionWrapperInParallel.getStep().isNull()) {
              continue;
            }
            StepElementConfig stepElementConfig =
                IntegrationStageUtils.getStepElementConfig(executionWrapperInParallel);
            validateConfig(stepElementConfig);
            String identifier = getConnectorIdentifier(stepElementConfig);
            if (identifier != null) {
              connectorIdentifiers.add(identifier);
            }
          }
        }
      }
    }
    return VmBuildJobInfo.builder()
        .ciExecutionArgs(ciExecutionArgs)
        .workDir(STEP_WORK_DIR)
        .connectorRefs(connectorIdentifiers)
        .stageVars(stageElementConfig.getVariables())
        .build();
  }

  private void validateConfig(StepElementConfig stepElementConfig) {
    if (stepElementConfig.getStepSpecType() instanceof CIStepInfo) {
      CIStepInfo ciStepInfo = (CIStepInfo) stepElementConfig.getStepSpecType();
      switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
        case RUN:
          validateRunStepConnector((RunStepInfo) ciStepInfo);
          break;
        case RUN_TESTS:
          validateRunTestsStepConnector((RunTestsStepInfo) ciStepInfo);
          break;
        default:
          return;
      }
    }
  }

  private void validateRunTestsStepConnector(RunTestsStepInfo stepInfo) {
    if (stepInfo.getImage() != null && stepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty if image is provided");
    }
    if (stepInfo.getImage() == null && stepInfo.getConnectorRef() != null) {
      throw new CIStageExecutionException("image can't be empty if connector ref is provided");
    }
  }

  private void validateRunStepConnector(RunStepInfo runStepInfo) {
    if (runStepInfo.getImage() != null && runStepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty if image is provided");
    }
    if (runStepInfo.getImage() == null && runStepInfo.getConnectorRef() != null) {
      throw new CIStageExecutionException("image can't be empty if connector ref is provided");
    }
  }

  private String getConnectorIdentifier(StepElementConfig stepElementConfig) {
    if (stepElementConfig.getStepSpecType() instanceof CIStepInfo) {
      CIStepInfo ciStepInfo = (CIStepInfo) stepElementConfig.getStepSpecType();
      switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
        case RUN:
          return resolveConnectorIdentifier(((RunStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case PLUGIN:
          return resolveConnectorIdentifier(
              ((PluginStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case RUN_TESTS:
          return resolveConnectorIdentifier(
              ((RunTestsStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case DOCKER:
        case ECR:
        case GCR:
        case SAVE_CACHE_S3:
        case RESTORE_CACHE_S3:
        case RESTORE_CACHE_GCS:
        case SAVE_CACHE_GCS:
        case UPLOAD_ARTIFACTORY:
        case UPLOAD_S3:
        case UPLOAD_GCS:
          return resolveConnectorIdentifier(
              ((PluginCompatibleStep) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        default:
          return null;
      }
    }
    return null;
  }

  private String resolveConnectorIdentifier(ParameterField<String> connectorRef, String stepIdentifier) {
    if (connectorRef != null) {
      String connectorIdentifier = resolveStringParameter("connectorRef", "Run", stepIdentifier, connectorRef, false);
      if (!StringUtils.isEmpty(connectorIdentifier)) {
        return connectorIdentifier;
      }
    }
    return null;
  }
}