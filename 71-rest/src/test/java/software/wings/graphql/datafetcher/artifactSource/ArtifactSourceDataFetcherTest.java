package software.wings.graphql.datafetcher.artifactSource;

import static io.harness.rule.OwnerRule.AADITI;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static software.wings.graphql.datafetcher.artifactSource.ArtifactSourceTestHelper.getNexusArtifactStream;
import static software.wings.graphql.datafetcher.artifactSource.ArtifactSourceTestHelper.getSmbArtifactStream;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import com.google.inject.Inject;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.artifact.SmbArtifactStream;
import software.wings.graphql.datafetcher.AbstractDataFetcherTest;
import software.wings.graphql.schema.query.QLArtifactSourceQueryParam;
import software.wings.graphql.schema.type.artifactSource.QLArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLNexusArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLSMBArtifactSource;
import software.wings.service.intfc.ArtifactStreamService;

import java.util.ArrayList;
import java.util.List;

public class ArtifactSourceDataFetcherTest extends AbstractDataFetcherTest {
  @Mock private ArtifactStreamService artifactStreamService;
  @Inject @InjectMocks private ArtifactSourceDataFetcher artifactSourceDataFetcher;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldReturnParameterizedNexus2ArtifactSource() {
    NexusArtifactStream nexusArtifactStream = getNexusArtifactStream(SETTING_ID, ARTIFACT_STREAM_ID);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    List<String> params = new ArrayList<>();
    params.addAll(asList("repo", "groupId", "path"));
    when(artifactStreamService.getArtifactStreamParameters(ARTIFACT_STREAM_ID)).thenReturn(params);
    QLArtifactSourceQueryParam qlArtifactSourceQueryParam = QLArtifactSourceQueryParam.builder()
                                                                .artifactSourceId(ARTIFACT_STREAM_ID)
                                                                .applicationId(APP_ID)
                                                                .serviceId(SERVICE_ID)
                                                                .build();
    QLArtifactSource qlArtifactSource = artifactSourceDataFetcher.fetch(qlArtifactSourceQueryParam, ACCOUNT_ID);
    assertThat(qlArtifactSource).isNotNull();
    assertThat(qlArtifactSource).isInstanceOf(QLNexusArtifactSource.class);
    QLNexusArtifactSource qlNexusArtifactSource = (QLNexusArtifactSource) qlArtifactSource;
    assertThat(qlNexusArtifactSource.getName()).isEqualTo("testNexus");
    assertThat(qlNexusArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlNexusArtifactSource.getParameters().size()).isEqualTo(3);
    assertThat(qlNexusArtifactSource.getParameters()).containsAll(asList("repo", "groupId", "path"));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldReturnSmbArtifactSource() {
    SmbArtifactStream smbArtifactStream = getSmbArtifactStream(SETTING_ID, ARTIFACT_STREAM_ID);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(smbArtifactStream);
    QLArtifactSourceQueryParam qlArtifactSourceQueryParam = QLArtifactSourceQueryParam.builder()
                                                                .artifactSourceId(ARTIFACT_STREAM_ID)
                                                                .applicationId(APP_ID)
                                                                .serviceId(SERVICE_ID)
                                                                .build();
    QLArtifactSource qlArtifactSource = artifactSourceDataFetcher.fetch(qlArtifactSourceQueryParam, ACCOUNT_ID);
    assertThat(qlArtifactSource).isNotNull();
    assertThat(qlArtifactSource).isInstanceOf(QLSMBArtifactSource.class);
    QLSMBArtifactSource qlsmbArtifactSource = (QLSMBArtifactSource) qlArtifactSource;
    assertThat(qlsmbArtifactSource.getName()).isEqualTo("testSMB");
    assertThat(qlsmbArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
  }
}
