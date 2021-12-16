package io.harness.resourcegroup.resourceclient.dashboard;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.resourcegroup.beans.ValidatorType.RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.consumer.Message;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.service.Resource;
import io.harness.resourcegroup.framework.service.ResourceInfo;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Slf4j
public class DashboardResourceImpl implements Resource {
  public static final String DASHBOARD = "DASHBOARDS";

  @Override
  public List<Boolean> validate(List<String> resourceIds, Scope scope) {
    return resourceIds.stream().map(resourceId -> true).collect(Collectors.toList());
  }

  @Override
  public Map<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind() {
    return ImmutableMap.of(ScopeLevel.ACCOUNT,
        EnumSet.of(RESOURCE_IDENTIFIER, RESOURCE_TYPE, RESOURCE_TYPE_INCLUDING_CHILD_SCOPES), ScopeLevel.ORGANIZATION,
        EnumSet.of(RESOURCE_IDENTIFIER, RESOURCE_TYPE));
  }

  @Override
  public String getType() {
    return DASHBOARD;
  }

  @Override
  public Set<ScopeLevel> getValidScopeLevels() {
    return EnumSet.of(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION);
  }

  @Override
  public Optional<String> getEventFrameworkEntityType() {
    return Optional.empty();
  }

  @Override
  public ResourceInfo getResourceInfoFromEvent(Message message) {
    return null;
  }
}