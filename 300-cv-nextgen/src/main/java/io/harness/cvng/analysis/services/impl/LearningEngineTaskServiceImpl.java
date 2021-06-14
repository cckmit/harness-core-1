package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.CVConstants.SERVICE_BASE_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.LEARNING_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.MARK_FAILURE_PATH;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.beans.AccountMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class LearningEngineTaskServiceImpl implements LearningEngineTaskService {
  @Inject private HPersistence hPersistence;
  @Inject private MetricService metricService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private Clock clock;

  @Override
  public LearningEngineTask getNextAnalysisTask() {
    return getNextAnalysisTask(null);
  }

  @Override
  public LearningEngineTask getNextAnalysisTask(List<LearningEngineTaskType> taskType) {
    Query<LearningEngineTask> learningEngineTaskQuery =
        hPersistence.createQuery(LearningEngineTask.class)
            .filter(LearningEngineTaskKeys.taskStatus, ExecutionStatus.QUEUED)
            .order(Sort.ascending(LearningEngineTaskKeys.taskPriority));
    // TODO: add ordering based on createdAt.

    if (isNotEmpty(taskType)) {
      learningEngineTaskQuery.field(LearningEngineTaskKeys.analysisType).in(taskType);
    }

    UpdateOperations<LearningEngineTask> updateOperations =
        hPersistence.createUpdateOperations(LearningEngineTask.class);
    updateOperations.set(LearningEngineTaskKeys.taskStatus, ExecutionStatus.RUNNING)
        .set(LearningEngineTaskKeys.pickedAt, clock.instant());
    return hPersistence.findAndModify(learningEngineTaskQuery, updateOperations, new FindAndModifyOptions());
  }

  @Override
  public List<String> createLearningEngineTasks(List<LearningEngineTask> tasks) {
    Preconditions.checkNotNull(tasks, "tasks can not be null");
    Preconditions.checkArgument(tasks.size() > 0, "List size can not be zero");
    return tasks.stream().map(task -> createLearningEngineTask(task)).collect(Collectors.toList());
  }

  @Override
  public String createLearningEngineTask(LearningEngineTask learningEngineTask) {
    learningEngineTask.setTaskStatus(ExecutionStatus.QUEUED);
    VerificationTask verificationTask = verificationTaskService.get(learningEngineTask.getVerificationTaskId());
    learningEngineTask.setAccountId(verificationTask.getAccountId());
    return hPersistence.save(learningEngineTask);
  }

  @Override
  public Map<String, ExecutionStatus> getTaskStatus(Set<String> taskIds) {
    if (isNotEmpty(taskIds)) {
      Map<String, ExecutionStatus> taskStatuses = new HashMap<>();
      List<LearningEngineTask> tasks = hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
                                           .field(LearningEngineTaskKeys.uuid)
                                           .in(taskIds)
                                           .asList();

      List<String> timedOutTaskIds = new ArrayList<>();
      if (tasks != null) {
        tasks.forEach(task -> {
          if (hasTaskTimedOut(task)) {
            // TODO: this should be done using iterator.
            log.info("LearningEngineTask {} for verificationTaskId {} has TIMEDOUT", task.getUuid(),
                task.getVerificationTaskId());
            incTaskStatusMetric(task.getAccountId(), ExecutionStatus.TIMEOUT);
            task.setTaskStatus(ExecutionStatus.TIMEOUT);
            timedOutTaskIds.add(task.getUuid());
          }
          taskStatuses.put(task.getUuid(), task.getTaskStatus());
        });
      }
      if (isNotEmpty(timedOutTaskIds)) {
        UpdateOperations updateOperations = hPersistence.createUpdateOperations(LearningEngineTask.class)
                                                .set(LearningEngineTaskKeys.taskStatus, ExecutionStatus.TIMEOUT);
        Query<LearningEngineTask> timeoutQuery =
            hPersistence.createQuery(LearningEngineTask.class).field(LearningEngineTaskKeys.uuid).in(timedOutTaskIds);

        hPersistence.update(timeoutQuery, updateOperations);
      }
      return taskStatuses;
    }
    return null;
  }

  private void incTaskStatusMetric(String accountId, ExecutionStatus status) {
    try (AccountMetricContext accountMetricContext = new AccountMetricContext(accountId)) {
      metricService.incCounter(CVNGMetricsUtils.getLearningEngineTaskStatusMetricName(status));
    }
  }

  private void addTimeToFinishMetrics(LearningEngineTask task) {
    try (AccountMetricContext accountMetricContext = new AccountMetricContext(task.getAccountId())) {
      metricService.recordDuration(CVNGMetricsUtils.LEARNING_ENGINE_TASK_TOTAL_TIME, task.totalTime(clock.instant()));
      if (task.getPickedAt() != null) { // Remove this in the future after lastPickedAt is populated.
        metricService.recordDuration(CVNGMetricsUtils.LEARNING_ENGINE_TASK_WAIT_TIME, task.waitTime());
        metricService.recordDuration(
            CVNGMetricsUtils.LEARNING_ENGINE_TASK_RUNNING_TIME, task.runningTime(clock.instant()));
      }
    }
  }

  @Override
  public String createFailureUrl(String taskId) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LEARNING_RESOURCE + "/" + MARK_FAILURE_PATH);
    uriBuilder.addParameter("taskId", taskId);
    try {
      return uriBuilder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public LearningEngineTask get(String learningEngineTaskId) {
    return hPersistence.get(LearningEngineTask.class, learningEngineTaskId);
  }

  @Override
  public void markCompleted(String taskId) {
    Preconditions.checkNotNull(taskId, "taskId can not be null.");
    UpdateOperations<LearningEngineTask> updateOperations =
        hPersistence.createUpdateOperations(LearningEngineTask.class);
    // TODO: add metrics to capture total time taken from running to success.
    updateOperations.set(LearningEngineTaskKeys.taskStatus, ExecutionStatus.SUCCESS);
    hPersistence.update(hPersistence.createQuery(LearningEngineTask.class).filter(LearningEngineTaskKeys.uuid, taskId),
        updateOperations);
    LearningEngineTask task = get(taskId);
    incTaskStatusMetric(task.getAccountId(), ExecutionStatus.SUCCESS);
    addTimeToFinishMetrics(task);
  }

  @Override
  public void markFailure(String taskId) {
    Preconditions.checkNotNull(taskId, "taskId can not be null.");
    UpdateOperations<LearningEngineTask> updateOperations =
        hPersistence.createUpdateOperations(LearningEngineTask.class);
    updateOperations.set(LearningEngineTaskKeys.taskStatus, ExecutionStatus.FAILED);
    hPersistence.update(hPersistence.createQuery(LearningEngineTask.class).filter(LearningEngineTaskKeys.uuid, taskId),
        updateOperations);
    incTaskStatusMetric(get(taskId).getAccountId(), ExecutionStatus.FAILED);
  }

  private boolean hasTaskTimedOut(LearningEngineTask task) {
    if (task != null && task.getTaskStatus().equals(ExecutionStatus.RUNNING)) {
      Instant tenMinsAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
      if (Instant.ofEpochMilli(task.getLastUpdatedAt()).isBefore(tenMinsAgo)) {
        return true;
      }
    }
    return false;
  }
}
