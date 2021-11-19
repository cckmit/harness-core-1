package io.harness.enforcement.executions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata;
import io.harness.enforcement.beans.CustomRestrictionEvaluationDTO;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.licensing.Edition;
import io.harness.repositories.executions.CDAccountExecutionMetadataRepository;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
public class DeploymentRestrictionUsageImpl implements CustomRestrictionInterface {
  @Inject CDAccountExecutionMetadataRepository accountExecutionMetadataRepository;

  @Override
  public boolean evaluateCustomRestriction(CustomRestrictionEvaluationDTO customFeatureEvaluationDTO) {
    String accountIdentifier = customFeatureEvaluationDTO.getAccountIdentifier();
    Edition edition = customFeatureEvaluationDTO.getEdition();
    if (edition == Edition.FREE) {
      Optional<CDAccountExecutionMetadata> accountExecutionMetadata =
          accountExecutionMetadataRepository.findByAccountId(accountIdentifier);
      if (!accountExecutionMetadata.isPresent() || accountExecutionMetadata.get().getExecutionCount() <= 1100) {
        return true;
      }
      LocalDate startDate = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
      YearMonth yearMonth = YearMonth.of(startDate.getYear(), startDate.getMonth());
      return accountExecutionMetadata.get().getAccountExecutionInfo().getCountPerMonth().getOrDefault(
                 yearMonth.toString(), 0L)
          > 100;
    }
    return true;
  }
}
