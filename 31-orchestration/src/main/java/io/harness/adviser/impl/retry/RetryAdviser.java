package io.harness.adviser.impl.retry;

import io.harness.adviser.Advise;
import io.harness.adviser.Adviser;
import io.harness.adviser.AdviserType;
import io.harness.adviser.AdvisingEvent;
import io.harness.annotations.Produces;
import io.harness.annotations.Redesign;

@Redesign
@Produces(Adviser.class)
public class RetryAdviser implements Adviser {
  @Override
  public Advise onAdviseEvent(AdvisingEvent advisingEvent) {
    return null;
  }

  @Override
  public AdviserType getType() {
    return AdviserType.builder().type(AdviserType.RETRY).build();
  }
}
