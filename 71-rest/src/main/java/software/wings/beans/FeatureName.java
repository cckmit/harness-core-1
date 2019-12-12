package software.wings.beans;

import lombok.Getter;
import software.wings.beans.FeatureFlag.Scope;

/**
 * Add your feature name here. When the feature is fully launched and no longer needs to be flagged,
 * delete the feature name.
 */
public enum FeatureName {
  AWS_CLOUD_FORMATION_TEMPLATE,
  CV_DEMO,
  LOGML_NEURAL_NET,
  GIT_BATCH_SYNC,
  GLOBAL_CV_DASH,
  CV_SUCCEED_FOR_ANOMALY,
  COPY_ARTIFACT,
  INLINE_SSH_COMMAND,
  CUSTOM_WORKFLOW,
  ECS_DELEGATE,
  USE_QUARTZ_JOBS,
  CV_DATA_COLLECTION_JOB,
  THREE_PHASE_SECRET_DECRYPTION,
  DELEGATE_CAPABILITY_FRAMEWORK,
  GRAPHQL,
  SHELL_SCRIPT_ENV,
  REMOVE_STENCILS,
  DISABLE_METRIC_NAME_CURLY_BRACE_CHECK,
  GLOBAL_DISABLE_HEALTH_CHECK(Scope.GLOBAL),
  GIT_HTTPS_KERBEROS,
  TRIGGER_FOR_ALL_ARTIFACTS,
  USE_PCF_CLI,
  AUDIT_TRAIL_UI,
  ARTIFACT_STREAM_REFACTOR,
  TRIGGER_REFACTOR,
  TRIGGER_YAML,
  CV_FEEDBACKS,
  CV_HOST_SAMPLING,
  CUSTOM_DASHBOARD,
  SEND_LOG_ANALYSIS_COMPRESSED,
  SSH_SHORT_VALIDATION_TIMEOUT,
  PERPETUAL_TASK_SERVICE(Scope.GLOBAL),
  CCM_EVENT_COLLECTION,
  INFRA_MAPPING_REFACTOR,
  GRAPHQL_DEV,
  SUPERVISED_TS_THRESHOLD,
  REJECT_TRIGGER_IF_ARTIFACTS_NOT_MATCH,
  TAGS_YAML,
  NEW_INSTANCE_TIMESERIES,
  SPOTINST,
  ENTITY_AUDIT_RECORD,
  TIME_RANGE_FREEZE_GOVERNANCE,
  SCIM_INTEGRATION,
  SPLUNK_CV_TASK,
  NEW_RELIC_CV_TASK,
  ELK_CV_TASK,
  ELK_24_7_CV_TASK,
  SLACK_APPROVALS,
  SPLUNK_24_7_CV_TASK,
  NEWRELIC_24_7_CV_TASK,
  SEARCH(Scope.GLOBAL),
  PCF_MANIFEST_REDESIGN,
  SERVERLESS_DASHBOARD_AWS_LAMBDA,
  GLOBAL_KMS_PRE_PROCESSING,
  BATCH_SECRET_DECRYPTION,
  PIPELINE_GOVERNANCE,
  ADD_COMMAND,
  STACKDRIVER_SERVICEGUARD,
  SEARCH_REQUEST,
  ON_DEMAND_ROLLBACK,
  WORKFLOW_VERIFICATION_REMOVE_CRON,
  TIME_SERIES_WORKFLOW_V2,
  BIND_FETCH_FILES_TASK_TO_DELEGATE,
  DEFAULT_ARTIFACT,
  DEPLOY_TO_SPECIFIC_HOSTS,
  CUSTOM_LOGS_SERVICEGUARD,
  LOGIN_POST_REQUEST,
  PCF_CUSTOM_PLUGIN_SUPPORT,
  UI_ALLOW_K8S_V1,
  SEND_SLACK_NOTIFICATION_FROM_DELEGATE,
  TIME_SERIES_SERVICEGUARD_V2,
  TF_USE_VAR_CL,
  GOOGLE_KMS;

  FeatureName() {
    scope = Scope.PER_ACCOUNT;
  }

  FeatureName(Scope scope) {
    this.scope = scope;
  }

  @Getter private FeatureFlag.Scope scope;
}
