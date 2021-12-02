package io.harness.cdng.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("HelmStepPassThroughData")
@RecasterAlias("io.harness.cdng.helm.HelmStepPassThroughData")
public class NativeHelmStepPassThroughData implements PassThroughData {
  ManifestOutcome helmChartManifestOutcome;
  List<ValuesManifestOutcome> valuesManifestOutcomes;
  InfrastructureOutcome infrastructure;
  String helmValuesFileContent;
}