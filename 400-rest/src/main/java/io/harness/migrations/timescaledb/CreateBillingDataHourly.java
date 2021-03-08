package io.harness.migrations.timescaledb;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

@TargetModule(Module._390_DB_MIGRATION)
public class CreateBillingDataHourly extends AbstractTimeScaleDBMigration {
  @Override
  public String getFileName() {
    return "timescaledb/create_billing_data_hourly_table.sql";
  }
}
