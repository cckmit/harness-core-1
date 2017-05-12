package software.wings.service.intfc.appdynamics;

import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.service.impl.appdynamics.AppdynamicsApplication;
import software.wings.service.impl.appdynamics.AppdynamicsBusinessTransaction;
import software.wings.service.impl.appdynamics.AppdynamicsTier;

import java.io.IOException;
import java.util.List;

/**
 * Created by rsingh on 4/17/17.
 */
public interface AppdynamicsDelegateService {
  @DelegateTaskType(TaskType.APPDYNAMICS_GET_APP_TASK)
  List<AppdynamicsApplication> getAllApplications(final AppDynamicsConfig appDynamicsConfig) throws IOException;

  @DelegateTaskType(TaskType.APPDYNAMICS_GET_TIER_TASK)
  List<AppdynamicsTier> getTiers(AppDynamicsConfig value, int appdynamicsAppId) throws IOException;

  @DelegateTaskType(TaskType.APPDYNAMICS_CONFIGURATION_VALIDATE_TASK)
  void validateConfig(AppDynamicsConfig appDynamicsConfig) throws IOException;

  @DelegateTaskType(TaskType.APPDYNAMICS_GET_BUSINESS_TRANSACTION_TASK)
  List<AppdynamicsBusinessTransaction> getBusinessTransactions(
      AppDynamicsConfig appDynamicsConfig, long appdynamicsAppId) throws IOException;
}
