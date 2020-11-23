package io.harness.ngtriggers.repository;

import io.harness.annotation.HarnessRepo;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;

import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface TriggerEventHistoryRepository
    extends PagingAndSortingRepository<TriggerEventHistory, String>, TriggerEventHistoryRepositoryCustom {}
