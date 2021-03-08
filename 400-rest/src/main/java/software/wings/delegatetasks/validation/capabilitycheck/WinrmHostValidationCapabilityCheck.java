package software.wings.delegatetasks.validation.capabilitycheck;

import static software.wings.common.Constants.WINDOWS_HOME_DIR;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.CapabilityResponse.CapabilityResponseBuilder;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.WinRmConnectionAttributes;
import software.wings.core.winrm.executors.WinRmSession;
import software.wings.core.winrm.executors.WinRmSessionConfig;
import software.wings.delegatetasks.validation.capabilities.BasicValidationInfo;
import software.wings.delegatetasks.validation.capabilities.WinrmHostValidationCapability;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.jcraft.jsch.JSchException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class WinrmHostValidationCapabilityCheck implements CapabilityCheck {
  @Inject private EncryptionService encryptionService;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    WinrmHostValidationCapability capability = (WinrmHostValidationCapability) delegateCapability;
    CapabilityResponseBuilder capabilityResponseBuilder = CapabilityResponse.builder().delegateCapability(capability);
    WinRmConnectionAttributes connectionAttributes = capability.getWinRmConnectionAttributes();
    List<EncryptedDataDetail> encryptedDataDetails = capability.getWinrmConnectionEncryptedDataDetails();
    encryptionService.decrypt(connectionAttributes, encryptedDataDetails, false);

    WinRmSessionConfig config =
        winrmSessionConfig(capability.getValidationInfo(), connectionAttributes, capability.getEnvVariables());
    log.info("Validating Winrm Session to Host: {}, Port: {}, useSsl: {}", config.getHostname(), config.getPort(),
        config.isUseSSL());

    try (WinRmSession ignore = makeSession(config, new NoopExecutionCallback())) {
      capabilityResponseBuilder.validated(true);
    } catch (Exception e) {
      log.info("Exception in WinrmSession Validation: {}", e);
      capabilityResponseBuilder.validated(false);
    }
    return capabilityResponseBuilder.build();
  }

  private WinRmSessionConfig winrmSessionConfig(BasicValidationInfo validationInfo,
      WinRmConnectionAttributes winrmConnectionAttributes, Map<String, String> envVariables) {
    return WinRmSessionConfig.builder()
        .accountId(validationInfo.getAccountId())
        .appId(validationInfo.getAppId())
        .executionId(validationInfo.getActivityId())
        .commandUnitName("HOST_CONNECTION_TEST")
        .hostname(validationInfo.getPublicDns())
        .authenticationScheme(winrmConnectionAttributes.getAuthenticationScheme())
        .domain(winrmConnectionAttributes.getDomain())
        .username(winrmConnectionAttributes.getUsername())
        .password(winrmConnectionAttributes.isUseKeyTab() ? StringUtils.EMPTY
                                                          : String.valueOf(winrmConnectionAttributes.getPassword()))
        .port(winrmConnectionAttributes.getPort())
        .useSSL(winrmConnectionAttributes.isUseSSL())
        .skipCertChecks(winrmConnectionAttributes.isSkipCertChecks())
        .useKeyTab(winrmConnectionAttributes.isUseKeyTab())
        .keyTabFilePath(winrmConnectionAttributes.getKeyTabFilePath())
        .workingDirectory(WINDOWS_HOME_DIR)
        .environment(envVariables == null ? Collections.emptyMap() : envVariables)
        .useNoProfile(winrmConnectionAttributes.isUseNoProfile())
        .build();
  }

  @VisibleForTesting
  WinRmSession makeSession(WinRmSessionConfig config, LogCallback logCallback) throws JSchException {
    return new WinRmSession(config, logCallback);
  }
}
