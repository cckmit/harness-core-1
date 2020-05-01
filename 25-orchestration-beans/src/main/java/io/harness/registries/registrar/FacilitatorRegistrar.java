package io.harness.registries.registrar;

import io.harness.facilitate.Facilitator;

import java.util.Set;

public interface FacilitatorRegistrar extends EngineRegistrar<Facilitator> {
  void register(Set<Class<? extends Facilitator>> facilitatorClasses);
}
