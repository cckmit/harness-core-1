package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.eraro.ErrorCode.PR_CREATION_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.connector.impl.ConnectorErrorMessagesHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.ScmException;
import io.harness.gitsync.common.dtos.CreatePRDTO;
import io.harness.gitsync.common.dtos.GitDiffResultFileListDTO;
import io.harness.gitsync.common.dtos.GitFileChangeDTO;
import io.harness.gitsync.common.dtos.GitFileContent;
import io.harness.gitsync.common.helper.FileBatchResponseMapper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.PRFileListMapper;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.FileBatchContentResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.service.ScmClient;
import io.harness.tasks.DecryptGitApiAccessHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

// Don't inject this directly go through ScmClientOrchestrator.
@Slf4j
@OwnedBy(DX)
public class ScmManagerFacilitatorServiceImpl extends AbstractScmClientFacilitatorServiceImpl {
  private ScmClient scmClient;
  private GitSyncConnectorHelper gitSyncConnectorHelper;
  private DecryptGitApiAccessHelper decryptGitApiAccessHelper;

  @Inject
  public ScmManagerFacilitatorServiceImpl(ScmClient scmClient,
      @Named("connectorDecoratorService") ConnectorService connectorService,
      ConnectorErrorMessagesHelper connectorErrorMessagesHelper, YamlGitConfigService yamlGitConfigService,
      DecryptGitApiAccessHelper decryptGitApiAccessHelper, GitSyncConnectorHelper gitSyncConnectorHelper) {
    super(connectorService, connectorErrorMessagesHelper, yamlGitConfigService);
    this.scmClient = scmClient;
    this.gitSyncConnectorHelper = gitSyncConnectorHelper;
    this.decryptGitApiAccessHelper = decryptGitApiAccessHelper;
  }

  @Override
  public List<String> listBranchesForRepoByConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifierRef, String repoURL,
      io.harness.ng.beans.PageRequest pageRequest, String searchTerm) {
    final IdentifierRef gitConnectorIdentifierRef =
        getConnectorIdentifierRef(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifierRef);
    final ScmConnector connector = getScmConnector(gitConnectorIdentifierRef);
    ScmConnector decryptScmConnector =
        decryptGitApiAccessHelper.decryptScmApiAccess(connector, accountIdentifier, projectIdentifier, orgIdentifier);
    decryptScmConnector.setUrl(repoURL);
    return scmClient.listBranches(decryptScmConnector).getBranchesList();
  }

  @Override
  public GitFileContent getFileContent(String yamlGitConfigIdentifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String filePath, String branch, String commitId) {
    validateFileContentParams(branch, commitId);
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(
        yamlGitConfigIdentifier, projectIdentifier, orgIdentifier, accountIdentifier);
    final GitFilePathDetails gitFilePathDetails = getGitFilePathDetails(filePath, branch, commitId);
    final FileContent fileContent = scmClient.getFileContent(decryptedConnector, gitFilePathDetails);
    return validateAndGetGitFileContent(fileContent);
  }

  @Override
  public CreatePRDTO createPullRequest(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlGitConfigRef, GitPRCreateRequest gitCreatePRRequest) {
    // since project level ref = ref
    ScmConnector decryptScmConnector = gitSyncConnectorHelper.getDecryptedConnector(
        yamlGitConfigRef, projectIdentifier, orgIdentifier, accountIdentifier);
    CreatePRResponse createPRResponse;
    try {
      createPRResponse = scmClient.createPullRequest(decryptScmConnector, gitCreatePRRequest);
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(createPRResponse.getStatus(),
          String.format("Could not create the pull request from %s to %s", gitCreatePRRequest.getSourceBranch(),
              gitCreatePRRequest.getTargetBranch()));
    } catch (Exception ex) {
      throw new ScmException(PR_CREATION_ERROR);
    }
    return CreatePRDTO.builder().prNumber(createPRResponse.getNumber()).build();
  }

  @Override
  public List<GitFileChangeDTO> listFilesOfBranches(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlGitConfigIdentifier, List<String> foldersList, String branchName) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(
        yamlGitConfigIdentifier, projectIdentifier, orgIdentifier, accountIdentifier);
    FileBatchContentResponse filesList = scmClient.listFiles(decryptedConnector, foldersList, branchName);
    return FileBatchResponseMapper.createGitFileChangeList(filesList);
  }

  @Override
  public List<GitFileChangeDTO> listFilesByFilePaths(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String branchName) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigDTO, yamlGitConfigDTO.getAccountIdentifier());
    FileBatchContentResponse filesList = scmClient.listFilesByFilePaths(decryptedConnector, filePaths, branchName);
    return FileBatchResponseMapper.createGitFileChangeList(filesList);
  }

  @Override
  public GitDiffResultFileListDTO listCommitsDiffFiles(
      YamlGitConfigDTO yamlGitConfigDTO, String initialCommitId, String finalCommitId) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigDTO, yamlGitConfigDTO.getAccountIdentifier());
    CompareCommitsResponse compareCommitsResponse =
        scmClient.compareCommits(decryptedConnector, initialCommitId, finalCommitId);
    return PRFileListMapper.toGitDiffResultFileListDTO(compareCommitsResponse.getFilesList());
  }
}
