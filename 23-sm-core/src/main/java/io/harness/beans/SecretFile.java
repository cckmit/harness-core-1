package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.annotations.dev.OwnedBy;
import io.harness.stream.BoundedInputStream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@OwnedBy(PL)
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "SecretTextKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecretFile extends HarnessSecret {
  // Base64 encoded file content
  BoundedInputStream boundedInputStream;
  long fileSize;
}