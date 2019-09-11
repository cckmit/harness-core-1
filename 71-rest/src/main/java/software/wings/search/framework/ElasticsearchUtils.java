package software.wings.search.framework;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.hibernate.validator.constraints.NotBlank;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@UtilityClass
public final class ElasticsearchUtils {
  public static String getIndexName(String type) {
    String INDEX_SUFFIX = "_idx";
    return type.concat(INDEX_SUFFIX);
  }

  public static BoolQueryBuilder createQuery(@NotBlank String searchString, @NotBlank String accountId) {
    BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();

    QueryBuilder queryBuilder = QueryBuilders.disMaxQuery()
                                    .add(QueryBuilders.matchPhrasePrefixQuery("name", searchString).boost(5))
                                    .add(QueryBuilders.matchPhraseQuery("description", searchString))
                                    .tieBreaker(0.7f);

    boolQueryBuilder.must(queryBuilder).filter(QueryBuilders.termQuery("accountId", accountId));
    return boolQueryBuilder;
  }

  public static boolean upsertDocument(RestHighLevelClient client, String type, String id, String jsonString) {
    String indexName = getIndexName(type);
    UpdateRequest updateRequest = new UpdateRequest(indexName, id);
    updateRequest.doc(jsonString, XContentType.JSON);
    updateRequest.retryOnConflict(3);
    updateRequest.docAsUpsert(true);
    try {
      UpdateResponse updateResponse = client.update(updateRequest, RequestOptions.DEFAULT);
      if (updateResponse.getResult() == Result.CREATED || updateResponse.getResult() == Result.UPDATED) {
        return true;
      }
      logger.error(String.format(
          "The upsert operation on index %s with document %s did not affect any document", indexName, jsonString));
    } catch (ElasticsearchException e) {
      logger.error(String.format("Error while updating document %s in index %s", jsonString, indexName), e);
    } catch (IOException e) {
      logger.error("Could not connect to elasticsearch", e);
    }
    return false;
  }

  public static boolean updateKeyInMultipleDocuments(RestHighLevelClient client, String type, String keyToUpdate,
      String newValue, String filterKey, String filterValue) {
    String indexName = getIndexName(type);
    UpdateByQueryRequest request = new UpdateByQueryRequest(indexName);
    request.setConflicts("proceed");
    //    request.setQuery(new TermQueryBuilder(filterKey, filterValue));
    Map<String, Object> params = new HashMap<>();
    params.put("keyToUpdate", keyToUpdate);
    params.put("newValue", newValue);
    params.put("filterKey", filterKey);
    params.put("filterValue", filterValue);
    request.setScript(new Script(ScriptType.INLINE, "painless",
        "if (ctx._source[params.filterKey] == params.filterValue) {ctx._source[params.keyToUpdate] = params.newValue;}",
        params));
    try {
      BulkByScrollResponse bulkResponse = client.updateByQuery(request, RequestOptions.DEFAULT);
      if (bulkResponse.getSearchFailures().isEmpty() && bulkResponse.getBulkFailures().isEmpty()) {
        if (bulkResponse.getUpdated() == 0) {
          logger.warn(
              String.format("No documents were updated with params %s in index %s", params.toString(), indexName));
        }
        return true;
      }
      logger.error(String.format("Failed to update index %s by query with params %s", indexName, params.toString()));
    } catch (IOException e) {
      logger.error("Could not connect to elasticsearch", e);
    }
    return false;
  }

  public static boolean deleteDocument(RestHighLevelClient client, String type, String id) {
    String indexName = getIndexName(type);
    DeleteRequest deleteRequest = new DeleteRequest(indexName, id);
    try {
      DeleteResponse deleteResponse = client.delete(deleteRequest, RequestOptions.DEFAULT);
      if (deleteResponse.getResult() == Result.DELETED) {
        return true;
      }
      logger.error(String.format("Could not delete document %s in index %s", id, indexName));
    } catch (ElasticsearchException e) {
      logger.error(String.format("Error while trying to delete document %s in index %s", id, indexName), e);
    } catch (IOException e) {
      logger.error("Could not connect to elasticsearch", e);
    }
    return false;
  }
}