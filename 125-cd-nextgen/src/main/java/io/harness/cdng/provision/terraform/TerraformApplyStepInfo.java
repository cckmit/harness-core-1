package io.harness.cdng.provision.terraform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.provision.terraform.TerraformApplyStepParameters.TerraformApplyStepParametersBuilder;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.facilitator.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.Validator;
import io.harness.walktree.beans.LevelNode;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("terraformApplyStepInfo")
@JsonTypeName(StepSpecTypeConstants.TERRAFORM_APPLY)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TerraformApplyStepInfo extends TerraformApplyBaseStepInfo implements CDStepInfo, Visitable {
  @JsonProperty("configuration") TerrformStepConfiguration terrformStepConfiguration;

  @Builder(builderMethodName = "infoBuilder")
  public TerraformApplyStepInfo(
      ParameterField<String> provisionerIdentifier, TerrformStepConfiguration terrformStepConfiguration) {
    super(provisionerIdentifier);
    this.terrformStepConfiguration = terrformStepConfiguration;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return TerraformApplyStep.STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    TerraformApplyStepParametersBuilder builder =
        TerraformApplyStepParameters.infoBuilder().provisionerIdentifier(provisionerIdentifier);
    Validator.notNullCheck("Terraform Step configuration is null", terrformStepConfiguration);
    TerraformStepConfigurationType stepConfigurationType =
        terrformStepConfiguration.getTerraformStepConfigurationType();
    if (TerraformStepConfigurationType.INLINE == stepConfigurationType) {
      builder.configuration(terrformStepConfiguration.toStepParameters());
    }
    return builder.build();
  }

  @Override
  public LevelNode getLevelNode() {
    return LevelNode.builder().qualifierName(StepSpecTypeConstants.TERRAFORM_APPLY).isPartOfFQN(false).build();
  }
}
