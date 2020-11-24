package io.harness.ngtriggers.helpers;

import static io.harness.ngtriggers.beans.webhookresponse.WebhookEventResponse.FinalStatus.INVALID_RUNTIME_INPUT_YAML;
import static io.harness.ngtriggers.beans.webhookresponse.WebhookEventResponse.FinalStatus.NO_MATCHING_TRIGGER;
import static io.harness.ngtriggers.beans.webhookresponse.WebhookEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_REPO;
import static io.harness.ngtriggers.beans.webhookresponse.WebhookEventResponse.FinalStatus.TARGET_DID_NOT_EXECUTE;
import static io.harness.ngtriggers.beans.webhookresponse.WebhookEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.beans.EmbeddedUser;
import io.harness.cdng.pipeline.helpers.NGPipelineExecuteHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ngpipeline.pipeline.beans.resources.NGPipelineExecutionResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse.ParsePayloadResponseBuilder;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.NGTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerSpec;
import io.harness.ngtriggers.beans.target.TargetSpec;
import io.harness.ngtriggers.beans.target.pipeline.PipelineTargetSpec;
import io.harness.ngtriggers.beans.webhookresponse.WebhookEventResponse;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.ngtriggers.utils.WebhookTriggerFilterUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGTriggerWebhookExecutionHelper {
  private final NGTriggerService ngTriggerService;
  private final NGPipelineExecuteHelper ngPipelineExecuteHelper;
  private final WebhookEventPayloadParser webhookEventPayloadParser;

  public WebhookEventResponse handleTriggerWebhookEvent(TriggerWebhookEvent triggerWebhookEvent) {
    WebhookPayloadData webhookPayloadData;

    // 1. Parse Payload
    ParsePayloadResponse parsePayloadResponse = parseEventData(triggerWebhookEvent);
    if (parsePayloadResponse.isExceptionOccured()) {
      return WebhookEventResponseHelper.prepareResponseForScmException(parsePayloadResponse);
    }

    // 2. Get Trigger for Repo
    webhookPayloadData = parsePayloadResponse.getWebhookPayloadData();
    List<NGTriggerEntity> triggersForRepo =
        retrieveTriggersConfiguredForRepo(triggerWebhookEvent, webhookPayloadData.getRepository().getLink());
    if (EmptyPredicate.isEmpty(triggersForRepo)) {
      log.info("No trigger found for repoUrl:" + webhookPayloadData.getRepository().getLink());
      return WebhookEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_REPO, triggerWebhookEvent, null, EMPTY,
          "No Trigger was configured for Repo: " + webhookPayloadData.getRepository().getLink());
    }

    // 3. Apply Event, Action and Condition filters
    Optional<TriggerDetails> optionalEntity = applyFilters(webhookPayloadData, triggersForRepo);
    if (!optionalEntity.isPresent()) {
      log.info("No trigger matched payload after condition evaluation:");
      return WebhookEventResponseHelper.toResponse(
          NO_MATCHING_TRIGGER, triggerWebhookEvent, null, EMPTY, "No Trigger matched for payload event");
    }

    // 4. Request Execution if trigger is available
    log.info("Preparing for pipeline execution request");
    return triggerPipelineExecution(triggerWebhookEvent, optionalEntity.get());
  }

  private WebhookEventResponse triggerPipelineExecution(
      TriggerWebhookEvent triggerWebhookEvent, TriggerDetails triggerDetails) {
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    try {
      NGPipelineExecutionResponseDTO response =
          resolveRuntimeInputAndSubmitExecutionRequest(triggerDetails, triggerWebhookEvent.getPayload());
      if (response.isErrorResponse()) {
        log.warn(new StringBuilder(128)
                     .append(ngTriggerEntity.getTargetType())
                     .append(" execution failed to start : ")
                     .append(ngTriggerEntity.getTargetIdentifier())
                     .append(", using trigger: ")
                     .append(ngTriggerEntity.getIdentifier())
                     .toString());
        return WebhookEventResponseHelper.toResponse(
            TARGET_DID_NOT_EXECUTE, triggerWebhookEvent, response, ngTriggerEntity.getIdentifier(), EMPTY);
      } else {
        log.info(new StringBuilder(128)
                     .append(ngTriggerEntity.getTargetType())
                     .append(" execution was requested successfully for Pipeline: ")
                     .append(ngTriggerEntity.getTargetIdentifier())
                     .append(", using trigger: ")
                     .append(ngTriggerEntity.getIdentifier())
                     .toString());
        return WebhookEventResponseHelper.toResponse(TARGET_EXECUTION_REQUESTED, triggerWebhookEvent, response,
            ngTriggerEntity.getIdentifier(), "Pipeline execution was requested successfully");
      }
    } catch (Exception e) {
      log.info(new StringBuilder(128)
                   .append(" Exception occured while requesting ")
                   .append(ngTriggerEntity.getTargetType())
                   .append(" execution. Identifier: ")
                   .append(ngTriggerEntity.getTargetIdentifier())
                   .append(", using trigger: ")
                   .append(ngTriggerEntity.getIdentifier())
                   .toString());
      return WebhookEventResponseHelper.toResponse(
          INVALID_RUNTIME_INPUT_YAML, triggerWebhookEvent, null, ngTriggerEntity.getIdentifier(), e.getMessage());
    }
  }

  // Add error handling
  private ParsePayloadResponse parseEventData(TriggerWebhookEvent triggerWebhookEvent) {
    ParsePayloadResponseBuilder builder = ParsePayloadResponse.builder().originalEvent(triggerWebhookEvent);
    try {
      WebhookPayloadData webhookPayloadData = webhookEventPayloadParser.parseEvent(triggerWebhookEvent);
      builder.webhookPayloadData(webhookPayloadData).build();
    } catch (Exception e) {
      log.error("Exception while invoking SCM service for webhook trigger payload parsing", e);
      builder.exceptionOccured(true).exception(e).build();
    }

    return builder.build();
  }

  private List<NGTriggerEntity> retrieveTriggersConfiguredForRepo(
      TriggerWebhookEvent triggerWebhookEvent, String repoUrl) {
    Page<NGTriggerEntity> triggerPage =
        ngTriggerService.listWebhookTriggers(triggerWebhookEvent.getAccountId(), repoUrl, false);

    List<NGTriggerEntity> listOfTriggers = triggerPage.get().collect(Collectors.toList());
    return EmptyPredicate.isEmpty(listOfTriggers) ? Collections.emptyList() : listOfTriggers;
  }

  @VisibleForTesting
  Optional<TriggerDetails> applyFilters(WebhookPayloadData webhookPayloadData, List<NGTriggerEntity> triggersForRepo) {
    TriggerDetails matchedTrigger = null;
    for (NGTriggerEntity ngTriggerEntity : triggersForRepo) {
      NGTriggerConfig ngTriggerConfig = NGTriggerElementMapper.toTriggerConfig(ngTriggerEntity.getYaml());
      TriggerDetails triggerDetails =
          TriggerDetails.builder().ngTriggerConfig(ngTriggerConfig).ngTriggerEntity(ngTriggerEntity).build();
      if (checkTriggerEligibility(webhookPayloadData, triggerDetails)) {
        if (matchedTrigger != null) {
          throw new InvalidRequestException("More than one trigger matched the eligibility criteria");
        }
        matchedTrigger = triggerDetails;
      }
    }
    if (matchedTrigger == null) {
      return Optional.empty();
    }
    return Optional.of(matchedTrigger);
  }

  private boolean checkTriggerEligibility(WebhookPayloadData webhookPayloadData, TriggerDetails triggerDetails) {
    try {
      NGTriggerSpec spec = triggerDetails.getNgTriggerConfig().getSource().getSpec();
      if (!WebhookTriggerConfig.class.isAssignableFrom(spec.getClass())) {
        log.error("Trigger spec is not a WebhookTriggerConfig");
        return false;
      }

      WebhookTriggerSpec triggerSpec = ((WebhookTriggerConfig) spec).getSpec();
      return WebhookTriggerFilterUtil.evaluateFilterConditions(webhookPayloadData, triggerSpec);
    } catch (Exception e) {
      NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
      log.error("Failed while evaluating Trigger: " + ngTriggerEntity.getIdentifier()
          + ", For Account: " + ngTriggerEntity.getAccountId()
          + ", correlationId for event is: " + webhookPayloadData.getOriginalEvent().getUuid());
      return false;
    }
  }

  private NGPipelineExecutionResponseDTO resolveRuntimeInputAndSubmitExecutionRequest(
      TriggerDetails triggerDetails, String eventPayload) {
    NGTriggerConfig ngTriggerConfig = triggerDetails.getNgTriggerConfig();
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    TargetSpec targetSpec = ngTriggerConfig.getTarget().getSpec();
    EmbeddedUser embeddedUser = EmbeddedUser.builder().email("email").name("name").uuid("uuid").build();

    if (PipelineTargetSpec.class.isAssignableFrom(targetSpec.getClass())) {
      PipelineTargetSpec pipelineTargetSpec = (PipelineTargetSpec) targetSpec;
      return ngPipelineExecuteHelper.runPipelineWithInputSetPipelineYaml(ngTriggerEntity.getAccountId(),
          ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(),
          ngTriggerEntity.getTargetIdentifier(), pipelineTargetSpec.getRuntimeInputYaml(), eventPayload, false,
          embeddedUser);
    }
    throw new InvalidRequestException("Target type does not match");
  }
}
