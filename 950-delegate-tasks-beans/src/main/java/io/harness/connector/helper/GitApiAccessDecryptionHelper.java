package io.harness.connector.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(DX)
public class GitApiAccessDecryptionHelper {
  public DecryptableEntity getAPIAccessDecryptableEntity(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      return getAPIAccessDecryptableEntity((GithubConnectorDTO) scmConnector);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      return getAPIAccessDecryptableEntity((BitbucketConnectorDTO) scmConnector);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      return getAPIAccessDecryptableEntity((GitlabConnectorDTO) scmConnector);
    }
    throw new InvalidRequestException("Unsupported Scm Connector");
  }

  public boolean hasApiAccess(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      return hasAPIAccess((GithubConnectorDTO) scmConnector);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      return hasAPIAccess((BitbucketConnectorDTO) scmConnector);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      return hasAPIAccess((GitlabConnectorDTO) scmConnector);
    } else if (scmConnector instanceof GitConfigDTO) {
      return false;
    }
    throw new InvalidRequestException("Unsupported Scm Connector");
  }

  private boolean hasAPIAccess(GithubConnectorDTO githubConnectorDTO) {
    return !(githubConnectorDTO == null || githubConnectorDTO.getApiAccess() == null
        || githubConnectorDTO.getApiAccess().getSpec() == null);
  }

  private boolean hasAPIAccess(BitbucketConnectorDTO bitbucketConnectorDTO) {
    return !(bitbucketConnectorDTO == null || bitbucketConnectorDTO.getApiAccess() == null
        || bitbucketConnectorDTO.getApiAccess().getSpec() == null);
  }

  private boolean hasAPIAccess(GitlabConnectorDTO gitlabConnectorDTO) {
    return !(gitlabConnectorDTO == null || gitlabConnectorDTO.getApiAccess() == null
        || gitlabConnectorDTO.getApiAccess().getSpec() == null);
  }

  public DecryptableEntity getAPIAccessDecryptableEntity(GithubConnectorDTO githubConnectorDTO) {
    if (githubConnectorDTO == null || githubConnectorDTO.getApiAccess() == null) {
      throw new InvalidRequestException("The given connector doesn't have api access field set");
    }
    return githubConnectorDTO.getApiAccess().getSpec();
  }

  public DecryptableEntity getAPIAccessDecryptableEntity(BitbucketConnectorDTO bitbucketConnectorDTO) {
    if (bitbucketConnectorDTO == null || bitbucketConnectorDTO.getApiAccess() == null) {
      throw new InvalidRequestException("The given connector doesn't have api access field set");
    }
    return bitbucketConnectorDTO.getApiAccess().getSpec();
  }

  public DecryptableEntity getAPIAccessDecryptableEntity(GitlabConnectorDTO gitlabConnectorDTO) {
    if (gitlabConnectorDTO == null || gitlabConnectorDTO.getApiAccess() == null) {
      throw new InvalidRequestException("The given connector doesn't have api access field set");
    }
    return gitlabConnectorDTO.getApiAccess().getSpec();
  }

  public void setAPIAccessDecryptableEntity(ScmConnector scmConnector, DecryptableEntity decryptableEntity) {
    if (scmConnector instanceof GithubConnectorDTO) {
      setAPIAccessDecryptableEntity((GithubConnectorDTO) scmConnector, decryptableEntity);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      setAPIAccessDecryptableEntity((BitbucketConnectorDTO) scmConnector, decryptableEntity);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      setAPIAccessDecryptableEntity((GitlabConnectorDTO) scmConnector, decryptableEntity);
    }
  }

  public void setAPIAccessDecryptableEntity(
      GithubConnectorDTO githubConnectorDTO, DecryptableEntity decryptableEntity) {
    if (githubConnectorDTO == null || githubConnectorDTO.getApiAccess() == null) {
      throw new InvalidRequestException("The given connector doesn't have api access field set");
    }
    githubConnectorDTO.getApiAccess().setSpec((GithubApiAccessSpecDTO) decryptableEntity);
  }

  public void setAPIAccessDecryptableEntity(
      BitbucketConnectorDTO bitbucketConnectorDTO, DecryptableEntity decryptableEntity) {
    if (bitbucketConnectorDTO == null || bitbucketConnectorDTO.getApiAccess() == null) {
      throw new InvalidRequestException("The given connector doesn't have api access field set");
    }
    bitbucketConnectorDTO.getApiAccess().setSpec((BitbucketApiAccessSpecDTO) decryptableEntity);
  }

  public void setAPIAccessDecryptableEntity(
      GitlabConnectorDTO gitlabConnectorDTO, DecryptableEntity decryptableEntity) {
    if (gitlabConnectorDTO == null || gitlabConnectorDTO.getApiAccess() == null) {
      throw new InvalidRequestException("The given connector doesn't have api access field set");
    }
    gitlabConnectorDTO.getApiAccess().setSpec((GitlabApiAccessSpecDTO) decryptableEntity);
  }
}
