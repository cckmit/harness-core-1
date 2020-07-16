package io.harness.batch.processing.cloudevents.aws.ecs.service.impl;

import io.harness.batch.processing.cloudevents.aws.ecs.service.CEClusterDao;
import io.harness.batch.processing.cloudevents.aws.ecs.service.intfc.AwsECSClusterService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsECSHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsHelperResourceService;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.NameValuePair;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.beans.ce.CECloudAccount;
import software.wings.beans.ce.CECluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AwsECSClusterServiceImpl implements AwsECSClusterService {
  private final AwsHelperResourceService awsHelperResourceService;
  private final AwsECSHelperService awsECSHelperService;
  private final CEClusterDao ceClusterDao;

  @Autowired
  public AwsECSClusterServiceImpl(AwsHelperResourceService awsHelperResourceService,
      AwsECSHelperService awsECSHelperService, CEClusterDao ceClusterDao) {
    this.awsHelperResourceService = awsHelperResourceService;
    this.awsECSHelperService = awsECSHelperService;
    this.ceClusterDao = ceClusterDao;
  }

  @Value
  private static class ClusterIdentifierKey {
    String accountId;
    String infraAccountId;
    String clusterName;
    String region;
  }

  @Override
  public List<CECluster> getECSCluster(String accountId, String settingId, CEAwsConfig ceAwsConfig) {
    AwsCrossAccountAttributes awsCrossAccountAttributes = ceAwsConfig.getAwsCrossAccountAttributes();
    List<NameValuePair> awsRegions = awsHelperResourceService.getAwsRegions();
    List<CECluster> clusters = new ArrayList<>();
    awsRegions.forEach(awsRegion -> {
      List<String> ecsClusters = awsECSHelperService.listECSClusters(awsRegion.getValue(), awsCrossAccountAttributes);
      logger.info("ECS clusters {}", ecsClusters);
      ecsClusters.forEach(ecsCluster -> {
        CECluster ceCluster = CECluster.builder()
                                  .accountId(accountId)
                                  .clusterName(getNameFromArn(ecsCluster))
                                  .clusterArn(ecsCluster)
                                  .region(awsRegion.getValue())
                                  .infraAccountId(ceAwsConfig.getAwsAccountId())
                                  .infraMasterAccountId(ceAwsConfig.getAwsMasterAccountId())
                                  .parentAccountSettingId(settingId)
                                  .build();
        clusters.add(ceCluster);
      });
    });
    return clusters;
  }

  protected void updateClusters(String accountId, String infraAccountId, List<CECluster> infraClusters) {
    Map<ClusterIdentifierKey, CECluster> infraClusterMap = createClusterMap(infraClusters);

    List<CECluster> ceExistingClusters = ceClusterDao.getByInfraAccountId(accountId, infraAccountId);
    Map<ClusterIdentifierKey, CECluster> ceExistingClusterMap = createClusterMap(ceExistingClusters);

    infraClusterMap.forEach((clusterIdentifierKey, ceCluster) -> {
      if (!ceExistingClusterMap.containsKey(clusterIdentifierKey)) {
        ceClusterDao.create(ceCluster);
      }
    });

    ceExistingClusterMap.forEach((clusterIdentifierKey, ceCluster) -> {
      if (!infraClusterMap.containsKey(clusterIdentifierKey)) {
        ceClusterDao.deleteCluster(ceCluster.getUuid());
      }
    });
  }

  protected String getNameFromArn(String arn) {
    return arn.substring(arn.lastIndexOf('/') + 1);
  }

  private Map<ClusterIdentifierKey, CECluster> createClusterMap(List<CECluster> ceClusters) {
    return ceClusters.stream().collect(Collectors.toMap(ceCluster
        -> new ClusterIdentifierKey(
            ceCluster.getAccountId(), ceCluster.getInfraAccountId(), ceCluster.getClusterName(), ceCluster.getRegion()),
        Function.identity()));
  }

  private void syncCEClusters(String accountId, String settingId, CEAwsConfig ceAwsConfig) {
    List<CECluster> ecsCluster = getECSCluster(accountId, settingId, ceAwsConfig);
    updateClusters(accountId, ceAwsConfig.getAwsAccountId(), ecsCluster);
  }

  @Override
  public void syncCEClusters(CECloudAccount ceCloudAccount) {
    String accountId = ceCloudAccount.getAccountId();
    String settingId = ceCloudAccount.getMasterAccountSettingId();
    CEAwsConfig ceAwsConfig = CEAwsConfig.builder()
                                  .awsCrossAccountAttributes(ceCloudAccount.getAwsCrossAccountAttributes())
                                  .awsAccountId(ceCloudAccount.getInfraAccountId())
                                  .awsMasterAccountId(ceCloudAccount.getInfraMasterAccountId())
                                  .build();
    syncCEClusters(accountId, settingId, ceAwsConfig);
  }
}
