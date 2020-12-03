package software.wings.settings.validation;

import io.harness.delegate.beans.executioncapability.AlwaysFalseValidationCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.delegatetasks.validation.capabilities.SmtpCapability;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.settings.SettingValue;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectivityValidationDelegateRequest implements ExecutionCapabilityDemander {
  private SettingAttribute settingAttribute;
  private List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities =
        CapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(encryptedDataDetails, maskingEvaluator);
    if (settingAttribute == null) {
      return executionCapabilities;
    }
    SettingValue settingValue = settingAttribute.getValue();
    if (settingValue instanceof HostConnectionAttributes) {
      SshConnectionConnectivityValidationAttributes validationAttributes =
          (SshConnectionConnectivityValidationAttributes) settingAttribute.getValidationAttributes();
      String hostName = validationAttributes.getHostName();
      Integer port = ((HostConnectionAttributes) settingValue).getSshPort();
      executionCapabilities.add(
          SocketConnectivityCapabilityGenerator.buildSocketConnectivityCapability(hostName, Integer.toString(port)));
      return executionCapabilities;
    } else if (settingValue instanceof WinRmConnectionAttributes) {
      WinRmConnectivityValidationAttributes validationAttributes =
          (WinRmConnectivityValidationAttributes) settingAttribute.getValidationAttributes();
      String hostName = validationAttributes.getHostName();
      int port = ((WinRmConnectionAttributes) settingValue).getPort();
      executionCapabilities.add(
          SocketConnectivityCapabilityGenerator.buildSocketConnectivityCapability(hostName, Integer.toString(port)));
      return executionCapabilities;
    } else if (settingValue instanceof SmtpConfig) {
      executionCapabilities.add(SmtpCapability.builder()
                                    .smtpConfig((SmtpConfig) settingValue)
                                    .encryptionDetails(encryptedDataDetails)
                                    .build());
      return executionCapabilities;
    } else {
      executionCapabilities.add(AlwaysFalseValidationCapability.builder().build());
      return executionCapabilities;
    }
  }
}
