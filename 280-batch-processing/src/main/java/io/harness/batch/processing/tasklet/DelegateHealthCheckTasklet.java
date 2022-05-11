/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecordDao;

import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import software.wings.service.impl.DelegateServiceImpl;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Singleton
public class DelegateHealthCheckTasklet implements Tasklet {
  @Autowired private PerpetualTaskRecordDao perpetualTaskRecordDao;
  @Autowired private LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;
  @Autowired private DelegateServiceImpl delegateService;

  private static final int BATCH_SIZE = 20;
  private static final long DELAY_IN_MINUTES_FOR_LAST_RECEIVED_MSG = 90;
  private static final long MINUTES_FOR_HEALTHY_DELEGATE_HEARTBEAT = 5;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    List<PerpetualTaskRecord> perpetualTasks =
        perpetualTaskRecordDao.listValidK8sWatchPerpetualTasksForAccount(accountId);
    List<String> clusterIds = new ArrayList<>();
    Map<String, String> clusterIdToDelegateIdMap = new HashMap<>();
    for (PerpetualTaskRecord perpetualTask : perpetualTasks) {
      String clusterId;
      if (perpetualTask.getTaskDescription().equals("NG")) {
        String clientId = perpetualTask.getClientContext().getClientId();
        clusterId = clientId.substring(clientId.lastIndexOf('/') + 1);
      } else {
        clusterId = perpetualTask.getClientContext().getClientParams().get("clusterId");
      }
      clusterIds.add(clusterId);
      clusterIdToDelegateIdMap.put(clusterId, perpetualTask.getDelegateId());
    }
    Instant allowedTime = startTime.minus(Duration.ofMinutes(DELAY_IN_MINUTES_FOR_LAST_RECEIVED_MSG));
    for (List<String> clusterIdsBatch : Lists.partition(clusterIds, BATCH_SIZE)) {
      List<String> delegateIds = clusterIdsBatch.stream().map(clusterIdToDelegateIdMap::get)
          .collect(Collectors.toList());
      log.info("delegate list size: {}, delegates: {}" , delegateIds.size(), delegateIds);
      List<Delegate> delegates = delegateService.obtainDelegateDetails(accountId, delegateIds);
      log.info("delegate details list size: {}, delegates: {}" , delegates.size(), delegates);
      Set<String> healthyDelegates = delegates.stream().filter((delegate -> isDelegateHealthy(delegate, startTime)))
          .map((Delegate::getUuid)).collect(Collectors.toSet());
      log.info("healthy delegates list size: {}, healthy delegates: {}" , healthyDelegates.size(), healthyDelegates);
      Map<String, Long> lastReceivedTimeForClusters =
          lastReceivedPublishedMessageDao.getLastReceivedTimeForClusters(accountId, clusterIdsBatch);
      for (String clusterId: clusterIdsBatch) {
        if (healthyDelegates.contains(clusterIdToDelegateIdMap.get(clusterId)) &&
            (!lastReceivedTimeForClusters.containsKey(clusterId) ||
            Instant.ofEpochMilli(lastReceivedTimeForClusters.get(clusterId)).isBefore(allowedTime))) {
          log.info("Delegate health check failed for clusterId: {}, delegateId: {}", clusterId,
              clusterIdToDelegateIdMap.get(clusterId));
        }
      }
    }
    return null;
  }

  private boolean isDelegateHealthy(Delegate delegate, Instant startTime) {
    return delegate.getStatus().equals(DelegateInstanceStatus.ENABLED) &&
        Instant.ofEpochMilli(delegate.getLastHeartBeat()).isAfter(
            startTime.minus(Duration.ofMinutes(MINUTES_FOR_HEALTHY_DELEGATE_HEARTBEAT)));
  }
}
