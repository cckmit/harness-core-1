package io.harness.enforcement.handlers.impl;

import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.bases.StaticLimitRestriction;
import io.harness.enforcement.beans.FeatureRestrictionUsageDTO;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailsDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.enforcement.services.impl.EnforcementSdkClient;
import io.harness.licensing.Edition;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

public class StaticLimitRestrictionHandlerTest extends CategoryTest {
  private StaticLimitRestrictionHandler handler;
  private FeatureRestrictionName featureRestrictionName = FeatureRestrictionName.TEST1;
  private StaticLimitRestriction restriction;
  private EnforcementSdkClient client;
  private String accountIdentifier = "accountId";
  private ModuleType moduleType = ModuleType.CD;
  private Edition edition = Edition.ENTERPRISE;

  @Before
  public void setup() throws IOException {
    handler = new StaticLimitRestrictionHandler();
    client = mock(EnforcementSdkClient.class);
    Call<ResponseDTO<FeatureRestrictionUsageDTO>> usageCall = mock(Call.class);
    when(usageCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(FeatureRestrictionUsageDTO.builder().count(10).build())));
    when(client.getRestrictionUsage(any(), any(), any())).thenReturn(usageCall);
    restriction = new StaticLimitRestriction(RestrictionType.STATIC_LIMIT, 11, false, client);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheck() {
    handler.check(featureRestrictionName, restriction, accountIdentifier, moduleType, edition);
  }

  @Test(expected = LimitExceededException.class)
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheckFailed() {
    StaticLimitRestriction invalid = new StaticLimitRestriction(RestrictionType.RATE_LIMIT, 10, false, client);
    handler.check(featureRestrictionName, invalid, accountIdentifier, moduleType, edition);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testFillRestrictionDTO() {
    FeatureRestrictionDetailsDTO dto = FeatureRestrictionDetailsDTO.builder().build();
    handler.fillRestrictionDTO(featureRestrictionName, restriction, accountIdentifier, edition, dto);

    assertThat(dto.getRestrictionType()).isEqualTo(RestrictionType.STATIC_LIMIT);
    assertThat(dto.getRestriction()).isNotNull();
    assertThat(dto.isAllowed()).isTrue();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetMetadataDTO() {
    RestrictionMetadataDTO metadataDTO = handler.getMetadataDTO(restriction);

    StaticLimitRestrictionMetadataDTO dto = (StaticLimitRestrictionMetadataDTO) metadataDTO;
    assertThat(dto.getRestrictionType()).isEqualTo(RestrictionType.STATIC_LIMIT);
    assertThat(dto.getLimit()).isEqualTo(11);
  }
}