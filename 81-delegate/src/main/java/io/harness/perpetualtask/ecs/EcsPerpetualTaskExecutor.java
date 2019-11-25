package io.harness.perpetualtask.ecs;

import static io.harness.event.payloads.Lifecycle.EventType.EVENT_TYPE_START;
import static io.harness.event.payloads.Lifecycle.EventType.EVENT_TYPE_STOP;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.Attribute;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.Resource;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Task;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.harness.event.client.EventPublisher;
import io.harness.event.payloads.Ec2InstanceInfo;
import io.harness.event.payloads.Ec2Lifecycle;
import io.harness.event.payloads.EcsContainerInstanceDescription;
import io.harness.event.payloads.EcsContainerInstanceInfo;
import io.harness.event.payloads.EcsContainerInstanceLifecycle;
import io.harness.event.payloads.EcsSyncEvent;
import io.harness.event.payloads.EcsTaskDescription;
import io.harness.event.payloads.EcsTaskInfo;
import io.harness.event.payloads.EcsTaskLifecycle;
import io.harness.event.payloads.InstanceState;
import io.harness.event.payloads.Lifecycle;
import io.harness.event.payloads.Lifecycle.EventType;
import io.harness.event.payloads.ReservedResource;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskParams;
import io.harness.perpetualtask.ecs.support.EcsMetricClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import software.wings.beans.AwsConfig;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class EcsPerpetualTaskExecutor implements PerpetualTaskExecutor {
  private static final long METRIC_AGGREGATION_WINDOW_MINUTES = 10;
  private static final String INSTANCE_TERMINATED_NAME = "terminated";
  private static final String ECS_OS_TYPE = "ecs.os-type";

  private final AwsEcsHelperServiceDelegate ecsHelperServiceDelegate;
  private final AwsEc2HelperServiceDelegate ec2ServiceDelegate;
  private final EcsMetricClient ecsMetricClient;
  private final EventPublisher eventPublisher;

  private Cache<String, EcsActiveInstancesCache> cache = Caffeine.newBuilder().build();

  private Instant lastMetricCollectionTime = Instant.ofEpochSecond(0);

  @Inject
  public EcsPerpetualTaskExecutor(AwsEcsHelperServiceDelegate ecsHelperServiceDelegate,
      AwsEc2HelperServiceDelegate ec2ServiceDelegate, EcsMetricClient ecsMetricClient, EventPublisher eventPublisher) {
    this.ecsHelperServiceDelegate = ecsHelperServiceDelegate;
    this.ec2ServiceDelegate = ec2ServiceDelegate;
    this.ecsMetricClient = ecsMetricClient;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public boolean runOnce(PerpetualTaskId taskId, PerpetualTaskParams params, Instant heartbeatTime) {
    try {
      EcsPerpetualTaskParams ecsPerpetualTaskParams = getTaskParams(params);
      String clusterName = ecsPerpetualTaskParams.getClusterName();
      String region = ecsPerpetualTaskParams.getRegion();
      String clusterId = ecsPerpetualTaskParams.getClusterId();
      String settingId = ecsPerpetualTaskParams.getSettingId();
      logger.info("Task params cluster name {} region {} ", clusterName, region);
      AwsConfig awsConfig = (AwsConfig) KryoUtils.asObject(ecsPerpetualTaskParams.getAwsConfig().toByteArray());
      List<EncryptedDataDetail> encryptionDetails =
          (List<EncryptedDataDetail>) KryoUtils.asObject(ecsPerpetualTaskParams.getEncryptionDetail().toByteArray());
      Instant startTime = Instant.now();

      Instant lastProcessedTime = fetchLastProcessedTimestamp(clusterName, heartbeatTime);
      List<ContainerInstance> containerInstances =
          listContainerInstances(clusterName, region, awsConfig, encryptionDetails);
      Set<String> instanceIds = fetchEc2InstanceIds(clusterName, containerInstances);
      List<Instance> instances = listEc2Instances(region, awsConfig, encryptionDetails, instanceIds);
      Map<String, String> taskArnServiceNameMap = new HashMap<>();
      loadTaskArnServiceNameMap(clusterName, region, awsConfig, encryptionDetails, taskArnServiceNameMap);
      List<Task> tasks = listTask(clusterName, region, awsConfig, encryptionDetails);

      Set<String> currentActiveEc2InstanceIds = new HashSet<>();
      publishEc2InstanceEvent(clusterName, region, currentActiveEc2InstanceIds, instances);
      Set<String> currentActiveContainerInstanceArns = getCurrentActiveContainerInstanceArns(containerInstances);
      publishContainerInstanceEvent(clusterId, settingId, clusterName, region, currentActiveContainerInstanceArns,
          lastProcessedTime, containerInstances);
      Set<String> currentActiveTaskArns = new HashSet<>();
      publishTaskEvent(ecsPerpetualTaskParams, currentActiveTaskArns, lastProcessedTime, tasks, taskArnServiceNameMap);

      updateActiveInstanceCache(clusterName, currentActiveEc2InstanceIds, currentActiveContainerInstanceArns,
          currentActiveTaskArns, startTime);
      publishEcsClusterSyncEvent(clusterId, settingId, clusterName, currentActiveEc2InstanceIds,
          currentActiveContainerInstanceArns, currentActiveTaskArns, startTime);
      if (startTime.isAfter(lastMetricCollectionTime.plus(METRIC_AGGREGATION_WINDOW_MINUTES, ChronoUnit.MINUTES))) {
        publishUtilizationMetrics(clusterId, settingId, region, awsConfig, encryptionDetails, clusterName, startTime);
        lastMetricCollectionTime = startTime;
      }
    } catch (Exception ex) {
      throw new InvalidRequestException("Exception while executing task: ", ex);
    }
    return true;
  }

  private EcsPerpetualTaskParams getTaskParams(PerpetualTaskParams params) {
    return AnyUtils.unpack(params.getCustomizedParams(), EcsPerpetualTaskParams.class);
  }

  @VisibleForTesting
  void publishUtilizationMetrics(String clusterId, String settingId, String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String clusterNameOrArn, Instant pollTime) {
    Date startTime = Date.from(pollTime.minus(METRIC_AGGREGATION_WINDOW_MINUTES, ChronoUnit.MINUTES));
    Date endTime = Date.from(pollTime);
    String clusterName = StringUtils.substringAfterLast(clusterNameOrArn, "/");
    List<Service> services = listServices(clusterNameOrArn, region, awsConfig, encryptionDetails);
    Cluster cluster = new Cluster().withClusterName(clusterName).withClusterArn(clusterNameOrArn);
    ecsMetricClient
        .getUtilizationMetrics(awsConfig, encryptionDetails, startTime, endTime, cluster, services,
            EcsPerpetualTaskParams.newBuilder()
                .setClusterId(clusterId)
                .setSettingId(settingId)
                .setRegion(region)
                .build())
        .forEach(msg -> eventPublisher.publishMessage(msg, HTimestamps.fromInstant(pollTime)));
  }

  private void publishEcsClusterSyncEvent(String clusterId, String settingId, String clusterName,
      Set<String> activeEc2InstanceIds, Set<String> activeContainerInstanceArns, Set<String> activeTaskArns,
      Instant startTime) {
    EcsSyncEvent ecsSyncEvent = EcsSyncEvent.newBuilder()
                                    .setClusterArn(clusterName)
                                    .setSettingId(settingId)
                                    .setClusterId(clusterId)
                                    .addAllActiveEc2InstanceArns(activeEc2InstanceIds)
                                    .addAllActiveContainerInstanceArns(activeContainerInstanceArns)
                                    .addAllActiveTaskArns(activeTaskArns)
                                    .setLastProcessedTimestamp(HTimestamps.fromInstant(startTime))
                                    .build();

    logger.info("Esc sync published Message {} ", ecsSyncEvent.toString());
    eventPublisher.publishMessage(ecsSyncEvent, ecsSyncEvent.getLastProcessedTimestamp());
  }

  private void publishTaskEvent(EcsPerpetualTaskParams ecsPerpetualTaskParams, Set<String> currentActiveTaskArns,
      Instant lastProcessedTime, List<Task> tasks, Map<String, String> taskArnServiceNameMap) {
    Set<String> activeTaskArns = fetchActiveTaskArns(ecsPerpetualTaskParams.getClusterName());
    logger.info("Active tasks {} task size {} ", activeTaskArns, tasks.size());
    publishMissingTaskLifecycleEvent(activeTaskArns, tasks);

    for (Task task : tasks) {
      if (null == task.getStoppedAt()) {
        currentActiveTaskArns.add(task.getTaskArn());
      }

      if (!activeTaskArns.contains(task.getTaskArn())
          && convertDateToInstant(task.getPullStartedAt()).isAfter(lastProcessedTime)) {
        int memory = Integer.parseInt(task.getMemory());
        int cpu = Integer.parseInt(task.getCpu());
        if (null != task.getPullStartedAt()) {
          publishTaskLifecycleEvent(task.getTaskArn(), task.getPullStartedAt(), EVENT_TYPE_START);
        }

        EcsTaskDescription.Builder ecsTaskDescriptionBuilder = EcsTaskDescription.newBuilder()
                                                                   .setTaskArn(task.getTaskArn())
                                                                   .setClusterId(ecsPerpetualTaskParams.getClusterId())
                                                                   .setSettingId(ecsPerpetualTaskParams.getSettingId())
                                                                   .setLaunchType(task.getLaunchType())
                                                                   .setClusterArn(task.getClusterArn())
                                                                   .setDesiredStatus(task.getDesiredStatus())
                                                                   .setRegion(ecsPerpetualTaskParams.getRegion());

        if (null != taskArnServiceNameMap.get(task.getTaskArn())) {
          ecsTaskDescriptionBuilder.setServiceName(taskArnServiceNameMap.get(task.getTaskArn()));
        }
        if (null != task.getContainerInstanceArn()) {
          ecsTaskDescriptionBuilder.setContainerInstanceArn(task.getContainerInstanceArn());
        }

        EcsTaskInfo ecsTaskInfo =
            EcsTaskInfo.newBuilder()
                .setEcsTaskDescription(ecsTaskDescriptionBuilder.build())
                .setEcsTaskResource(ReservedResource.newBuilder().setCpu(cpu).setMemory(memory).build())
                .build();
        logger.debug("Task published Message {} ", ecsTaskInfo.toString());
        eventPublisher.publishMessage(ecsTaskInfo, HTimestamps.fromDate(task.getStartedAt()));
      }

      if (null != task.getStoppedAt() && taskStoppedEventRequired(lastProcessedTime, task, activeTaskArns)) {
        publishTaskLifecycleEvent(task.getTaskArn(), task.getStoppedAt(), EVENT_TYPE_STOP);
      }
    }
  }

  private boolean taskStoppedEventRequired(Instant lastProcessedTime, Task task, Set<String> activeTaskArns) {
    boolean eventRequired = true;
    if (!activeTaskArns.contains(task.getTaskArn())
        && convertDateToInstant(task.getStoppedAt()).isBefore(lastProcessedTime)) {
      eventRequired = false;
    }
    return eventRequired;
  }

  private Instant convertDateToInstant(Date date) {
    return Instant.ofEpochMilli(date.getTime());
  }

  private Lifecycle createLifecycle(String instanceId, Date date, EventType eventType) {
    return Lifecycle.newBuilder()
        .setInstanceId(instanceId)
        .setTimestamp(HTimestamps.fromDate(date))
        .setType(eventType)
        .setCreatedTimestamp(HTimestamps.fromInstant(Instant.now()))
        .build();
  }

  private void publishEc2LifecycleEvent(String instanceId, Date date, EventType eventType) {
    Ec2Lifecycle ec2Lifecycle =
        Ec2Lifecycle.newBuilder().setLifecycle(createLifecycle(instanceId, date, eventType)).build();
    eventPublisher.publishMessage(ec2Lifecycle, ec2Lifecycle.getLifecycle().getTimestamp());
  }

  private void publishContainerInstanceLifecycleEvent(String instanceId, Date date, EventType eventType) {
    EcsContainerInstanceLifecycle ecsContainerInstanceLifecycle =
        EcsContainerInstanceLifecycle.newBuilder().setLifecycle(createLifecycle(instanceId, date, eventType)).build();
    eventPublisher.publishMessage(
        ecsContainerInstanceLifecycle, ecsContainerInstanceLifecycle.getLifecycle().getTimestamp());
  }

  private void publishTaskLifecycleEvent(String instanceId, Date date, EventType eventType) {
    EcsTaskLifecycle ecsTaskLifecycle =
        EcsTaskLifecycle.newBuilder().setLifecycle(createLifecycle(instanceId, date, eventType)).build();
    logger.debug("Task Lifecycle event {} ", ecsTaskLifecycle.toString());
    eventPublisher.publishMessage(ecsTaskLifecycle, ecsTaskLifecycle.getLifecycle().getTimestamp());
  }

  private Set<String> getCurrentActiveContainerInstanceArns(List<ContainerInstance> containerInstances) {
    return containerInstances.stream().map(ContainerInstance::getContainerInstanceArn).collect(Collectors.toSet());
  }

  private void publishContainerInstanceEvent(String clusterId, String settingId, String clusterName, String region,
      Set<String> currentActiveArns, Instant lastProcessedTime, List<ContainerInstance> containerInstances) {
    logger.info("Container instance size is {} ", containerInstances.size());
    Set<String> activeContainerInstancesArns = fetchActiveContainerInstancesArns(clusterName);
    logger.info("Container instances in cache {} ", activeContainerInstancesArns);

    publishStoppedContainerInstanceEvents(activeContainerInstancesArns, currentActiveArns);
    for (ContainerInstance containerInstance : containerInstances) {
      if (!activeContainerInstancesArns.contains(containerInstance.getContainerInstanceArn())
          && convertDateToInstant(containerInstance.getRegisteredAt()).isAfter(lastProcessedTime)) {
        publishContainerInstanceLifecycleEvent(
            containerInstance.getContainerInstanceArn(), containerInstance.getRegisteredAt(), EVENT_TYPE_START);

        List<Resource> registeredResources = containerInstance.getRegisteredResources();
        Map<String, Resource> resourceMap =
            registeredResources.stream().collect(Collectors.toMap(Resource::getName, resource -> resource));

        int memory = resourceMap.get("MEMORY").getIntegerValue();
        int cpu = resourceMap.get("CPU").getIntegerValue();

        Map<String, Attribute> attributeMap = containerInstance.getAttributes().stream().collect(
            Collectors.toMap(Attribute::getName, attribute -> attribute));
        Attribute attribute = attributeMap.get(ECS_OS_TYPE);

        EcsContainerInstanceInfo ecsContainerInstanceInfo =
            EcsContainerInstanceInfo.newBuilder()
                .setEcsContainerInstanceDescription(
                    EcsContainerInstanceDescription.newBuilder()
                        .setClusterArn(clusterName)
                        .setClusterId(clusterId)
                        .setSettingId(settingId)
                        .setContainerInstanceArn(containerInstance.getContainerInstanceArn())
                        .setEc2InstanceId(containerInstance.getEc2InstanceId())
                        .setOperatingSystem(attribute.getValue())
                        .setRegion(region)
                        .build())
                .setEcsContainerInstanceResource(ReservedResource.newBuilder().setCpu(cpu).setMemory(memory).build())
                .build();

        logger.info("Container published Message {} ", ecsContainerInstanceInfo.toString());
        eventPublisher.publishMessage(
            ecsContainerInstanceInfo, HTimestamps.fromDate(containerInstance.getRegisteredAt()));
      }
    }
  }

  /**
   * once container instance is stopped its data is not available so
   * arns which are in cache and not in response are stopped
   */
  private void publishStoppedContainerInstanceEvents(
      Set<String> activeContainerInstancesArns, Set<String> currentActiveArns) {
    SetView<String> stoppedContainerInstanceArns = Sets.difference(activeContainerInstancesArns, currentActiveArns);
    for (String stoppedContainerInstanceArn : stoppedContainerInstanceArns) {
      publishContainerInstanceLifecycleEvent(stoppedContainerInstanceArn, Date.from(Instant.now()), EVENT_TYPE_STOP);
    }
  }

  private void publishEc2InstanceEvent(
      String clusterName, String region, Set<String> currentActiveEc2InstanceIds, List<Instance> instances) {
    logger.info("Instance list size is {} ", instances.size());
    Set<String> activeEc2InstanceIds = fetchActiveEc2InstanceIds(clusterName);
    logger.info("Active instance in cache {}", activeEc2InstanceIds);
    publishMissingInstancesLifecycleEvent(activeEc2InstanceIds, instances);

    for (Instance instance : instances) {
      if (INSTANCE_TERMINATED_NAME.equals(instance.getState().getName())) {
        publishEc2LifecycleEvent(instance.getInstanceId(), Date.from(Instant.now()), EVENT_TYPE_STOP);
      } else {
        currentActiveEc2InstanceIds.add(instance.getInstanceId());
      }

      if (!activeEc2InstanceIds.contains(instance.getInstanceId())) {
        publishEc2LifecycleEvent(instance.getInstanceId(), instance.getLaunchTime(), EVENT_TYPE_START);

        InstanceState.Builder instanceStateBuilder =
            InstanceState.newBuilder().setCode(instance.getState().getCode()).setName(instance.getState().getName());

        Ec2InstanceInfo.Builder ec2InstanceInfoBuilder = Ec2InstanceInfo.newBuilder()
                                                             .setInstanceId(instance.getInstanceId())
                                                             .setClusterArn(clusterName)
                                                             .setInstanceType(instance.getInstanceType())
                                                             .setInstanceState(instanceStateBuilder.build())
                                                             .setRegion(region);

        if (null != instance.getCapacityReservationId()) {
          ec2InstanceInfoBuilder.setCapacityReservationId(instance.getCapacityReservationId());
        }

        if (null != instance.getSpotInstanceRequestId()) {
          ec2InstanceInfoBuilder.setSpotInstanceRequestId(instance.getSpotInstanceRequestId());
        }

        if (null != instance.getInstanceLifecycle()) {
          ec2InstanceInfoBuilder.setInstanceLifecycle(instance.getInstanceLifecycle());
        }

        Ec2InstanceInfo ec2InstanceInfo = ec2InstanceInfoBuilder.build();
        logger.info("EC2 published Message {} ", ec2InstanceInfo.toString());
        eventPublisher.publishMessage(ec2InstanceInfo, HTimestamps.fromDate(instance.getLaunchTime()));
      }
    }
  }

  private void publishMissingTaskLifecycleEvent(Set<String> activeTaskArns, List<Task> tasks) {
    Set<String> currentlyActiveTaskArns = tasks.stream().map(Task::getTaskArn).collect(Collectors.toSet());
    SetView<String> missingTaskArns = Sets.difference(activeTaskArns, currentlyActiveTaskArns);
    for (String missingTask : missingTaskArns) {
      publishTaskLifecycleEvent(missingTask, Date.from(Instant.now()), EVENT_TYPE_STOP);
    }
  }

  /* Instances which were in activeInstances cache but were not present in api response.
   * Can happen if perpetual task didn't run within 1 hr of ec2 instance was termination.
   */
  private void publishMissingInstancesLifecycleEvent(Set<String> activeEc2InstanceIds, List<Instance> instances) {
    Set<String> ec2InstanceIds = instances.stream().map(Instance::getInstanceId).collect(Collectors.toSet());
    SetView<String> missingInstances = Sets.difference(activeEc2InstanceIds, ec2InstanceIds);
    logger.info("Missing instances {} ", missingInstances);
    for (String missingInstance : missingInstances) {
      publishEc2LifecycleEvent(missingInstance, Date.from(Instant.now()), EVENT_TYPE_STOP);
    }
  }

  private List<Task> listTask(
      String clusterName, String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    List<DesiredStatus> desiredStatuses = listTaskDesiredStatus();
    List<Task> tasks = new ArrayList<>();
    for (DesiredStatus desiredStatus : desiredStatuses) {
      tasks.addAll(ecsHelperServiceDelegate.listTasksForService(
          awsConfig, encryptionDetails, region, clusterName, null, desiredStatus));
    }
    return tasks;
  }

  private List<DesiredStatus> listTaskDesiredStatus() {
    return new ArrayList<>(Arrays.asList(DesiredStatus.RUNNING, DesiredStatus.STOPPED));
  }

  private List<ContainerInstanceStatus> listContainerInstanceStatus() {
    return new ArrayList<>(Arrays.asList(ContainerInstanceStatus.ACTIVE, ContainerInstanceStatus.DRAINING));
  }

  private void loadTaskArnServiceNameMap(String clusterName, String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, Map<String, String> taskArnServiceNameMap) {
    List<DesiredStatus> desiredStatuses = listTaskDesiredStatus();
    for (Service service : listServices(clusterName, region, awsConfig, encryptionDetails)) {
      for (DesiredStatus desiredStatus : desiredStatuses) {
        List<String> taskArns = ecsHelperServiceDelegate.listTasksArnForService(
            awsConfig, encryptionDetails, region, clusterName, service.getServiceArn(), desiredStatus);
        if (!CollectionUtils.isEmpty(taskArns)) {
          for (String taskArn : taskArns) {
            taskArnServiceNameMap.put(taskArn, service.getServiceArn());
          }
        }
      }
    }
  }

  private List<Service> listServices(
      String clusterName, String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    return ecsHelperServiceDelegate.listServicesForCluster(awsConfig, encryptionDetails, region, clusterName);
  }

  private List<ContainerInstance> listContainerInstances(
      String clusterName, String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    List<ContainerInstanceStatus> containerInstanceStatuses = listContainerInstanceStatus();
    List<ContainerInstance> containerInstances = new ArrayList<>();
    for (ContainerInstanceStatus containerInstanceStatus : containerInstanceStatuses) {
      containerInstances.addAll(ecsHelperServiceDelegate.listContainerInstancesForCluster(
          awsConfig, encryptionDetails, region, clusterName, containerInstanceStatus));
    }
    return containerInstances;
  }

  private Set<String> fetchEc2InstanceIds(String clusterName, List<ContainerInstance> containerInstances) {
    Set<String> instanceIds = new HashSet<>();
    if (!CollectionUtils.isEmpty(containerInstances)) {
      instanceIds = containerInstances.stream().map(ContainerInstance::getEc2InstanceId).collect(Collectors.toSet());
      instanceIds.addAll(fetchActiveEc2InstanceIds(clusterName));
    }
    return instanceIds;
  }

  private Set<String> fetchActiveEc2InstanceIds(String clusterName) {
    EcsActiveInstancesCache ecsActiveInstancesCache = cache.getIfPresent(clusterName);
    if (null != ecsActiveInstancesCache
        && !CollectionUtils.isEmpty(ecsActiveInstancesCache.getActiveEc2InstanceIds())) {
      return ecsActiveInstancesCache.getActiveEc2InstanceIds();
    } else {
      return Collections.emptySet();
    }
  }

  private Set<String> fetchActiveContainerInstancesArns(String clusterName) {
    EcsActiveInstancesCache ecsActiveInstancesCache = cache.getIfPresent(clusterName);
    if (null != ecsActiveInstancesCache
        && !CollectionUtils.isEmpty(ecsActiveInstancesCache.getActiveContainerInstanceArns())) {
      return ecsActiveInstancesCache.getActiveContainerInstanceArns();
    } else {
      return Collections.emptySet();
    }
  }

  private Instant fetchLastProcessedTimestamp(String clusterName, Instant heartbeatTime) {
    EcsActiveInstancesCache ecsActiveInstancesCache = cache.getIfPresent(clusterName);
    if (null != ecsActiveInstancesCache && null != ecsActiveInstancesCache.getLastProcessedTimestamp()) {
      return ecsActiveInstancesCache.getLastProcessedTimestamp();
    } else {
      return heartbeatTime;
    }
  }

  private Set<String> fetchActiveTaskArns(String clusterName) {
    EcsActiveInstancesCache ecsActiveInstancesCache = cache.getIfPresent(clusterName);
    if (null != ecsActiveInstancesCache && !CollectionUtils.isEmpty(ecsActiveInstancesCache.getActiveTaskArns())) {
      return ecsActiveInstancesCache.getActiveTaskArns();
    } else {
      return Collections.emptySet();
    }
  }

  private void updateActiveInstanceCache(String clusterName, Set<String> activeEc2InstanceIds,
      Set<String> activeContainerInstanceArns, Set<String> activeTaskArns, Instant startTime) {
    logger.info("Params to update cache {} ; {} ; {} ; {} ; {} ", clusterName, activeEc2InstanceIds,
        activeContainerInstanceArns, activeTaskArns, startTime);
    Optional.ofNullable(cache.get(clusterName, s -> createDefaultCache()))
        .ifPresent(activeInstancesCache
            -> updateCache(
                activeInstancesCache, activeEc2InstanceIds, activeContainerInstanceArns, activeTaskArns, startTime));
  }

  private void updateCache(EcsActiveInstancesCache activeInstancesCache, Set<String> activeEc2InstanceIds,
      Set<String> activeContainerInstanceArns, Set<String> activeTaskArns, Instant startTime) {
    activeInstancesCache.setActiveEc2InstanceIds(activeEc2InstanceIds);
    activeInstancesCache.setActiveContainerInstanceArns(activeContainerInstanceArns);
    activeInstancesCache.setActiveTaskArns(activeTaskArns);
    activeInstancesCache.setLastProcessedTimestamp(startTime);
  }

  private EcsActiveInstancesCache createDefaultCache() {
    return new EcsActiveInstancesCache(
        Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Instant.now());
  }

  private List<Instance> listEc2Instances(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, Set<String> instanceIds) {
    List<Instance> instances = new ArrayList<>();
    if (!CollectionUtils.isEmpty(instanceIds)) {
      instances =
          ec2ServiceDelegate.listEc2Instances(awsConfig, encryptionDetails, new ArrayList<>(instanceIds), region);
      instances = instances.stream().filter(instance -> null != instance.getLaunchTime()).collect(Collectors.toList());
      logger.debug("Instances {} ", instances.toString());
    }
    return instances;
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskParams params) {
    EcsPerpetualTaskParams taskParams = getTaskParams(params);
    cache.invalidate(taskParams.getClusterName());
    return true;
  }
}
