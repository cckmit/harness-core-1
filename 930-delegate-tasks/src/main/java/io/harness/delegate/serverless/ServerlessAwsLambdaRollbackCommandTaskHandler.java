/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaRollbackResult;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaRollbackResult.ServerlessAwsLambdaRollbackResultBuilder;
import io.harness.delegate.task.serverless.*;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessRollbackRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse.ServerlessRollbackResponseBuilder;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandUnitConstants;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@NoArgsConstructor
public class ServerlessAwsLambdaRollbackCommandTaskHandler extends ServerlessCommandTaskHandler {
  @Inject private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Inject private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Inject private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;

  private ServerlessClient serverlessClient;
  private ServerlessAwsLambdaManifestConfig serverlessManifestConfig;
  private ServerlessAwsLambdaManifestSchema serverlessManifestSchema;
  private ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig;
  private ServerlessAwsLambdaConfig serverlessAwsLambdaConfig;
  private long timeoutInMillis;

  @Override
  protected ServerlessCommandResponse executeTaskInternal(ServerlessCommandRequest serverlessCommandRequest,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(serverlessCommandRequest instanceof ServerlessRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("serverlessCommandRequest", "Must be instance of ServerlessRollbackRequest"));
    }
    ServerlessRollbackRequest serverlessRollbackRequest = (ServerlessRollbackRequest) serverlessCommandRequest;
    if (!(serverlessRollbackRequest.getServerlessInfraConfig() instanceof ServerlessAwsLambdaInfraConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessInfraConfig", "Must be instance of ServerlessAwsLambdaInfraConfig"));
    }
    if (!(serverlessRollbackRequest.getServerlessManifestConfig() instanceof ServerlessAwsLambdaManifestConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessManifestConfig", "Must be instance of ServerlessAwsLambdaManifestConfig"));
    }
    if (!(serverlessRollbackRequest.getServerlessRollbackConfig() instanceof ServerlessAwsLambdaRollbackConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessRollbackConfig", "Must be instance of ServerlessAwsLambdaRollbackConfig"));
    }
    timeoutInMillis = serverlessRollbackRequest.getTimeoutIntervalInMin() * 60000;
    serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessRollbackRequest.getServerlessInfraConfig();
    LogCallback initLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.init.toString(), true, commandUnitsProgress);
    init(serverlessRollbackRequest, initLogCallback, serverlessDelegateTaskParams);
    LogCallback rollbackLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    return rollback(serverlessRollbackRequest, rollbackLogCallback, serverlessDelegateTaskParams);
  }

  private void init(ServerlessRollbackRequest serverlessRollbackRequest, LogCallback executionLogCallback,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing...\n");
    ServerlessCliResponse response;
    serverlessManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessRollbackRequest.getServerlessManifestConfig();
    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig,
        serverlessRollbackRequest.getAccountId(), executionLogCallback, serverlessDelegateTaskParams);
    serverlessTaskHelperBase.replaceManifestWithRenderedContent(serverlessDelegateTaskParams, serverlessManifestConfig);
    serverlessAwsLambdaConfig = (ServerlessAwsLambdaConfig) serverlessInfraConfigHelper.createServerlessConfig(
        serverlessRollbackRequest.getServerlessInfraConfig());
    serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());
    serverlessAwsCommandTaskHelper.configCredential(serverlessClient, serverlessAwsLambdaConfig,
        serverlessDelegateTaskParams, executionLogCallback, true, timeoutInMillis);
    serverlessManifestSchema = serverlessAwsCommandTaskHelper.parseServerlessManifest(serverlessManifestConfig);
    serverlessAwsCommandTaskHelper.installPlugins(serverlessManifestSchema, serverlessDelegateTaskParams,
        executionLogCallback, serverlessClient, timeoutInMillis, serverlessManifestConfig);
    executionLogCallback.saveExecutionLog("Done..\n", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  private ServerlessRollbackResponse rollback(ServerlessRollbackRequest serverlessRollbackRequest,
      LogCallback executionLogCallback, ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog("Rollback..\n");
    ServerlessCliResponse response;
    ServerlessAwsLambdaRollbackConfig serverlessAwsLambdaRollbackConfig =
        (ServerlessAwsLambdaRollbackConfig) serverlessRollbackRequest.getServerlessRollbackConfig();
    response = serverlessAwsCommandTaskHelper.rollback(serverlessClient, serverlessDelegateTaskParams,
        executionLogCallback, serverlessAwsLambdaRollbackConfig, timeoutInMillis, serverlessManifestConfig,
        serverlessAwsLambdaInfraConfig);
    ServerlessAwsLambdaRollbackResultBuilder serverlessAwsLambdaRollbackResultBuilder =
        ServerlessAwsLambdaRollbackResult.builder();
    serverlessAwsLambdaRollbackResultBuilder.service(serverlessManifestSchema.getService());
    serverlessAwsLambdaRollbackResultBuilder.region(serverlessAwsLambdaInfraConfig.getRegion());
    serverlessAwsLambdaRollbackResultBuilder.stage(serverlessAwsLambdaInfraConfig.getStage());
    ServerlessRollbackResponseBuilder serverlessRollbackResponseBuilder = ServerlessRollbackResponse.builder();
    if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      serverlessAwsLambdaRollbackResultBuilder.rollbackTimeStamp(
          serverlessAwsLambdaRollbackConfig.getPreviousVersionTimeStamp());
      executionLogCallback.saveExecutionLog("Done..\n", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      serverlessRollbackResponseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
    } else {
      // todo: set error message and error handling
      executionLogCallback.saveExecutionLog("Failed..\n", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      serverlessRollbackResponseBuilder.commandExecutionStatus(CommandExecutionStatus.FAILURE);
    }
    serverlessRollbackResponseBuilder.serverlessRollbackResult(serverlessAwsLambdaRollbackResultBuilder.build());
    return serverlessRollbackResponseBuilder.build();
  }
}
