package io.harness.engine.utils;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class ProducerCacheKey {
  enum EventCategory { INTERRUPT_EVENT, ORCHESTRATION_EVENT, FACILITATOR_EVENT, NODE_START }
  String serviceName;
  EventCategory eventCategory;
}
