package io.harness.cvng.activity.services.api;

import io.harness.cvng.activity.beans.ActivityDashboardDTO;
import io.harness.cvng.activity.beans.ActivityVerificationResultDTO;
import io.harness.cvng.activity.beans.DeploymentActivityPopoverResultDTO;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO;
import io.harness.cvng.activity.beans.DeploymentActivityVerificationResultDTO;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.beans.ActivityDTO;

import java.time.Instant;
import java.util.List;

public interface ActivityService {
  Activity get(String activityId);
  Activity getByVerificationJobInstanceId(String verificationJobInstanceId);
  void register(String accountId, String webhookToken, ActivityDTO activityDTO);

  List<DeploymentActivityVerificationResultDTO> getRecentDeploymentActivityVerifications(
      String accountId, String orgIdentifier, String projectIdentifier);

  DeploymentActivityResultDTO getDeploymentActivityVerificationsByTag(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, String deploymentTag);
  DeploymentActivityPopoverResultDTO getDeploymentActivityVerificationsPopoverSummary(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, String deploymentTag);
  Activity getActivityFromDTO(ActivityDTO activityDTO);

  String getDeploymentTagFromActivity(String accountId, String verificationJobInstanceId);

  String createActivity(Activity activity);

  List<ActivityDashboardDTO> listActivitiesInTimeRange(
      String orgIdentifier, String projectIdentifier, String environmentIdentifier, Instant startTime, Instant endTime);

  List<ActivityVerificationResultDTO> getRecentActivityVerificationResults(
      String orgIdentifier, String projectIdentifier, int size);
  ActivityVerificationResultDTO getActivityVerificationResult(String accountId, String activityId);
}
