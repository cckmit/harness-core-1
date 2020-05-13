package io.harness.states;

import static java.util.stream.Collectors.toList;
import static software.wings.common.CICommonPodConstants.CONTAINER_NAME;
import static software.wings.common.CICommonPodConstants.MOUNT_PATH;
import static software.wings.common.CICommonPodConstants.NAMESPACE;
import static software.wings.common.CICommonPodConstants.POD_NAME;

import com.google.inject.Inject;

import io.harness.ambiance.Ambiance;
import io.harness.annotations.Produces;
import io.harness.beans.steps.BuildStepInfo;
import io.harness.execution.status.NodeExecutionStatus;
import io.harness.facilitator.PassThroughData;
import io.harness.facilitator.modes.sync.SyncExecutable;
import io.harness.managerclient.ManagerCIResource;
import io.harness.network.SafeHttpCall;
import io.harness.state.State;
import io.harness.state.StateType;
import io.harness.state.io.StateParameters;
import io.harness.state.io.StateResponse;
import io.harness.state.io.StateTransput;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.ci.K8ExecCommandParams;
import software.wings.beans.ci.ShellScriptType;

import java.util.List;

/**
 * This state will execute build command on already setup pod. It will send customer defined commands.
 * Currently it assumes a timeout of 60 minutes
 */

@Produces(State.class)
@Slf4j
public class BuildState implements State, SyncExecutable {
  @Inject private ManagerCIResource managerCIResource;

  // TODO Async can not be supported at this point. We have to build polling framework on CI manager.
  //     Async will be supported once we will have delegate microservice ready.

  @Override
  public StateResponse executeSync(
      Ambiance ambiance, StateParameters parameters, List<StateTransput> inputs, PassThroughData passThroughData) {
    try {
      BuildStepInfo buildStepInfo = (BuildStepInfo) parameters;

      String stdoutFilePath = MOUNT_PATH + "/stdout";
      String stderrFilePath = MOUNT_PATH + "/stderr";
      Integer commandTimeoutSecs = 3600;
      List<String> commandList = buildStepInfo.getScriptInfos()
                                     .stream()
                                     .map(scriptInfo -> { return scriptInfo.getScriptString(); })
                                     .collect(toList());

      K8ExecCommandParams k8ExecCommandParams = K8ExecCommandParams.builder()
                                                    .podName(POD_NAME)
                                                    .containerName(CONTAINER_NAME)
                                                    .stdoutFilePath(stdoutFilePath)
                                                    .stderrFilePath(stderrFilePath)
                                                    .commandTimeoutSecs(commandTimeoutSecs)
                                                    .scriptType(ShellScriptType.DASH)
                                                    .commands(commandList)
                                                    .namespace(NAMESPACE)
                                                    .build();

      // TODO Use k8 connector from element input and, handle response

      SafeHttpCall.execute(managerCIResource.podCommandExecutionTask("kubernetes_clusterqqq", k8ExecCommandParams));

      return StateResponse.builder().status(NodeExecutionStatus.SUCCEEDED).build();
    } catch (Exception e) {
      logger.error("state execution failed", e);
    }

    return StateResponse.builder().status(NodeExecutionStatus.SUCCEEDED).build();
  }

  @Override
  public StateType getType() {
    return BuildStepInfo.stateType;
  }
}
