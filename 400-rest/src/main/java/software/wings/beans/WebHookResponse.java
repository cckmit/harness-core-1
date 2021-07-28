package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import lombok.Builder;
import lombok.Data;

@TargetModule(HarnessModule._959_CG_BEANS)
@OwnedBy(CDC)
@Data
@Builder
public class WebHookResponse {
  private String requestId;
  private String status;
  private String error;
  private String uiUrl;
  private String apiUrl;
  private String message;
}
