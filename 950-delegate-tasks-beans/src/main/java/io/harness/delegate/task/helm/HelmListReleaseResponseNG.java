package io.harness.delegate.task.helm;

import io.harness.logging.CommandExecutionStatus;

import software.wings.helpers.ext.helm.response.ReleaseInfo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
public class HelmListReleaseResponseNG extends HelmCommandResponseNG {
  List<ReleaseInfo> releaseInfoList;
  @Builder
  public HelmListReleaseResponseNG(
      CommandExecutionStatus commandExecutionStatus, String output, List<ReleaseInfo> releaseInfoList) {
    super(commandExecutionStatus, output);
    this.releaseInfoList = releaseInfoList;
  }
}