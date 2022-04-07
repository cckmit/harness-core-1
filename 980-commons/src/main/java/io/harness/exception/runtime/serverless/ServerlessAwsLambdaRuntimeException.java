/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime.serverless;

import static io.harness.eraro.ErrorCode.SERVERLESS_EXECUTION_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;

import java.util.EnumSet;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(HarnessTeam.CDP)
@EqualsAndHashCode(callSuper = false)
public class ServerlessAwsLambdaRuntimeException extends WingsException {
  private final String message;
  private static final String MESSAGE_ARG = "message";

  public ServerlessAwsLambdaRuntimeException(String message) {
    super(message, null, SERVERLESS_EXECUTION_ERROR, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    this.message = message;
    super.param(MESSAGE_ARG, message);
  }

  public ServerlessAwsLambdaRuntimeException(String message, Throwable cause) {
    super(message, cause, SERVERLESS_EXECUTION_ERROR, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    this.message = message;
    super.param(MESSAGE_ARG, message);
  }
}
