package software.wings.graphql.datafetcher;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.logging.ExceptionLogger;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.instance.dashboard.EntitySummary;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLDataPoint;
import software.wings.graphql.schema.type.aggregation.QLReference;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesAggregation;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.service.impl.instance.DashboardStatisticsServiceImpl.FlatEntitySummaryStats;
import software.wings.service.intfc.HarnessTagService;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public abstract class AbstractStatsDataFetcher<A, F, G, S> implements DataFetcher, BaseStatsDataFetcher {
  private static final String EXCEPTION_MSG_DELIMITER = ";; ";
  private static final String AGGREGATE_FUNCTION = "aggregateFunction";
  private static final String FILTERS = "filters";
  private static final String GROUP_BY = "groupBy";
  private static final String SORT_CRITERIA = "sortCriteria";
  protected static final String GENERIC_EXCEPTION_MSG =
      "An error has occurred. Please contact the Harness support team.";

  @Inject protected DataFetcherUtils utils;
  @Inject protected HarnessTagService tagService;
  @Inject protected NameService nameService;
  @Inject protected WingsPersistence wingsPersistence;
  public static final int MAX_RETRY = 3;

  protected abstract QLData fetch(
      String accountId, A aggregateFunction, List<F> filters, List<G> groupBy, List<S> sort);

  protected abstract QLData postFetch(String accountId, List<G> groupByList, QLData qlData);

  @Override
  public final Object get(DataFetchingEnvironment dataFetchingEnvironment) {
    Object result;
    try {
      Type[] typeArguments = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments();
      Class<A> aggregationFuncClass = (Class<A>) typeArguments[0];
      Class<F> filterClass = (Class<F>) typeArguments[1];
      Class<G> groupByClass = (Class<G>) typeArguments[2];
      Class<S> sortClass = (Class<S>) typeArguments[3];

      final A aggregateFunction = (A) fetchObject(dataFetchingEnvironment, AGGREGATE_FUNCTION, aggregationFuncClass);
      final List<F> filters = (List<F>) fetchObject(dataFetchingEnvironment, FILTERS, filterClass);
      final List<G> groupBy = (List<G>) fetchObject(dataFetchingEnvironment, GROUP_BY, groupByClass);
      final List<S> sort = (List<S>) fetchObject(dataFetchingEnvironment, SORT_CRITERIA, sortClass);
      String accountId = utils.getAccountId(dataFetchingEnvironment);
      QLData qlData = fetch(accountId, aggregateFunction, filters, groupBy, sort);
      QLData postFetchResult = postFetch(accountId, groupBy, qlData);
      if (postFetchResult == null) {
        result = qlData;
      } else {
        result = postFetchResult;
      }
    } catch (WingsException ex) {
      throw new InvalidRequestException(getCombinedErrorMessages(ex), ex, ex.getReportTargets());
    } catch (Exception ex) {
      throw new InvalidRequestException(GENERIC_EXCEPTION_MSG, ex, WingsException.USER_SRE);
    }
    return result;
  }

  private String getCombinedErrorMessages(WingsException ex) {
    List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(ex, ReportTarget.GRAPHQL_API);
    return responseMessages.stream().map(rm -> rm.getMessage()).collect(Collectors.joining(EXCEPTION_MSG_DELIMITER));
  }

  private <O> Object fetchObject(DataFetchingEnvironment dataFetchingEnvironment, String fieldName, Class<O> klass) {
    Object object = dataFetchingEnvironment.getArguments().get(fieldName);
    if (object == null) {
      return null;
    }

    if (object instanceof Collection) {
      Collection returnCollection = Lists.newArrayList();
      Collection collection = (Collection) object;
      collection.forEach(item -> returnCollection.add(convertToObject(item, klass)));
      return returnCollection;
    }
    return convertToObject(object, klass);
  }

  private <O> O convertToObject(Object fromValue, Class<O> klass) {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(fromValue, klass);
  }

  protected String getAccountId(DataFetchingEnvironment environment) {
    GraphQLContext context = environment.getContext();
    String accountId = context.get("accountId");

    if (isEmpty(accountId)) {
      throw new WingsException("accountId is null in the environment");
    }

    return accountId;
  }

  protected QLDataPoint getDataPoint(FlatEntitySummaryStats stats, String entityType) {
    QLReference qlReference =
        QLReference.builder().type(entityType).name(stats.getEntityName()).id(stats.getEntityId()).build();
    return QLDataPoint.builder().value(stats.getCount()).key(qlReference).build();
  }

  public String getGroupByTimeQuery(QLTimeSeriesAggregation groupByTime, String dbFieldName) {
    String unit;
    int value = groupByTime.getTimeAggregationValue();

    switch (groupByTime.getTimeAggregationType()) {
      case DAY:
        unit = "days";
        break;
      case HOUR:
        unit = "hours";
        break;
      default:
        logger.warn("Unsupported timeAggregationType " + groupByTime.getTimeAggregationType());
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }

    return new StringBuilder("time_bucket('")
        .append(value)
        .append(' ')
        .append(unit)
        .append("',")
        .append(dbFieldName)
        .append(')')
        .toString();
  }

  public String getGroupByTimeQueryWithGapFill(
      QLTimeSeriesAggregation groupByTime, String dbFieldName, String from, String to) {
    String unit = "days";
    int value = groupByTime.getTimeAggregationValue();

    switch (groupByTime.getTimeAggregationType()) {
      case DAY:
        unit = "days";
        break;
      case HOUR:
        unit = "hours";
        break;
      default:
        logger.warn("Unsupported timeAggregationType " + groupByTime.getTimeAggregationType());
        throw new InvalidRequestException(GENERIC_EXCEPTION_MSG);
    }

    return new StringBuilder("time_bucket_gapfill('")
        .append(value)
        .append(' ')
        .append(unit)
        .append("',")
        .append(dbFieldName)
        .append(",'")
        .append(from)
        .append("','")
        .append(to)
        .append("')")
        .toString();
  }

  public void generateSqlInQuery(StringBuilder queryBuilder, Object[] values) {
    if (isEmpty(values)) {
      throw new WingsException("Filter should have at least one value");
    }

    boolean isString = values instanceof String[];

    for (Object value : values) {
      if (isString) {
        queryBuilder.append('\'');
      }
      queryBuilder.append(value);
      if (isString) {
        queryBuilder.append('\'');
      }
      queryBuilder.append(',');
    }
    queryBuilder.deleteCharAt(queryBuilder.length() - 1);
  }

  @Value
  @Builder
  public static class TwoLevelAggregatedData {
    private EntitySummary firstLevelInfo;
    private EntitySummary secondLevelInfo;
    private long count;
  }
}
