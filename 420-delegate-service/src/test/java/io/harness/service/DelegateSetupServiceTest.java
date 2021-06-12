package io.harness.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.NICOLAS;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.DelegateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupDetails;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateGroupStatus;
import io.harness.delegate.beans.DelegateInsightsBarDetails;
import io.harness.delegate.beans.DelegateInsightsDetails;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateSize;
import io.harness.delegate.beans.DelegateSizeDetails;
import io.harness.delegate.utils.DelegateEntityOwnerMapper;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.impl.DelegateSetupServiceImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateInsightsService;

import software.wings.beans.DelegateConnection;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
public class DelegateSetupServiceTest extends DelegateServiceTestBase {
  private static final String VERSION = "1.0.0";
  private static final String GROUPED_HOSTNAME_SUFFIX = "-{n}";

  @Mock private DelegateCache delegateCache;
  @Mock private DelegateInsightsService delegateInsightsService;

  @InjectMocks @Inject private DelegateSetupServiceImpl delegateSetupService;
  @Inject private HPersistence persistence;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldListAccountDelegateGroups() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId))
        .thenReturn(DelegateProfile.builder().name("profile").selectors(ImmutableList.of("s1", "s2")).build());

    DelegateSizeDetails grp1SizeDetails = DelegateSizeDetails.builder()
                                              .size(DelegateSize.LARGE)
                                              .cpu(2.5d)
                                              .label("size")
                                              .ram(2048)
                                              .taskLimit(25)
                                              .replicas(2)
                                              .build();

    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .name("grp1")
                                       .accountId(accountId)
                                       .ng(true)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .sizeDetails(grp1SizeDetails)
                                       .delegateConfigurationId(delegateProfileId)
                                       .build();
    persistence.save(delegateGroup1);
    DelegateGroup delegateGroup2 =
        DelegateGroup.builder()
            .name("grp2")
            .accountId(accountId)
            .ng(true)
            .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).replicas(1).build())
            .build();
    persistence.save(delegateGroup2);

    when(delegateCache.getDelegateGroup(accountId, delegateGroup1.getUuid())).thenReturn(delegateGroup1);
    when(delegateCache.getDelegateGroup(accountId, delegateGroup2.getUuid())).thenReturn(delegateGroup2);

    // Insights
    DelegateInsightsDetails delegateInsightsDetails =
        DelegateInsightsDetails.builder()
            .insights(ImmutableList.of(
                DelegateInsightsBarDetails.builder().build(), DelegateInsightsBarDetails.builder().build()))
            .build();
    when(
        delegateInsightsService.retrieveDelegateInsightsDetails(eq(accountId), eq(delegateGroup1.getUuid()), anyLong()))
        .thenReturn(delegateInsightsDetails);

    // these three delegates should be returned for group 1
    Delegate delegate1 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-0")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .build();

    Delegate delegate2 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-1")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .lastHeartBeat(System.currentTimeMillis() - 60000)
                             .build();

    // this delegate should cause an empty group to be returned
    Delegate delegate3 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateName("grp2")
                             .sizeDetails(DelegateSizeDetails.builder().replicas(1).build())
                             .delegateGroupId(delegateGroup2.getUuid())
                             .build();

    Delegate deletedDelegate =
        createDelegateBuilder().accountId(accountId).status(DelegateInstanceStatus.DELETED).build();

    Delegate orgDelegate = createDelegateBuilder()
                               .accountId(accountId)
                               .owner(DelegateEntityOwner.builder().identifier(generateUuid()).build())
                               .build();

    persistence.save(Arrays.asList(orgDelegate, deletedDelegate, delegate1, delegate2, delegate3));

    DelegateConnection delegateConnection1 = DelegateConnection.builder()
                                                 .accountId(accountId)
                                                 .delegateId(delegate1.getUuid())
                                                 .lastHeartbeat(System.currentTimeMillis())
                                                 .disconnected(false)
                                                 .version(VERSION)
                                                 .build();
    DelegateConnection delegateConnection2 = DelegateConnection.builder()
                                                 .accountId(accountId)
                                                 .delegateId(delegate2.getUuid())
                                                 .lastHeartbeat(System.currentTimeMillis())
                                                 .disconnected(false)
                                                 .version(VERSION)
                                                 .build();
    persistence.save(delegateConnection1);
    persistence.save(delegateConnection2);

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetails(accountId, null, null);

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(2);
    assertThat(delegateGroupListing.getDelegateGroupDetails())
        .extracting(DelegateGroupDetails::getGroupName)
        .containsOnly("grp1", "grp2");

    for (DelegateGroupDetails group : delegateGroupListing.getDelegateGroupDetails()) {
      if (group.getGroupName().equals("grp1")) {
        assertThat(group.getDelegateInstanceDetails()).hasSize(2);
        assertThat(group.getGroupId()).isEqualTo(delegateGroup1.getUuid());
        assertThat(group.getDelegateType()).isEqualTo(KUBERNETES);
        assertThat(group.getGroupHostName()).isEqualTo("kube-{n}");
        assertThat(group.getDelegateDescription()).isEqualTo("description");
        assertThat(group.getDelegateConfigurationId()).isEqualTo(delegateProfileId);
        assertThat(group.getGroupImplicitSelectors()).isNotNull();
        assertThat(group.getGroupImplicitSelectors().containsKey("grp1")).isTrue();
        assertThat(group.getGroupImplicitSelectors().containsKey("kube-0")).isFalse();
        assertThat(group.getGroupImplicitSelectors().containsKey("kube-1")).isFalse();
        assertThat(group.getGroupImplicitSelectors().containsKey("profile")).isTrue();
        assertThat(group.getGroupImplicitSelectors().containsKey("s1")).isTrue();
        assertThat(group.getGroupImplicitSelectors().containsKey("s2")).isTrue();
        assertThat(group.getLastHeartBeat()).isEqualTo(delegate1.getLastHeartBeat());
        assertThat(group.isActivelyConnected()).isTrue();
        assertThat(group.getSizeDetails()).isEqualTo(grp1SizeDetails);
        assertThat(group.getDelegateInstanceDetails())
            .extracting(DelegateGroupListing.DelegateInner::getUuid)
            .containsOnly(delegate1.getUuid(), delegate2.getUuid());
        assertThat(group.getDelegateInsightsDetails()).isNotNull();
        assertThat(group.getDelegateInsightsDetails().getInsights()).hasSize(2);
      } else if (group.getGroupName().equals("grp2")) {
        assertThat(group.getDelegateInstanceDetails()).isEmpty();
        assertThat(group.isActivelyConnected()).isFalse();
        assertThat(group.getSizeDetails().getReplicas()).isEqualTo(1);
      }
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldListDelegateGroupsUpTheHierarchy() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    DelegateGroup acctGroup = DelegateGroup.builder().accountId(accountId).ng(true).build();
    DelegateGroup orgGroup = DelegateGroup.builder()
                                 .accountId(accountId)
                                 .ng(true)
                                 .owner(DelegateEntityOwnerMapper.buildOwner(orgId, null))
                                 .build();
    DelegateGroup projectGroup = DelegateGroup.builder()
                                     .accountId(accountId)
                                     .ng(true)
                                     .owner(DelegateEntityOwnerMapper.buildOwner(orgId, projectId))
                                     .build();
    persistence.save(Arrays.asList(acctGroup, orgGroup, projectGroup));

    Delegate cgAcctDelegate = createDelegateBuilder()
                                  .accountId(accountId)
                                  .ng(false)
                                  .delegateType(KUBERNETES)
                                  .delegateName(generateUuid())
                                  .hostName(generateUuid())
                                  .build();

    Delegate acctDelegate = createDelegateBuilder()
                                .accountId(accountId)
                                .ng(true)
                                .delegateType(KUBERNETES)
                                .delegateName(generateUuid())
                                .hostName(generateUuid())
                                .delegateGroupId(acctGroup.getUuid())
                                .build();

    Delegate orgDelegate = createDelegateBuilder()
                               .accountId(accountId)
                               .ng(true)
                               .delegateType(KUBERNETES)
                               .delegateName(generateUuid())
                               .hostName(generateUuid())
                               .delegateGroupId(orgGroup.getUuid())
                               .owner(DelegateEntityOwner.builder().identifier(orgId).build())
                               .build();

    Delegate projectDelegate = createDelegateBuilder()
                                   .accountId(accountId)
                                   .ng(true)
                                   .delegateType(KUBERNETES)
                                   .delegateName(generateUuid())
                                   .hostName(generateUuid())
                                   .delegateGroupId(projectGroup.getUuid())
                                   .owner(DelegateEntityOwner.builder().identifier(orgId + "/" + projectId).build())
                                   .build();

    persistence.save(Arrays.asList(cgAcctDelegate, acctDelegate, orgDelegate, projectDelegate));

    DelegateGroupListing delegateGroupListing =
        delegateSetupService.listDelegateGroupDetailsUpTheHierarchy(accountId, null, null);
    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(1);
    assertThat(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupId()).isEqualTo(acctGroup.getUuid());

    delegateGroupListing = delegateSetupService.listDelegateGroupDetailsUpTheHierarchy(accountId, orgId, null);
    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(2);
    assertThat(Arrays.asList(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupId(),
                   delegateGroupListing.getDelegateGroupDetails().get(1).getGroupId()))
        .containsExactlyInAnyOrder(acctGroup.getUuid(), orgGroup.getUuid());

    delegateGroupListing = delegateSetupService.listDelegateGroupDetailsUpTheHierarchy(accountId, orgId, projectId);
    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(3);
    assertThat(Arrays.asList(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupId(),
                   delegateGroupListing.getDelegateGroupDetails().get(1).getGroupId(),
                   delegateGroupListing.getDelegateGroupDetails().get(2).getGroupId()))
        .containsExactlyInAnyOrder(acctGroup.getUuid(), orgGroup.getUuid(), projectGroup.getUuid());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGetDelegateGroupDetails() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId))
        .thenReturn(DelegateProfile.builder().name("profile").selectors(ImmutableList.of("s1", "s2")).build());

    DelegateSizeDetails grp1SizeDetails = DelegateSizeDetails.builder()
                                              .size(DelegateSize.LARGE)
                                              .cpu(2.5d)
                                              .label("size")
                                              .ram(2048)
                                              .taskLimit(25)
                                              .replicas(2)
                                              .build();

    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .name("grp1")
                                       .accountId(accountId)
                                       .ng(true)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .sizeDetails(grp1SizeDetails)
                                       .delegateConfigurationId(delegateProfileId)
                                       .build();
    persistence.save(delegateGroup1);

    when(delegateCache.getDelegateGroup(accountId, delegateGroup1.getUuid())).thenReturn(delegateGroup1);

    // Insights
    DelegateInsightsDetails delegateInsightsDetails =
        DelegateInsightsDetails.builder()
            .insights(ImmutableList.of(
                DelegateInsightsBarDetails.builder().build(), DelegateInsightsBarDetails.builder().build()))
            .build();
    when(
        delegateInsightsService.retrieveDelegateInsightsDetails(eq(accountId), eq(delegateGroup1.getUuid()), anyLong()))
        .thenReturn(delegateInsightsDetails);

    Delegate delegate1 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-0")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .build();

    Delegate delegate2 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-1")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .lastHeartBeat(System.currentTimeMillis() - 60000)
                             .build();

    persistence.save(Arrays.asList(delegate1, delegate2));

    DelegateConnection delegateConnection1 = DelegateConnection.builder()
                                                 .accountId(accountId)
                                                 .delegateId(delegate1.getUuid())
                                                 .lastHeartbeat(System.currentTimeMillis())
                                                 .disconnected(false)
                                                 .version(VERSION)
                                                 .build();
    DelegateConnection delegateConnection2 = DelegateConnection.builder()
                                                 .accountId(accountId)
                                                 .delegateId(delegate2.getUuid())
                                                 .lastHeartbeat(System.currentTimeMillis())
                                                 .disconnected(false)
                                                 .version(VERSION)
                                                 .build();
    persistence.save(delegateConnection1);
    persistence.save(delegateConnection2);

    DelegateGroupDetails delegateGroupDetails =
        delegateSetupService.getDelegateGroupDetails(accountId, delegateGroup1.getUuid());

    assertThat(delegateGroupDetails).isNotNull();

    assertThat(delegateGroupDetails.getGroupName()).isEqualTo("grp1");
    assertThat(delegateGroupDetails.getDelegateInstanceDetails()).hasSize(2);
    assertThat(delegateGroupDetails.getGroupId()).isEqualTo(delegateGroup1.getUuid());
    assertThat(delegateGroupDetails.getDelegateType()).isEqualTo(KUBERNETES);
    assertThat(delegateGroupDetails.getGroupHostName()).isEqualTo("kube-{n}");
    assertThat(delegateGroupDetails.getDelegateDescription()).isEqualTo("description");
    assertThat(delegateGroupDetails.getDelegateConfigurationId()).isEqualTo(delegateProfileId);
    assertThat(delegateGroupDetails.getGroupImplicitSelectors()).isNotNull();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("grp1")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("kube-0")).isFalse();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("kube-1")).isFalse();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("profile")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("s1")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("s2")).isTrue();
    assertThat(delegateGroupDetails.getLastHeartBeat()).isEqualTo(delegate1.getLastHeartBeat());
    assertThat(delegateGroupDetails.isActivelyConnected()).isTrue();
    assertThat(delegateGroupDetails.getSizeDetails()).isEqualTo(grp1SizeDetails);
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::getUuid)
        .containsOnly(delegate1.getUuid(), delegate2.getUuid());
    assertThat(delegateGroupDetails.getDelegateInsightsDetails()).isNotNull();
    assertThat(delegateGroupDetails.getDelegateInsightsDetails().getInsights()).hasSize(2);
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("testDelegateName")
        .delegateType(KUBERNETES)
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRetrieveGroupedHostnameNullValue() {
    String hostNameForGroupedDelegate = delegateSetupService.getHostNameForGroupedDelegate(null);

    assertThat(hostNameForGroupedDelegate).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRetrieveGroupedHostnameEmptyValue() {
    String hostNameForGroupedDelegate = delegateSetupService.getHostNameForGroupedDelegate("");

    assertThat(hostNameForGroupedDelegate).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRetrieveGroupedHostnameValidValue() {
    String hostNameForGroupedDelegate = delegateSetupService.getHostNameForGroupedDelegate("test-hostname-1");

    assertThat(hostNameForGroupedDelegate).isEqualTo("test-hostname" + GROUPED_HOSTNAME_SUFFIX);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegatesImplicitSelectors() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    DelegateProfile delegateProfile = DelegateProfile.builder()
                                          .uuid(delegateProfileId)
                                          .accountId(accountId)
                                          .name(generateUuid())
                                          .selectors(ImmutableList.of("jkl", "fgh"))
                                          .build();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId)).thenReturn(delegateProfile);

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .ip("127.0.0.1")
                            .hostName("host")
                            .delegateName("test")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateProfileId(delegateProfile.getUuid())
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateSetupService.retrieveDelegateImplicitSelectors(delegate).keySet();
    assertThat(tags.size()).isEqualTo(5);
    assertThat(tags).containsExactlyInAnyOrder(delegateProfile.getName().toLowerCase(), "test", "jkl", "fgh", "host");
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegateImplicitSelectorsWithDelegateProfileSelectorsOnly() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    DelegateProfile delegateProfile = DelegateProfile.builder()
                                          .uuid(delegateProfileId)
                                          .accountId(accountId)
                                          .selectors(ImmutableList.of("jkl", "fgh"))
                                          .build();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId)).thenReturn(delegateProfile);

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .delegateProfileId(delegateProfile.getUuid())
                            .ip("127.0.0.1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);

    Set<String> selectors = delegateSetupService.retrieveDelegateImplicitSelectors(delegate).keySet();
    assertThat(selectors.size()).isEqualTo(2);
    assertThat(selectors).containsExactly("fgh", "jkl");
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegateImplicitSelectorsWithHostName() {
    String accountId = generateUuid();

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .ip("127.0.0.1")
                            .hostName("a.b.c")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateSetupService.retrieveDelegateImplicitSelectors(delegate).keySet();
    assertThat(tags.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegateImplicitSelectorsWithGroupName() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup =
        DelegateGroup.builder().uuid(generateUuid()).accountId(accountId).name("group").build();
    when(delegateCache.getDelegateGroup(accountId, delegateGroup.getUuid())).thenReturn(delegateGroup);

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .version(VERSION)
                            .hostName("host")
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateGroupId(delegateGroup.getUuid())
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateSetupService.retrieveDelegateImplicitSelectors(delegate).keySet();
    assertThat(tags.size()).isEqualTo(1);
    assertThat(tags).containsExactlyInAnyOrder("group");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateDelegateGroupsShouldReturnEmptyList() {
    String accountId = generateUuid();
    assertThat(delegateSetupService.validateDelegateGroups(accountId, null, null, null)).isEmpty();
    assertThat(delegateSetupService.validateDelegateGroups(accountId, null, null, Collections.emptyList())).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateDelegateGroupsShouldReturnCorrectResult() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    DelegateGroup deletedGroup =
        DelegateGroup.builder().accountId(accountId).ng(true).status(DelegateGroupStatus.DELETED).build();
    DelegateGroup acctGroup = DelegateGroup.builder().accountId(accountId).ng(true).build();
    DelegateGroup orgGroup = DelegateGroup.builder()
                                 .accountId(accountId)
                                 .ng(true)
                                 .owner(DelegateEntityOwnerMapper.buildOwner(orgId, null))
                                 .build();
    DelegateGroup projectGroup = DelegateGroup.builder()
                                     .accountId(accountId)
                                     .ng(true)
                                     .owner(DelegateEntityOwnerMapper.buildOwner(orgId, projectId))
                                     .build();
    persistence.save(Arrays.asList(deletedGroup, acctGroup, orgGroup, projectGroup));

    Delegate cgDelegate = Delegate.builder()
                              .accountId(accountId)
                              .delegateGroupId(generateUuid())
                              .status(DelegateInstanceStatus.ENABLED)
                              .build();
    Delegate deletedDelegate = Delegate.builder()
                                   .accountId(accountId)
                                   .ng(true)
                                   .delegateGroupId(deletedGroup.getUuid())
                                   .status(DelegateInstanceStatus.DELETED)
                                   .build();
    Delegate acctDelegate = Delegate.builder()
                                .accountId(accountId)
                                .ng(true)
                                .delegateGroupId(acctGroup.getUuid())
                                .status(DelegateInstanceStatus.ENABLED)
                                .build();
    Delegate orgDelegate = Delegate.builder()
                               .accountId(accountId)
                               .ng(true)
                               .delegateGroupId(orgGroup.getUuid())
                               .status(DelegateInstanceStatus.ENABLED)
                               .owner(DelegateEntityOwnerMapper.buildOwner(orgId, null))
                               .build();
    Delegate projectDelegate = Delegate.builder()
                                   .accountId(accountId)
                                   .ng(true)
                                   .delegateGroupId(projectGroup.getUuid())
                                   .status(DelegateInstanceStatus.ENABLED)
                                   .owner(DelegateEntityOwnerMapper.buildOwner(orgId, projectId))
                                   .build();

    persistence.saveBatch(Arrays.asList(cgDelegate, deletedDelegate, acctDelegate, orgDelegate, projectDelegate));

    // Test non-existing delegate
    assertThat(
        delegateSetupService.validateDelegateGroups(accountId, null, null, Collections.singletonList(generateUuid())))
        .containsExactly(false);

    // Test cg delegate
    assertThat(delegateSetupService.validateDelegateGroups(
                   accountId, null, null, Collections.singletonList(cgDelegate.getDelegateGroupId())))
        .containsExactly(false);

    // Test account delegate
    assertThat(delegateSetupService.validateDelegateGroups(accountId, null, null,
                   Arrays.asList(deletedDelegate.getDelegateGroupId(), acctDelegate.getDelegateGroupId())))
        .containsExactly(false, true);

    // Test org delegate
    assertThat(delegateSetupService.validateDelegateGroups(
                   accountId, orgId, null, Collections.singletonList(orgDelegate.getDelegateGroupId())))
        .containsExactly(true);

    // Test project delegate
    assertThat(delegateSetupService.validateDelegateGroups(
                   accountId, orgId, projectId, Collections.singletonList(projectDelegate.getDelegateGroupId())))
        .containsExactly(true);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateDelegateConfigurationsShouldReturnEmptyList() {
    String accountId = generateUuid();
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, null, null, null)).isEmpty();
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, null, null, Collections.emptyList()))
        .isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateDelegateConfigurationsShouldReturnCorrectResult() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    final DelegateProfile cgDelegateProfile = DelegateProfile.builder().accountId(accountId).name("cg").build();
    final DelegateProfile primaryAcctDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("primary").ng(true).primary(true).build();
    final DelegateProfile acctDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("acct").ng(true).build();

    final DelegateEntityOwner orgOwner = DelegateEntityOwnerMapper.buildOwner(orgId, null);
    final DelegateProfile primaryOrgDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("primary").ng(true).primary(true).owner(orgOwner).build();
    final DelegateProfile orgDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("org").ng(true).owner(orgOwner).build();

    final DelegateEntityOwner projectOwner = DelegateEntityOwnerMapper.buildOwner(orgId, projectId);
    final DelegateProfile primaryProjectDelegateProfile = DelegateProfile.builder()
                                                              .accountId(accountId)
                                                              .name("primary")
                                                              .ng(true)
                                                              .primary(true)
                                                              .owner(projectOwner)
                                                              .build();
    final DelegateProfile projectDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("project").ng(true).owner(projectOwner).build();

    persistence.saveBatch(Arrays.asList(cgDelegateProfile, primaryAcctDelegateProfile, acctDelegateProfile,
        primaryOrgDelegateProfile, orgDelegateProfile, primaryProjectDelegateProfile, projectDelegateProfile));

    // Test non-existing delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(
                   accountId, null, null, Collections.singletonList(generateUuid())))
        .containsExactly(false);

    // Test cg delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(
                   accountId, null, null, Collections.singletonList(cgDelegateProfile.getUuid())))
        .containsExactly(false);

    // Test account delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, null, null,
                   Arrays.asList(primaryAcctDelegateProfile.getUuid(), acctDelegateProfile.getUuid())))
        .containsExactly(true, true);

    // Test org delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, orgId, null,
                   Arrays.asList(primaryOrgDelegateProfile.getUuid(), orgDelegateProfile.getUuid())))
        .containsExactly(true, true);

    // Test project delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, orgId, projectId,
                   Arrays.asList(primaryProjectDelegateProfile.getUuid(), projectDelegateProfile.getUuid())))
        .containsExactly(true, true);
  }
}
