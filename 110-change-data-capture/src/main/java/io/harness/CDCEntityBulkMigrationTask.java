package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeType;
import io.harness.persistence.PersistentEntity;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.concurrent.Callable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Strings;
import org.bson.Document;
import org.bson.types.ObjectId;

@OwnedBy(CE)
@AllArgsConstructor
@Slf4j
public class CDCEntityBulkMigrationTask<T extends PersistentEntity> implements Callable<Boolean> {
  private ChangeHandler changeHandler;
  private Class entityType;
  private Document document;
  private String tableName;
  private String[] fields;

  @Override
  public Boolean call() throws Exception {
    DBObject dbObject = toDBObject(document);

    ChangeEvent changeEvent = ChangeEvent.builder()
                                  .fullDocument(dbObject)
                                  .changeType(ChangeType.INSERT)
                                  .entityType(entityType)
                                  .uuid(getUuidFromDocument(document))
                                  .build();
    return changeHandler.handleChange(changeEvent, Strings.toLowerCase(tableName), fields);
  }

  public static DBObject toDBObject(Document document) {
    return BasicDBObject.parse(document.toJson());
  }

  private String getUuidFromDocument(Document document) {
    if (document.get("_id") instanceof String) {
      return (String) document.get("_id");
    }
    return ((ObjectId) document.get("_id")).toString();
  }
}
