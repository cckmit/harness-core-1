package software.wings.api;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import software.wings.beans.container.Label;
import software.wings.sm.StepExecutionSummary;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class KubernetesSteadyStateCheckExecutionSummary extends StepExecutionSummary {
  private List<Label> labels;
  private String namespace;

  @Builder
  public KubernetesSteadyStateCheckExecutionSummary(List<Label> labels, String namespace) {
    this.labels = labels;
    this.namespace = namespace;
  }
}
