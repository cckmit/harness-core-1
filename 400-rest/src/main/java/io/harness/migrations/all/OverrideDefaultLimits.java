package io.harness.migrations.all;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.limits.ActionType;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.impl.model.RateLimit;
import io.harness.migrations.Migration;

import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;

@TargetModule(Module._390_DB_MIGRATION)
public class OverrideDefaultLimits implements Migration {
  @Inject private LimitConfigurationService limitConfigurationService;

  @Override
  public void migrate() {
    // iHerb
    limitConfigurationService.configure(
        "bwBVO7N0RmKltRhTjk101A", ActionType.DEPLOY, new RateLimit(400, 24, TimeUnit.HOURS));
  }
}
