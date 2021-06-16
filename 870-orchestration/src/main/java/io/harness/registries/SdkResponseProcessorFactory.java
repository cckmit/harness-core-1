package io.harness.registries;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.event.handlers.AddExecutableResponseRequestProcessor;
import io.harness.event.handlers.AdviserResponseRequestProcessor;
import io.harness.event.handlers.ErrorEventRequestProcessor;
import io.harness.event.handlers.FacilitateResponseRequestProcessor;
import io.harness.event.handlers.HandleProgressRequestProcessor;
import io.harness.event.handlers.HandleStepResponseRequestProcessor;
import io.harness.event.handlers.QueueTaskRequestProcessor;
import io.harness.event.handlers.ResumeNodeExecutionRequestProcessor;
import io.harness.event.handlers.SdkResponseProcessor;
import io.harness.event.handlers.SpawnChildRequestProcessor;
import io.harness.event.handlers.SpawnChildrenRequestProcessor;
import io.harness.event.handlers.SuspendChainRequestProcessor;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class SdkResponseProcessorFactory {
  @Inject Injector injector;

  public SdkResponseProcessor getHandler(SdkResponseEventType eventType) {
    switch (eventType) {
      case ADD_EXECUTABLE_RESPONSE:
        return injector.getInstance(AddExecutableResponseRequestProcessor.class);
      case HANDLE_STEP_RESPONSE:
        return injector.getInstance(HandleStepResponseRequestProcessor.class);
      case RESUME_NODE_EXECUTION:
        return injector.getInstance(ResumeNodeExecutionRequestProcessor.class);
      case HANDLE_FACILITATE_RESPONSE:
        return injector.getInstance(FacilitateResponseRequestProcessor.class);
      case HANDLE_EVENT_ERROR:
        return injector.getInstance(ErrorEventRequestProcessor.class);
      case HANDLE_ADVISER_RESPONSE:
        return injector.getInstance(AdviserResponseRequestProcessor.class);
      case QUEUE_TASK:
        return injector.getInstance(QueueTaskRequestProcessor.class);
      case SUSPEND_CHAIN:
        return injector.getInstance(SuspendChainRequestProcessor.class);
      case SPAWN_CHILD:
        return injector.getInstance(SpawnChildRequestProcessor.class);
      case SPAWN_CHILDREN:
        return injector.getInstance(SpawnChildrenRequestProcessor.class);
      case HANDLE_PROGRESS:
        return injector.getInstance(HandleProgressRequestProcessor.class);
      default:
        throw new InvalidRequestException("Unknown sdkResponseEventType.");
    }
  }
}
