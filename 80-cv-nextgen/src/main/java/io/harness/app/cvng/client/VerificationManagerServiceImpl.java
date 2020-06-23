package io.harness.app.cvng.client;

import com.google.inject.Inject;

import java.io.IOException;

public class VerificationManagerServiceImpl implements VerificationManagerService {
  @Inject private VerificationManagerClient verificationManagerClient;
  @Override
  public String createDataCollectionTask(String accountId, String cvConfigId) {
    try {
      // Need to write this to handle retries, exception etc in a proper way.
      return verificationManagerClient.createDataCollectionTask(accountId, cvConfigId).execute().body().getResource();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
