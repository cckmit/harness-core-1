package io.harness.kustomize;

import io.harness.cli.CliResponse;
import io.harness.logging.LogCallback;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

public interface KustomizeClient {
  @Nonnull
  CliResponse build(@Nonnull String manifestFilesDirectory, @Nonnull String kustomizeDirPath,
      @Nonnull String kustomizeBinaryPath, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse buildWithPlugins(@Nonnull String manifestFilesDirectory, @Nonnull String kustomizeDirPath,
      @Nonnull String kustomizeBinaryPath, @Nonnull String pluginPath, @Nonnull LogCallback callback)
      throws InterruptedException, TimeoutException, IOException;
}
