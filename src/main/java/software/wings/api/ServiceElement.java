/**
 *
 */

package software.wings.api;

import software.wings.sm.ContextElement;
import software.wings.sm.ContextElementType;

import java.util.Map;

// TODO: Auto-generated Javadoc

/**
 * The Class ServiceElement.
 *
 * @author Rishi
 */
public class ServiceElement implements ContextElement {
  private String uuid;
  private String name;

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.SERVICE;
  }

  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  /* (non-Javadoc)
   * @see software.wings.sm.ContextElement#paramMap()
   */
  @Override
  public Map<String, Object> paramMap() {
    return null;
  }
}
