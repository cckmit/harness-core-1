package software.wings.resources;

import static software.wings.security.PermissionAttribute.Action.EXECUTE;
import static software.wings.security.PermissionAttribute.PermissionType.DEPLOYMENT;

import com.google.inject.Inject;

import io.harness.annotations.Redesign;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.presentation.Graph;
import io.harness.redesign.services.CustomExecutionService;
import io.harness.rest.RestResponse;
import software.wings.security.annotations.AuthRule;

import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Redesign
@Path("/execute2")
@Produces("application/json")
public class CustomExecutionResource {
  @Inject CustomExecutionService customExecutionService;

  @GET
  @Path("/http-switch")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeHttpSwitch() {
    return new RestResponse<>(customExecutionService.executeHttpSwitch());
  }

  @GET
  @Path("/http-fork")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeHttpFork() {
    return new RestResponse<>(customExecutionService.executeHttpFork());
  }

  @GET
  @Path("/http-section")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeSectionPlan() {
    return new RestResponse<>(customExecutionService.executeSectionPlan());
  }

  @GET
  @Path("/http-retry-ignore")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeRetryIgnorePlan() {
    return new RestResponse<>(customExecutionService.executeRetryIgnorePlan());
  }

  @GET
  @Path("/http-retry-abort")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeRetryAbortPlan() {
    return new RestResponse<>(customExecutionService.executeRetryAbortPlan());
  }

  @GET
  @Path("/http-rollback")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeRollbackPlan() {
    return new RestResponse<>(customExecutionService.executeRollbackPlan());
  }

  @GET
  @Path("/simple-shell-script")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeSimpleShellScriptPlan(
      @QueryParam("accountId") String accountId, @QueryParam("appId") String appId) {
    return new RestResponse<>(customExecutionService.executeSimpleShellScriptPlan(accountId, appId));
  }

  @GET
  @Path("/task-chain")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeTaskChainPlan() {
    return new RestResponse<>(customExecutionService.executeTaskChainPlan());
  }

  @GET
  @Path("/section-chain")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> executeSectionChainPlan() {
    return new RestResponse<>(customExecutionService.executeSectionChainPlan());
  }

  @GET
  @Path("/abort-plan")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<Interrupt> abortPlan(
      @QueryParam("accountId") String accountId, @QueryParam("planExecutionId") String planExecutionId) {
    return new RestResponse<>(customExecutionService.registerInterrupt(planExecutionId));
  }

  @GET
  @Path("/test-infra-state")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> testInfraState() throws IOException {
    return new RestResponse<>(customExecutionService.testInfraState());
  }

  @GET
  @Path("/test-graph-plan")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> testGraphPlan() {
    return new RestResponse<>(customExecutionService.testGraphPlan());
  }

  @GET
  @Path("/get-graph")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<Graph> getGraph(@QueryParam("planExecutionId") String planExecutionId) {
    return new RestResponse<>(customExecutionService.getGraph(planExecutionId));
  }

  @POST
  @Path("/test-execution-plan")
  @Produces("application/json")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> testExecutionPlan(
      @QueryParam("accountId") String accountId, @QueryParam("appId") String appId, String pipelineYaml) {
    return new RestResponse<>(customExecutionService.testExecutionPlanCreator(pipelineYaml, accountId, appId));
  }

  @GET
  @Path("/test-artifact-state")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> testArtifactStep() {
    return new RestResponse<>(customExecutionService.testArtifactState());
  }

  @GET
  @Path("/test-service-state")
  @AuthRule(permissionType = DEPLOYMENT, action = EXECUTE, skipAuth = true)
  public RestResponse<PlanExecution> testServiceStep() {
    return new RestResponse<>(customExecutionService.testServiceState());
  }
}
