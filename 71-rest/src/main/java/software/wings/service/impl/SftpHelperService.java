package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static software.wings.common.Constants.ARTIFACT_FILE_NAME;
import static software.wings.common.Constants.ARTIFACT_PATH;
import static software.wings.common.Constants.PARENT;
import static software.wings.common.Constants.PATH;
import static software.wings.common.Constants.URL;
import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import com.google.api.client.util.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.SftpConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.security.encryption.EncryptedDataDetail;
import software.wings.service.intfc.security.EncryptionService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class SftpHelperService {
  private static final Logger logger = LoggerFactory.getLogger(SftpHelperService.class);
  private static final String ROOT_DIR_ARTIFACT_PATH = ".";
  @Inject private EncryptionService encryptionService;

  public List<String> getSftpPaths(SftpConfig sftpConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(sftpConfig, encryptionDetails);
    List<String> artifactPaths = new ArrayList<>();

    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      ssh.loadKnownHosts();
      ssh.connect(getSFTPConnectionHost(sftpConfig.getSftpUrl()));
      ssh.authPassword(sftpConfig.getUsername(), sftpConfig.getPassword());

      try (SFTPClient sftp = ssh.newSFTPClient()) {
        List<RemoteResourceInfo> resourceInfos = Collections.EMPTY_LIST;
        // Get artifact paths
        resourceInfos = sftp.ls(ROOT_DIR_ARTIFACT_PATH);
        for (RemoteResourceInfo resourceInfo : resourceInfos) {
          artifactPaths.add(resourceInfo.getName());
        }
      } finally {
        logger.info("Closing SFTP connection :{}", sftpConfig.getSftpUrl());
      }
    } catch (IOException e) {
      logger.error(
          "SFTP server {} could not be reached. Exception Message {}", sftpConfig.getSftpUrl(), e.getMessage());
    } finally {
      logger.info("Closing SSH connection for SFTP URL :{}", sftpConfig.getSftpUrl());
    }

    return artifactPaths;
  }

  public String getSFTPConnectionHost(String sftpUrl) {
    String sftpHost = sftpUrl;
    if (sftpHost.contains("/")) {
      sftpHost = sftpHost.replaceFirst("^(sftp?://)", "").split(Pattern.quote("/"))[0];
    } else if (sftpHost.contains("\\")) {
      String[] split = sftpHost.replaceFirst("^(sftp?:\\\\)", "").split(Pattern.quote("\\"));
      sftpHost = split[1];
    }
    return sftpHost;
  }

  public boolean isConnectibleSFTPServer(String sftpUrl) {
    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      ssh.loadKnownHosts();
      ssh.connect(getSFTPConnectionHost(sftpUrl));
      return true;
    } catch (IOException e) {
      logger.error("SFTP server {} could not be reached. Exception Message {}", sftpUrl, e.getMessage());
    }
    return false;
  }

  public List<BuildDetails> getArtifactDetails(SftpConfig sftpConfig, List<EncryptedDataDetail> encryptionDetails,
      List<String> artifactPaths) throws IOException {
    List<BuildDetails> buildDetailsList = Lists.newArrayList();
    encryptionService.decrypt(sftpConfig, encryptionDetails);

    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      ssh.loadKnownHosts();
      ssh.connect(getSFTPConnectionHost(sftpConfig.getSftpUrl()));
      ssh.authPassword(sftpConfig.getUsername(), sftpConfig.getPassword());
      final SFTPClient sftp = ssh.newSFTPClient();

      List<RemoteResourceInfo> resourceInfos = Collections.EMPTY_LIST;
      // Get artifact details for each path
      try {
        for (String artifactPath : artifactPaths) {
          Path path = Paths.get(artifactPath);
          if (path != null) {
            Path ph = path.getFileName();
            String fileName = ph != null ? ph.toString() : "";
            Path parent = path.getParent();
            String directory = parent != null ? parent.toString() : "";
            // if directory is empty, default is HOME
            if (isEmpty(directory)) {
              directory = ROOT_DIR_ARTIFACT_PATH;
            }

            // Check if artifact is file or folder
            String artifactFullPath = directory + "\\" + artifactPath;
            FileAttributes attributes = sftp.lstat(artifactFullPath);

            if ("REGULAR".equals(attributes.getMode().getType().toString())) {
              resourceInfos = sftp.ls(directory, RemoteResourceInfo::isRegularFile);
            } else if ("DIRECTORY".equals(attributes.getMode().getType().name())) {
              resourceInfos = sftp.ls(directory, RemoteResourceInfo::isDirectory);
            }
            resourceInfos = resourceInfos.stream()
                                .filter(resourceInfo -> resourceInfo.getName().startsWith(fileName))
                                .collect(Collectors.toList());
          }

          List<BuildDetails> buildDetailsListForArtifactPath = Lists.newArrayList();
          for (RemoteResourceInfo resourceInfo : resourceInfos) {
            Map<String, String> map = new HashMap<>();
            map.put(ARTIFACT_PATH, artifactPath);
            map.put(URL, sftpConfig.getSftpUrl());
            map.put(ARTIFACT_FILE_NAME, resourceInfo.getName());
            map.put(PATH, resourceInfo.getPath());
            map.put(PARENT, resourceInfo.getParent());
            buildDetailsListForArtifactPath.add(aBuildDetails()
                                                    .withNumber(resourceInfo.getName())
                                                    .withArtifactPath(artifactPath)
                                                    .withBuildUrl(sftpConfig.getSftpUrl())
                                                    .withBuildParameters(map)
                                                    .build());
          }
          buildDetailsList.addAll(buildDetailsListForArtifactPath);
        }
      } finally {
        logger.info("Closing SFTP connection :{}", sftpConfig.getSftpUrl());
        sftp.close();
      }
    } catch (IOException e) {
      logger.error(
          "SFTP server {} could not be reached. Exception Message {}", sftpConfig.getSftpUrl(), e.getMessage());
    } finally {
      logger.info("Closing SSH connection for SFTP URL :{}", sftpConfig.getSftpUrl());
    }

    logger.info("SFTP server {} returned {} build details for artifact paths : ", sftpConfig.getSftpUrl(),
        buildDetailsList.size());
    return buildDetailsList;
  }
}