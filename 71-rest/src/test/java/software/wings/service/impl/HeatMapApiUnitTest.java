package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.common.VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;

import com.google.inject.Inject;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Service;
import software.wings.common.VerificationConstants;
import software.wings.dl.WingsPersistence;
import software.wings.resources.CVConfigurationResource;
import software.wings.security.encryption.EncryptionUtils;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMLHostSummary;
import software.wings.service.impl.analysis.TimeSeriesMLMetricSummary;
import software.wings.service.impl.analysis.TimeSeriesMLTxnSummary;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.HeatMap;
import software.wings.verification.TimeSeriesDataPoint;
import software.wings.verification.TimeSeriesOfMetric;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Vaibhav Tulsyan
 * 19/Oct/2018
 */
public class HeatMapApiUnitTest extends WingsBaseTest {
  private static final Logger logger = LoggerFactory.getLogger(HeatMapApiUnitTest.class);

  @Inject WingsPersistence wingsPersistence;
  @Inject ContinuousVerificationService continuousVerificationService;
  @Inject private CVConfigurationResource cvConfigurationResource;

  private String accountId;
  private String appId;
  private String serviceId;
  private String envId;
  private String envName;

  @Before
  public void setup() {
    Account account = anAccount().withAccountName(generateUUID()).build();

    account.setEncryptedLicenseInfo(
        EncryptionUtils.encrypt(LicenseUtil.convertToString(LicenseInfo.builder().accountType(AccountType.PAID).build())
                                    .getBytes(Charset.forName("UTF-8")),
            null));
    accountId = wingsPersistence.save(account);
    appId = wingsPersistence.save(anApplication().withAccountId(accountId).withName(generateUUID()).build());
    envName = generateUuid();
    serviceId = wingsPersistence.save(Service.builder().appId(appId).name(generateUuid()).build());
    envId = wingsPersistence.save(anEnvironment().withAppId(appId).withName(envName).build());
  }

  @Test
  public void testNoAnalysisRecords() {
    NewRelicCVServiceConfiguration cvServiceConfiguration =
        NewRelicCVServiceConfiguration.builder().applicationId(generateUUID()).build();
    cvServiceConfiguration.setName(generateUUID());
    cvServiceConfiguration.setConnectorId(generateUUID());
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    cvConfigurationResource.saveCVConfiguration(accountId, appId, StateType.NEW_RELIC, cvServiceConfiguration)
        .getResource();

    // ask for last 6 hours
    long hoursToAsk = 6;
    long endTime = System.currentTimeMillis();
    List<HeatMap> heatMaps = continuousVerificationService.getHeatMap(
        accountId, appId, serviceId, endTime - TimeUnit.HOURS.toMillis(hoursToAsk), endTime, false);

    assertEquals(1, heatMaps.size());

    HeatMap heatMapSummary = heatMaps.get(0);
    assertEquals(TimeUnit.HOURS.toMinutes(hoursToAsk) / CRON_POLL_INTERVAL_IN_MINUTES,
        heatMapSummary.getRiskLevelSummary().size());
    heatMapSummary.getRiskLevelSummary().forEach(riskLevel -> {
      assertEquals(0, riskLevel.getHighRisk());
      assertEquals(0, riskLevel.getMediumRisk());
      assertEquals(0, riskLevel.getLowRisk());
      assertEquals(1, riskLevel.getNa());
    });
  }

