package io.harness.pms.sdk.creator;

import com.google.common.base.Preconditions;

import io.harness.pms.facilitators.FacilitatorObtainment;
import io.harness.pms.facilitators.FacilitatorType;
import io.harness.pms.plan.PlanNode;
import io.harness.pms.plan.common.creator.PlanCreationContext;
import io.harness.pms.plan.common.creator.PlanCreationResponse;
import io.harness.pms.plan.common.yaml.YamlField;
import io.harness.pms.plan.common.yaml.YamlNode;
import io.harness.pms.sdk.io.MapStepParameters;
import io.harness.pms.steps.StepType;

import java.util.Set;

public abstract class SimpleStepPlanCreator implements PartialPlanCreator {
  public abstract Set<String> getSupportedStepTypes();

  @Override
  public boolean supportsField(YamlField field) {
    YamlNode yamlNode = field.getNode();
    String type = yamlNode.getType();
    return type != null && getSupportedStepTypes().contains(type);
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, YamlField field) {
    YamlNode yamlNode = field.getNode();
    YamlNode specYamlNode = Preconditions.checkNotNull(yamlNode.getField("spec")).getNode();
    PlanNode stepPlanNode =
        PlanNode.newBuilder()
            .setUuid(yamlNode.getUuid())
            .setIdentifier(yamlNode.getIdentifier())
            .setStepType(StepType.newBuilder().setType(yamlNode.getType()).build())
            .setName(yamlNode.getNameOrIdentifier())
            .setGroup(yamlNode.getType())
            .setStepParameters(ctx.toByteString(new MapStepParameters("spec", specYamlNode.toString())))
            .addFacilitatorObtainments(FacilitatorObtainment.newBuilder()
                                           .setType(FacilitatorType.newBuilder().setType("SYNC").build())
                                           .build())
            .setSkipExpressionChain(false)
            .build();

    return PlanCreationResponse.builder().node(stepPlanNode.getUuid(), stepPlanNode).build();
  }
}
