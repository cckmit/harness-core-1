package io.harness.entities;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.environment.beans.EnvironmentType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "InstanceKeys")
@Entity(value = "instanceNG", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("instanceNG")
@StoreIn(DbAliases.NG_MANAGER)
@OwnedBy(HarnessTeam.DX)
public class Instance {
  public static List<MongoIndex> mongoIndexes() {
    // TODO add more indexes
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_idx")
                 .field(InstanceKeys.accountIdentifier)
                 .field(InstanceKeys.orgIdentifier)
                 .field(InstanceKeys.projectIdentifier)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  @FdUniqueIndex private String instanceKey;
  @NotEmpty private InstanceType instanceType;

  private String envId;
  private String envName;
  private EnvironmentType envType;

  private String serviceId;
  private String serviceName;

  private String infrastructureMappingId;
  private String infraMappingType;
  private String connectorRef;

  private ArtifactDetails primaryArtifact;

  private String lastDeployedById;
  private String lastDeployedByName;
  private long lastDeployedAt;

  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;

  private InstanceInfo instanceInfo;

  private boolean isDeleted;
  private long deletedAt;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
}
