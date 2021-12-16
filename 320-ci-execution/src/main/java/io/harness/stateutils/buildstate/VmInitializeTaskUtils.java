package io.harness.stateutils.buildstate;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.VmBuildJobInfo;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.logserviceclient.CILogServiceUtils;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.tiserviceclient.TIServiceUtils;
import io.harness.util.CIVmSecretEvaluator;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class VmInitializeTaskUtils {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject CILogServiceUtils logServiceUtils;
  @Inject TIServiceUtils tiServiceUtils;
  @Inject CodebaseUtils codebaseUtils;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;

  public CIVmInitializeTaskParams getInitializeTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance, String logPrefix) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();

    if (infrastructure == null || ((VmInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    VmInfraYaml vmInfraYaml = (VmInfraYaml) infrastructure;
    if (vmInfraYaml.getSpec().getType() != VmInfraSpec.Type.POOL) {
      throw new CIStageExecutionException(
          format("Invalid VM infrastructure spec type: %s", vmInfraYaml.getSpec().getType()));
    }

    VmPoolYaml vmPoolYaml = (VmPoolYaml) vmInfraYaml.getSpec();
    String poolId = vmPoolYaml.getSpec().getIdentifier();
    executionSweepingOutputResolver.consume(ambiance, STAGE_INFRA_DETAILS,
        VmStageInfraDetails.builder().poolId(poolId).build(), StepOutcomeGroup.STAGE.name());

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
    }

    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();
    String accountID = AmbianceUtils.getAccountId(ambiance);
    VmBuildJobInfo vmBuildJobInfo = (VmBuildJobInfo) initializeStepInfo.getBuildJobEnvInfo();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    ConnectorDetails gitConnector = codebaseUtils.getGitConnector(
        ngAccess, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone());
    Map<String, String> codebaseEnvVars = codebaseUtils.getCodebaseVars(ambiance, vmBuildJobInfo.getCiExecutionArgs());
    Map<String, String> gitEnvVars = codebaseUtils.getGitEnvVariables(gitConnector, initializeStepInfo.getCiCodebase());

    Map<String, String> envVars = new HashMap<>();
    envVars.putAll(codebaseEnvVars);
    envVars.putAll(gitEnvVars);

    Map<String, String> stageVars = getEnvironmentVariables(
        NGVariablesUtils.getMapOfVariables(vmBuildJobInfo.getStageVars(), ambiance.getExpressionFunctorToken()));
    CIVmSecretEvaluator ciVmSecretEvaluator = CIVmSecretEvaluator.builder().build();
    Set<String> secrets = ciVmSecretEvaluator.resolve(stageVars, ngAccess, ambiance.getExpressionFunctorToken());
    envVars.putAll(stageVars);

    return CIVmInitializeTaskParams.builder()
        .poolID(poolId)
        .workingDir(vmBuildJobInfo.getWorkDir())
        .environment(envVars)
        .gitConnector(gitConnector)
        .stageRuntimeId(stageDetails.getStageRuntimeID())
        .accountID(accountID)
        .orgID(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectID(AmbianceUtils.getProjectIdentifier(ambiance))
        .pipelineID(ambiance.getMetadata().getPipelineIdentifier())
        .stageID(stageDetails.getStageID())
        .buildID(String.valueOf(ambiance.getMetadata().getRunSequence()))
        .logStreamUrl(logServiceUtils.getLogServiceConfig().getBaseUrl())
        .logSvcToken(getLogSvcToken(accountID))
        .tiUrl(tiServiceUtils.getTiServiceConfig().getBaseUrl())
        .tiSvcToken(getTISvcToken(accountID))
        .secrets(new ArrayList<>(secrets))
        .build();
  }

  public Map<String, String> getEnvironmentVariables(Map<String, Object> inputVariables) {
    if (EmptyPredicate.isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.getValue() == null) {
          throw new CIStageExecutionException(String.format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.getValue().toString());
      } else if (value instanceof String) {
        res.put(key, (String) value);
      } else {
        log.error(String.format(
            "Value other than String or ParameterField found for env. variable [%s]. value: [%s]", key, value));
      }
    });
    return res;
  }

  private String getLogSvcToken(String accountID) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch log service token attempt: {}"),
            format("Failed to fetch log service token after retrying {} times"));
    return Failsafe.with(retryPolicy).get(() -> logServiceUtils.getLogServiceToken(accountID));
  }

  private String getTISvcToken(String accountID) {
    // Make a call to the TI service and get back the token. We do not need TI service token for all steps,
    // so we can continue even if the service is down.
    try {
      return tiServiceUtils.getTIServiceToken(accountID);
    } catch (Exception e) {
      log.error("Could not call token endpoint for TI service", e);
    }

    return "";
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}