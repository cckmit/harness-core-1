package software.wings.delegatetasks.buildsource;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;

import static java.util.Collections.emptyList;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.settings.SettingValue;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
public class BuildSourceParameters implements TaskParameters, ExecutionCapabilityDemander {
  public enum BuildSourceRequestType { GET_BUILDS, GET_LAST_SUCCESSFUL_BUILD }

  @NotNull private BuildSourceRequestType buildSourceRequestType;
  @NotEmpty private String accountId;
  @NotEmpty private String appId;
  @NotNull private SettingValue settingValue;
  @NotNull private ArtifactStreamAttributes artifactStreamAttributes;
  @NotNull private List<EncryptedDataDetail> encryptedDataDetails;
  @NotEmpty private String artifactStreamType;
  private String artifactStreamId;
  private int limit;
  private boolean shouldFetchSecretFromCache;

  // These fields are used only during artifact collection and cleanup.
  private boolean isCollection;
  // Unique key representing build numbers already present in the DB. It stores different things for different artifact
  // stream types like buildNo, revision or artifactPath.
  private Set<String> savedBuildDetailsKeys;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (settingValue == null) {
      return emptyList();
    }

    switch (settingValue.getSettingType()) {
      case JENKINS:
      case BAMBOO:
      case DOCKER:
      case NEXUS:
      case ARTIFACTORY:
        return settingValue.fetchRequiredExecutionCapabilities(maskingEvaluator);
      default:
        return getExecutionCapabilitiesFromArtifactStreamType(maskingEvaluator);
    }
  }

  private List<ExecutionCapability> getExecutionCapabilitiesFromArtifactStreamType(
      ExpressionEvaluator maskingEvaluator) {
    if (artifactStreamType.equals(GCR.name())) {
      String gcrHostName = artifactStreamAttributes.getRegistryHostName();
      return Collections.singletonList(
          HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              getUrl(gcrHostName), maskingEvaluator));
    } else if (artifactStreamType.equals(AZURE_ARTIFACTS.name())) {
      return settingValue.fetchRequiredExecutionCapabilities(maskingEvaluator);
    } else if (artifactStreamType.equals(ACR.name())) {
      final String default_server = "azure.microsoft.com";
      String loginServer = isNotEmpty(artifactStreamAttributes.getRegistryHostName())
          ? artifactStreamAttributes.getRegistryHostName()
          : default_server;
      return Collections.singletonList(
          HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              getUrl(loginServer), maskingEvaluator));
    }
    return emptyList();
  }

  private String getUrl(String hostName) {
    return "https://" + hostName + (hostName.endsWith("/") ? "" : "/");
  }
}