  @Test
  public void testNoMergingWithoutGap() {
    NewRelicCVServiceConfiguration cvServiceConfiguration =
        NewRelicCVServiceConfiguration.builder().applicationId(generateUUID()).build();
    cvServiceConfiguration.setName(generateUUID());
    cvServiceConfiguration.setConnectorId(generateUUID());
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    String cvConfigId =
        cvConfigurationResource.saveCVConfiguration(accountId, appId, StateType.NEW_RELIC, cvServiceConfiguration)
            .getResource();

    // ask for last 6 hours
    long hoursToAsk = 12;
    long endTime = System.currentTimeMillis();
    long endMinute = TimeUnit.MILLISECONDS.toMinutes(endTime);
    for (long analysisMinute = endMinute; analysisMinute > endMinute - TimeUnit.HOURS.toMinutes(hoursToAsk);
         analysisMinute -= CRON_POLL_INTERVAL_IN_MINUTES) {
      TimeSeriesMLAnalysisRecord analysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
      analysisRecord.setAppId(appId);
      analysisRecord.setCvConfigId(cvConfigId);
      analysisRecord.setAnalysisMinute((int) analysisMinute);

      Map<String, TimeSeriesMLTxnSummary> txnSummaryMap = new HashMap<>();
      TimeSeriesMLTxnSummary timeSeriesMLTxnSummary = new TimeSeriesMLTxnSummary();
      Map<String, TimeSeriesMLMetricSummary> timeSeriesMLMetricSummaryMap = new HashMap<>();
      timeSeriesMLTxnSummary.setMetrics(timeSeriesMLMetricSummaryMap);
      txnSummaryMap.put(generateUuid(), timeSeriesMLTxnSummary);

      TimeSeriesMLMetricSummary timeSeriesMLMetricSummary = new TimeSeriesMLMetricSummary();
      Map<String, TimeSeriesMLHostSummary> timeSeriesMLHostSummaryMap = new HashMap<>();
      timeSeriesMLMetricSummary.setResults(timeSeriesMLHostSummaryMap);
      timeSeriesMLMetricSummaryMap.put(generateUuid(), timeSeriesMLMetricSummary);
      timeSeriesMLHostSummaryMap.put(generateUuid(), TimeSeriesMLHostSummary.builder().risk(2).build());

      analysisRecord.setTransactions(txnSummaryMap);
      wingsPersistence.save(analysisRecord);
    }
    List<HeatMap> heatMaps = continuousVerificationService.getHeatMap(
        accountId, appId, serviceId, endTime - TimeUnit.HOURS.toMillis(hoursToAsk), endTime, false);

    assertEquals(1, heatMaps.size());

    HeatMap heatMapSummary = heatMaps.get(0);
    assertEquals(TimeUnit.HOURS.toMinutes(hoursToAsk) / CRON_POLL_INTERVAL_IN_MINUTES,
        heatMapSummary.getRiskLevelSummary().size());
    heatMapSummary.getRiskLevelSummary().forEach(riskLevel -> {
      assertEquals(1, riskLevel.getHighRisk());
      assertEquals(0, riskLevel.getMediumRisk());
      assertEquals(0, riskLevel.getLowRisk());
      assertEquals(0, riskLevel.getNa());
    });

    // ask for 35 mins before and 48 mins after
    heatMaps = continuousVerificationService.getHeatMap(accountId, appId, serviceId,
        endTime - TimeUnit.HOURS.toMillis(hoursToAsk) - TimeUnit.MINUTES.toMillis(35),
        endTime + TimeUnit.MINUTES.toMillis(48), false);

    assertEquals(1, heatMaps.size());

    heatMapSummary = heatMaps.get(0);

    // The inverval is > 12 hours, hence resolution is that of 24hrs
    // total small units should be 53, resolved units should be 26
    assertEquals(1 + ((5 + TimeUnit.HOURS.toMinutes(hoursToAsk) / CRON_POLL_INTERVAL_IN_MINUTES) / 2),
        heatMapSummary.getRiskLevelSummary().size());
    AtomicInteger index = new AtomicInteger();
    heatMapSummary.getRiskLevelSummary().forEach(riskLevel -> {
      if (index.get() == 0 || index.get() == 25) {
        assertEquals(0, riskLevel.getHighRisk());
        assertEquals(2, riskLevel.getNa());
      } else if (index.get() == 26) { // 53rd small unit
        assertEquals(1, riskLevel.getNa());
      } else {
        assertEquals(2, riskLevel.getHighRisk());
        assertEquals(0, riskLevel.getNa());
      }
      assertEquals(0, riskLevel.getMediumRisk());
      assertEquals(0, riskLevel.getLowRisk());
      index.incrementAndGet();
    });

    // create gaps in between and test
    // remove 5th, 6th and 34th from endMinute
    wingsPersistence.delete(wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class)
                                .filter("analysisMinute", endMinute - 5 * CRON_POLL_INTERVAL_IN_MINUTES));
    wingsPersistence.delete(wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class)
                                .filter("analysisMinute", endMinute - 6 * CRON_POLL_INTERVAL_IN_MINUTES));
    wingsPersistence.delete(wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class)
                                .filter("analysisMinute", endMinute - 34 * CRON_POLL_INTERVAL_IN_MINUTES));

    // ask for 35 mins before and 48 mins after
    heatMaps = continuousVerificationService.getHeatMap(accountId, appId, serviceId,
        endTime - TimeUnit.HOURS.toMillis(hoursToAsk) - TimeUnit.MINUTES.toMillis(35),
        endTime + TimeUnit.MINUTES.toMillis(48), false);
    assertEquals(1, heatMaps.size());

    heatMapSummary = heatMaps.get(0);

    assertEquals(1 + ((5 + TimeUnit.HOURS.toMinutes(hoursToAsk) / CRON_POLL_INTERVAL_IN_MINUTES) / 2),
        heatMapSummary.getRiskLevelSummary().size());
    index.set(0);
    heatMapSummary.getRiskLevelSummary().forEach(riskLevel -> {
      if (index.get() == 0 || index.get() == 25) {
        assertEquals(0, riskLevel.getHighRisk());
        assertEquals(2, riskLevel.getNa());
      } else if (index.get() == 26) { // 53rd small unit
        assertEquals(0, riskLevel.getHighRisk());
        assertEquals(1, riskLevel.getNa());
      } else if (index.get() == 7 || index.get() == 21 || index.get() == 22) {
        assertEquals(1, riskLevel.getHighRisk());
        assertEquals(1, riskLevel.getNa());
      } else {
        assertEquals(2, riskLevel.getHighRisk());
        assertEquals(0, riskLevel.getNa());
      }
      assertEquals(0, riskLevel.getMediumRisk());
      assertEquals(0, riskLevel.getLowRisk());
      index.incrementAndGet();
    });
  }

  @Test
  public void testTimeSeries() {
    final int DURATION_IN_HOURS = 12;

    CVConfiguration cvConfiguration = new CVConfiguration();
    cvConfiguration.setName(generateUuid());
    cvConfiguration.setAccountId(accountId);
    cvConfiguration.setAppId(appId);
    cvConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    cvConfiguration.setConnectorId(generateUuid());
    cvConfiguration.setConnectorName(generateUuid());
    cvConfiguration.setEnabled24x7(true);
    cvConfiguration.setEnvId(envId);
    cvConfiguration.setEnvName(envName);
    cvConfiguration.setServiceId(serviceId);
    cvConfiguration.setServiceName(generateUuid());
    cvConfiguration.setStateType(StateType.APP_DYNAMICS);
    cvConfiguration = wingsPersistence.saveAndGet(CVConfiguration.class, cvConfiguration);

    long endTime = System.currentTimeMillis();
    long start12HoursAgo = endTime - TimeUnit.HOURS.toMillis(DURATION_IN_HOURS);
    int startMinuteFor12Hrs = (int) TimeUnit.MILLISECONDS.toMinutes(start12HoursAgo);

    int endMinute = (int) TimeUnit.MILLISECONDS.toMinutes(endTime);

    // Generate data for 12 hours
    for (int analysisMinute = endMinute; analysisMinute >= startMinuteFor12Hrs;
         analysisMinute -= VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES) {
      saveTimeSeriesRecordToDb(analysisMinute, cvConfiguration); // analysis minute = end minute of analyis
    }

    long start15MinutesAgo = endTime - TimeUnit.MINUTES.toMillis(VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES);
    int startMinuteFor15Mins = (int) TimeUnit.MILLISECONDS.toMinutes(start15MinutesAgo + 1);
    int totalMinutes = endMinute - startMinuteFor15Mins;

    // First case: Fetch an exact block of 15 minutes
    testTSFor15Mins(endTime, startMinuteFor15Mins, totalMinutes, cvConfiguration);

    // Second case: Fetch last 5 data points from one record, all 15 from the next records
    testTSFor20Mins(endTime, cvConfiguration);

    // Third case: Fetch last 5 data points from one record, first 5 data points from next record
    testOverlappingQuery(endTime, cvConfiguration);
  }

  private void testOverlappingQuery(long endTime, CVConfiguration cvConfiguration) {
    Map<String, Map<String, TimeSeriesOfMetric>> timeSeries;
    Map<String, TimeSeriesOfMetric> metricMap;
    List<TimeSeriesDataPoint> dataPoints;
    long startEpoch20MinutesAgo = endTime - TimeUnit.MINUTES.toMillis(20);
    long endEpoch10MinutesAgo = endTime - TimeUnit.MINUTES.toMillis(10);

    int startMinuteFor20Mins = (int) TimeUnit.MILLISECONDS.toMinutes(startEpoch20MinutesAgo);
    int endMinuteFor10MinsAgo = (int) TimeUnit.MILLISECONDS.toMinutes(endEpoch10MinutesAgo);

    timeSeries = continuousVerificationService.fetchObservedTimeSeries(TimeUnit.MINUTES.toMillis(startMinuteFor20Mins),
        TimeUnit.MINUTES.toMillis(endMinuteFor10MinsAgo), cvConfiguration);
    assertTrue(timeSeries.containsKey("/login"));
    metricMap = timeSeries.get("/login");
    dataPoints = metricMap.get("95th Percentile Response Time (ms)").getTimeSeries();
    assertEquals(10, dataPoints.size());
  }

  private void testTSFor20Mins(long endTime, CVConfiguration cvConfiguration) {
    Map<String, Map<String, TimeSeriesOfMetric>> timeSeries;
    Map<String, TimeSeriesOfMetric> metricMap;
    List<TimeSeriesDataPoint> dataPoints;
    long start20MinutesAgo = endTime - TimeUnit.MINUTES.toMillis(20);
    int startMinuteFor20Mins = (int) TimeUnit.MILLISECONDS.toMinutes(start20MinutesAgo);

    timeSeries = continuousVerificationService.fetchObservedTimeSeries(
        TimeUnit.MINUTES.toMillis(startMinuteFor20Mins), endTime, cvConfiguration);
    assertTrue(timeSeries.containsKey("/login"));
    metricMap = timeSeries.get("/login");
    dataPoints = metricMap.get("95th Percentile Response Time (ms)").getTimeSeries();
    assertEquals(20, dataPoints.size());
  }

  @NotNull
  private void testTSFor15Mins(
      long endTime, int startMinuteFor15Mins, int totalMinutes, CVConfiguration cvConfiguration) {
    Map<String, Map<String, TimeSeriesOfMetric>> timeSeries;
    Map<String, TimeSeriesOfMetric> metricMap;
    List<TimeSeriesDataPoint> dataPoints;
    timeSeries = continuousVerificationService.fetchObservedTimeSeries(
        TimeUnit.MINUTES.toMillis(startMinuteFor15Mins), endTime, cvConfiguration);
    assertTrue(timeSeries.containsKey("/login"));

    metricMap = timeSeries.get("/login");
    assertTrue(metricMap.containsKey("95th Percentile Response Time (ms)"));

    dataPoints = metricMap.get("95th Percentile Response Time (ms)").getTimeSeries();
    assertEquals(totalMinutes, dataPoints.size());
  }

  private void saveTimeSeriesRecordToDb(int analysisMinute, CVConfiguration cvConfiguration) {
    final String DEFAULT_GROUP_NAME = "default";
    final String DEFAULT_RESULTS_KEY = "docker-test/tier";

    TimeSeriesMLAnalysisRecord record = TimeSeriesMLAnalysisRecord.builder().build();
    record.setAnalysisMinute(analysisMinute);
    record.setStateType(StateType.APP_DYNAMICS);
    record.setGroupName(DEFAULT_GROUP_NAME);
    record.setCvConfigId(cvConfiguration.getUuid());
    record.setAppId(appId);

    // transactions
    Map<String, TimeSeriesMLTxnSummary> txnMap = new HashMap<>();
    TimeSeriesMLTxnSummary txnSummary = new TimeSeriesMLTxnSummary();

    // txn => 0
    txnSummary.setTxn_name("/login");
    txnSummary.setGroup_name(DEFAULT_GROUP_NAME);

    // txn => 0 => metrics
    Map<String, TimeSeriesMLMetricSummary> metricSummaryMap = new HashMap<>();

    // txn => 0 => metrics => 0
    TimeSeriesMLMetricSummary metricSummary = new TimeSeriesMLMetricSummary();
    metricSummary.setMetric_name("95th Percentile Response Time (ms)");

    // txn => 0 => metrics => 0 => results
    Map<String, TimeSeriesMLHostSummary> results = new HashMap<>();

    // txn => 0 => metrics => 0 => results => docker-test/tier
    int risk = ThreadLocalRandom.current().nextInt(1, 3);
    List<Double> test_data = new ArrayList<>();
    for (int i = 0; i < VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES; i++) {
      test_data.add(ThreadLocalRandom.current().nextDouble(0, 30));
    }
    TimeSeriesMLHostSummary timeSeriesMLHostSummary =
        TimeSeriesMLHostSummary.builder().risk(risk).test_data(test_data).build();
    results.put(DEFAULT_RESULTS_KEY, timeSeriesMLHostSummary);

    // Set/put everything we have constructed so far
    metricSummary.setResults(results);
    metricSummaryMap.put("0", metricSummary);
    txnSummary.setMetrics(metricSummaryMap);
    txnSummary.setMetrics(metricSummaryMap);
    txnMap.put("0", txnSummary);
    record.setTransactions(txnMap);

    // Save to DB
    wingsPersistence.save(record);
  }
}
