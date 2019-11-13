package io.harness.batch.processing.processor;

import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.batch.processing.ccm.InstanceType;
import io.harness.batch.processing.entities.InstanceData;
import io.harness.batch.processing.pricing.data.CloudProvider;
import io.harness.batch.processing.processor.util.K8sResourceUtils;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.writer.constants.InstanceMetaDataConstants;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.event.grpc.PublishedMessage;
import io.harness.perpetualtask.k8s.watch.PodInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.infrastructure.instance.key.deployment.K8sDeploymentKey;
import software.wings.beans.instance.HarnessServiceInfo;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class K8sPodInfoProcessor implements ItemProcessor<PublishedMessage, InstanceInfo> {
  @Autowired private InstanceDataService instanceDataService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;

  @Override
  public InstanceInfo process(PublishedMessage publishedMessage) {
    String accountId = publishedMessage.getAccountId();
    PodInfo podInfo = (PodInfo) publishedMessage.getMessage();

    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.GCP.name());
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, podInfo.getNodeName());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());

    InstanceData instanceData = instanceDataService.fetchInstanceDataWithName(
        accountId, podInfo.getNodeName(), publishedMessage.getOccurredAt());
    if (null != instanceData) {
      Map<String, String> nodeMetaData = instanceData.getMetaData();
      metaData.put(InstanceMetaDataConstants.REGION, nodeMetaData.get(InstanceMetaDataConstants.REGION));
      metaData.put(
          InstanceMetaDataConstants.INSTANCE_FAMILY, nodeMetaData.get(InstanceMetaDataConstants.INSTANCE_FAMILY));
      metaData.put(
          InstanceMetaDataConstants.OPERATING_SYSTEM, nodeMetaData.get(InstanceMetaDataConstants.OPERATING_SYSTEM));
      metaData.put(InstanceMetaDataConstants.POD_NAME, podInfo.getPodName());
      metaData.put(
          InstanceMetaDataConstants.PARENT_RESOURCE_CPU, String.valueOf(instanceData.getTotalResource().getCpuUnits()));
      metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY,
          String.valueOf(instanceData.getTotalResource().getMemoryMb()));
    }

    Map<String, String> labelsMap = podInfo.getLabelsMap();
    HarnessServiceInfo harnessServiceInfo = getHarnessServiceInfo(accountId, labelsMap);

    return InstanceInfo.builder()
        .accountId(accountId)
        .cloudProviderId(podInfo.getCloudProviderId())
        .instanceId(podInfo.getPodUid())
        .instanceName(podInfo.getPodName())
        .instanceType(InstanceType.K8S_POD)
        .resource(K8sResourceUtils.getResource(podInfo.getTotalResource()))
        .metaData(metaData)
        //.containerList(podInfo.getContainersList())
        .labels(labelsMap)
        .harnessServiceInfo(harnessServiceInfo)
        // TODO: add missing fields in PodInfo
        .build();
  }

  HarnessServiceInfo getHarnessServiceInfo(String accountId, Map<String, String> labelsMap) {
    if (labelsMap.containsKey(K8sCCMConstants.RELEASE_NAME)) {
      String releaseName = labelsMap.get(K8sCCMConstants.RELEASE_NAME);
      K8sDeploymentKey k8sDeploymentKey = K8sDeploymentKey.builder().releaseName(releaseName).build();
      K8sDeploymentInfo k8sDeploymentInfo = K8sDeploymentInfo.builder().releaseName(releaseName).build();
      DeploymentSummary deploymentSummary = DeploymentSummary.builder()
                                                .accountId(accountId)
                                                .k8sDeploymentKey(k8sDeploymentKey)
                                                .deploymentInfo(k8sDeploymentInfo)
                                                .build();
      Optional<HarnessServiceInfo> harnessServiceInfo =
          cloudToHarnessMappingService.getHarnessServiceInfo(deploymentSummary);
      return harnessServiceInfo.orElse(null);
    }
    return null;
  }
}
