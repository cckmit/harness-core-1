package io.harness.waiter;

import static java.time.Duration.ofDays;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.persistence.CreatedAtAccess;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents response generated by a correlationId.
 */
@Value
@Builder
@FieldNameConstants(innerTypeName = "NotifyResponseKeys")
@Document("notifyResponses")
@TypeAlias("notifyResponses")
@Entity(value = "notifyResponses", noClassnameStored = true)
@HarnessEntity(exportable = false)
@OwnedBy(HarnessTeam.PIPELINE)
public class NotifyResponse implements WaitEngineEntity, CreatedAtAccess {
  public static final Duration TTL = ofDays(21);

  @Id @org.springframework.data.annotation.Id String uuid;
  @FdIndex long createdAt;
  byte[] responseData;
  boolean error;

  @Default @FdTtlIndex @NonFinal @Wither Date validUntil = Date.from(OffsetDateTime.now().plus(TTL).toInstant());
}
