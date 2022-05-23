package io.harness.cdng.creator.plan.gitops;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static java.lang.String.format;
import static java.util.function.Function.identity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.gitops.steps.ClusterStepParameters;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.yaml.ParameterField;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.GITOPS)
@UtilityClass
public class ClusterPlanCreator {
  @NotNull
  public PlanNode getGitopsClustersStepPlanNode(final EnvironmentYamlV2 environmentYamlV2) {
    return PlanNode.builder()
        .uuid(UUIDGenerator.generateUuid())
        .name(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .identifier(PlanCreatorConstants.SPEC_IDENTIFIER)
        .stepType(GitopsClustersStep.STEP_TYPE)
        .stepParameters(getStepParams(environmentYamlV2))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build())
        .build();
  }

  @NotNull
  public static PlanNode getGitopsClustersStepPlanNode(final EnvironmentGroupYaml envGroupYaml) {
    return PlanNode.builder()
        .uuid(UUIDGenerator.generateUuid())
        .name(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .identifier(PlanCreatorConstants.SPEC_IDENTIFIER)
        .stepType(GitopsClustersStep.STEP_TYPE)
        .stepParameters(getStepParams(envGroupYaml))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build())
        .build();
  }

  private static ClusterStepParameters getStepParams(EnvironmentYamlV2 environmentYamlV2) {
    checkNotNull(environmentYamlV2, "environment must be present");

    final String envRef = fetchEnvRef(environmentYamlV2);

    if (environmentYamlV2.getDeployToAll() == Boolean.TRUE) {
      return ClusterStepParameters.WithEnv(envRef);
    }

    checkArgument(isNotEmpty(environmentYamlV2.getGitOpsClusters()),
        "list of gitops clusterRefs must be provided when not deploying to all clusters");
    return ClusterStepParameters.WithEnvAndClusterRefs(envRef, getClusterRefs(environmentYamlV2));
  }

  private static ClusterStepParameters getStepParams(EnvironmentGroupYaml envGroupYaml) {
    checkNotNull(envGroupYaml, "envGroupYaml must be present");

    final ParameterField<String> envGroupRef = envGroupYaml.getEnvGroupRef();
    checkNotNull(envGroupRef, "environment ref must be present");
    checkArgument(!envGroupRef.isExpression(), "environment ref not resolved yet");

    if (envGroupYaml.isDeployToAll()) {
      final String envGroup = (String) envGroupRef.fetchFinalValue();
      return ClusterStepParameters.WithEnvGroupRef(envGroup);
    }

    checkArgument(isNotEmpty(envGroupYaml.getEnvGroupConfig()),
        "list of environments must be provided when not deploying to all environments");

    final ClusterStepParameters parameters = ClusterStepParameters.builder().build();
    envGroupYaml.getEnvGroupConfig()
        .stream()
        .collect(Collectors.toMap(e -> (String) e.getEnvironmentRef().fetchFinalValue(), identity()))
        .values()
        .forEach(envYaml -> parameters.and(fetchEnvRef(envYaml), envYaml.getDeployToAll(), getClusterRefs(envYaml)));

    return parameters;
  }

  private static Set<String> getClusterRefs(EnvironmentYamlV2 environmentYamlV2) {
    final Set<String> clusterRefs = new HashSet<>();
    for (ClusterYaml cluster : environmentYamlV2.getGitOpsClusters()) {
      checkArgument(!cluster.getRef().isExpression(),
          format("cluster ref %s is an expression, this is not supported", cluster.getRef()));
      clusterRefs.add((String) cluster.getRef().fetchFinalValue());
    }
    return clusterRefs;
  }

  private static String fetchEnvRef(EnvironmentYamlV2 environmentYamlV2) {
    final ParameterField<String> environmentRef = environmentYamlV2.getEnvironmentRef();
    checkNotNull(environmentRef, "environment ref must be present");
    checkArgument(!environmentRef.isExpression(), "environment ref not resolved yet");

    return (String) environmentRef.fetchFinalValue();
  }
}
