package io.harness.cvng.verificationjob.services.impl;

import static io.harness.cvng.verificationjob.beans.VerificationJobType.HEALTH;

import io.harness.cvng.verificationjob.beans.VerificationJobDTO;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter.RuntimeParameterKeys;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.cvng.verificationjob.services.api.VerificationJobService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class VerificationJobServiceImpl implements VerificationJobService {
  @Inject private HPersistence hPersistence;

  @Override
  @Nullable
  public VerificationJobDTO getVerificationJobDTO(String accountId, String identifier) {
    VerificationJob verificationJob = getVerificationJob(accountId, identifier);
    if (verificationJob == null) {
      return null;
    }
    return verificationJob.getVerificationJobDTO();
  }

  @Override
  public void upsert(String accountId, VerificationJobDTO verificationJobDTO) {
    VerificationJob verificationJob = verificationJobDTO.getVerificationJob();
    verificationJob.setAccountId(accountId);
    VerificationJob stored = getVerificationJob(accountId, verificationJobDTO.getIdentifier());
    if (stored != null) {
      verificationJob.setUuid(stored.getUuid());
    }
    verificationJob.validate();
    // TODO: Keeping it simple for now. find a better way to save if more fields are added to verification Job. This can
    // potentially override them.
    hPersistence.save(verificationJob);
  }

  @Override
  public void save(VerificationJob verificationJob) {
    hPersistence.save(verificationJob);
  }

  @Override
  public VerificationJob getVerificationJob(String accountId, String identifier) {
    Preconditions.checkNotNull(accountId);
    Preconditions.checkNotNull(identifier);
    return hPersistence.createQuery(VerificationJob.class)
        .filter(VerificationJobKeys.accountId, accountId)
        .filter(VerificationJobKeys.identifier, identifier)
        .get();
  }

  @Override
  public List<VerificationJob> getHealthVerificationJobs(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String serviceIdentifier) {
    Preconditions.checkNotNull(accountIdentifier);
    Preconditions.checkNotNull(orgIdentifier);
    Preconditions.checkNotNull(projectIdentifier);
    Preconditions.checkNotNull(envIdentifier);
    Preconditions.checkNotNull(serviceIdentifier);
    return hPersistence.createQuery(VerificationJob.class)
        .filter(VerificationJobKeys.accountId, accountIdentifier)
        .filter(VerificationJobKeys.orgIdentifier, orgIdentifier)
        .filter(VerificationJobKeys.projectIdentifier, projectIdentifier)
        .filter(VerificationJobKeys.envIdentifier + "." + RuntimeParameterKeys.value, envIdentifier)
        .filter(VerificationJobKeys.serviceIdentifier + "." + RuntimeParameterKeys.value, serviceIdentifier)
        .filter(VerificationJobKeys.type, HEALTH)
        .asList();
  }

  @Override
  public VerificationJob get(String uuid) {
    Preconditions.checkNotNull(uuid);
    return hPersistence.get(VerificationJob.class, uuid);
  }

  @Override
  public void delete(String accountId, String identifier) {
    hPersistence.delete(hPersistence.createQuery(VerificationJob.class)
                            .filter(VerificationJobKeys.accountId, accountId)
                            .filter(VerificationJobKeys.identifier, identifier));
  }

  @Override
  public List<VerificationJobDTO> list(String accountId, String projectIdentifier, String orgIdentifier) {
    return hPersistence.createQuery(VerificationJob.class)
        .filter(VerificationJobKeys.accountId, accountId)
        .filter(VerificationJobKeys.orgIdentifier, orgIdentifier)
        .filter(VerificationJobKeys.projectIdentifier, projectIdentifier)
        .asList()
        .stream()
        .map(verificationJob -> verificationJob.getVerificationJobDTO())
        .collect(Collectors.toList());
  }

  @Override
  public boolean doesAVerificationJobExistsForThisProject(
      String accountId, String orgIdentifier, String projectIdentifier) {
    long numberOfVerificationJobs = hPersistence.createQuery(VerificationJob.class)
                                        .filter(VerificationJobKeys.accountId, accountId)
                                        .filter(VerificationJobKeys.orgIdentifier, orgIdentifier)
                                        .filter(VerificationJobKeys.projectIdentifier, projectIdentifier)
                                        .count();
    return numberOfVerificationJobs > 0;
  }

  @Override
  public int getNumberOfServicesUndergoingHealthVerification(
      String accountId, String orgIdentifier, String projectIdentifier) {
    BasicDBObject verificationJobQuery = new BasicDBObject();
    List<BasicDBObject> conditions = new ArrayList<>();
    conditions.add(new BasicDBObject(VerificationJobKeys.accountId, accountId));
    conditions.add(new BasicDBObject(VerificationJobKeys.projectIdentifier, projectIdentifier));
    conditions.add(new BasicDBObject(VerificationJobKeys.orgIdentifier, orgIdentifier));
    conditions.add(new BasicDBObject(VerificationJobKeys.type, HEALTH.toString()));
    verificationJobQuery.put("$and", conditions);
    List<String> serviceIdentifiers = hPersistence.getCollection(VerificationJob.class)
                                          .distinct(VerificationJobKeys.serviceIdentifier, verificationJobQuery);
    return serviceIdentifiers.size();
  }
}
