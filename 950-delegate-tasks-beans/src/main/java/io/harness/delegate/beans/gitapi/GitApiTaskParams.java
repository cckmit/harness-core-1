package io.harness.delegate.beans.gitapi;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class GitApiTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  String prNumber;
  String repo;
  String owner;
  String slug;
  String key; // TODO it will come via github app connector with encrypted details
  String installId; // TODO it will come via github app connector with encrypted details
  String appId; //  TODO it will come via github app connector
  // private String token; //  TODO it will come via bitbucket/gitlab  connector details
  String userName; //  TODO it will come via bitbucket/gitlab connector details
  ConnectorDetails connectorDetails; // Use connectorDetails to retrieve all information
  GitRepoType gitRepoType;
  @NotEmpty GitApiRequestType requestType;

  public GitApiTaskParams(String prNumber, String repo, String owner, String slug, String key, String installId,
      String appId, String userName, ConnectorDetails connectorDetails, GitRepoType gitRepoType,
      GitApiRequestType requestType) {
    this.prNumber = prNumber;
    this.repo = repo;
    this.owner = owner;
    this.slug = slug;
    this.key = key;
    this.installId = installId;
    this.appId = appId;
    this.userName = userName;
    this.connectorDetails = connectorDetails;
    this.gitRepoType = gitRepoType;
    this.requestType = requestType;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.emptyList();
  }
}
