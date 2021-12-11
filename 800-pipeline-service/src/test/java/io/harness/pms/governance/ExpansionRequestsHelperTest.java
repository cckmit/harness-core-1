package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ExpansionRequestsHelperTest extends CategoryTest {
  @InjectMocks ExpansionRequestsHelper expansionRequestsHelper;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;

  List<PmsSdkInstance> activeInstances;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Map<String, Set<String>> cdSupportedTypes = new HashMap<>();
    cdSupportedTypes.put("stage", Collections.singleton("Deployment"));
    cdSupportedTypes.put("step", Collections.singleton("K8sApply"));
    PmsSdkInstance cdInstance = PmsSdkInstance.builder()
                                    .name("cd")
                                    .expandableFields(Arrays.asList("connectorRef", "serviceRef", "environmentRef"))
                                    .supportedTypes(cdSupportedTypes)
                                    .build();

    Map<String, Set<String>> pmsSupportedTypes = new HashMap<>();
    pmsSupportedTypes.put("stage", Collections.singleton("Approval"));
    pmsSupportedTypes.put("step", Collections.singleton("ShellScript"));
    PmsSdkInstance pmsInstance = PmsSdkInstance.builder()
                                     .name("pms")
                                     .expandableFields(Collections.singletonList("connectorRef"))
                                     .supportedTypes(pmsSupportedTypes)
                                     .build();
    activeInstances = Arrays.asList(cdInstance, pmsInstance);
    doReturn(activeInstances).when(pmsSdkInstanceService).getActiveInstances();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetExpandableFieldsPerService() {
    Map<ModuleType, Set<String>> expandableFieldsPerService = expansionRequestsHelper.getExpandableFieldsPerService();
    assertThat(expandableFieldsPerService).hasSize(2);
    Set<String> cdKeys = expandableFieldsPerService.get(ModuleType.CD);
    assertThat(cdKeys).hasSize(3);
    assertThat(cdKeys).contains("connectorRef", "serviceRef", "environmentRef");
    Set<String> pmsKeys = expandableFieldsPerService.get(ModuleType.PMS);
    assertThat(pmsKeys).hasSize(1);
    assertThat(pmsKeys).contains("connectorRef");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetTypeToService() {
    Map<String, ModuleType> typeToService = expansionRequestsHelper.getTypeToService();
    assertThat(typeToService).hasSize(4);
    assertThat(typeToService.get("Approval")).isEqualTo(ModuleType.PMS);
    assertThat(typeToService.get("ShellScript")).isEqualTo(ModuleType.PMS);
    assertThat(typeToService.get("Deployment")).isEqualTo(ModuleType.CD);
    assertThat(typeToService.get("K8sApply")).isEqualTo(ModuleType.CD);
  }
}