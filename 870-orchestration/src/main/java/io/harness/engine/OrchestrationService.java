package io.harness.engine;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.interrupts.Interrupt;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;

import java.util.Map;
import javax.validation.Valid;
import lombok.NonNull;

@OwnedBy(CDC)
public interface OrchestrationService {
  PlanExecution startExecution(@Valid Plan plan, @NonNull Map<String, String> setupAbstractions,
      ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata);

  Interrupt registerInterrupt(@Valid InterruptPackage interruptPackage);
}
