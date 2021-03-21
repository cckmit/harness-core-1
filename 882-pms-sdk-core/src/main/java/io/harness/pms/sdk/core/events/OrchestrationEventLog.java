package io.harness.pms.sdk.core.events;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "OrchestrationEventLogKeys")
@Entity(value = "orchestrationEventLog")
@Document("orchestrationEventLog")
@HarnessEntity(exportable = false)
@TypeAlias("OrchestrationEventLog")
public class OrchestrationEventLog implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_createdAt")
                 .unique(false)
                 .field(OrchestrationEventLogKeys.planExecutionId)
                 .field(OrchestrationEventLogKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("createdAt")
                 .unique(false)
                 .field(OrchestrationEventLogKeys.createdAt)
                 .build())
        .build();
  }

  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
  String planExecutionId;
  OrchestrationEvent event;
  @FdTtlIndex Date validUntil;
  long createdAt;
}
