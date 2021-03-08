package software.wings.service.impl.aws.model;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.api.AwsLambdaContextElement.FunctionMeta;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsLambdaFunctionResult {
  private boolean success;
  private String errorMessage;
  private FunctionMeta functionMeta;
}
