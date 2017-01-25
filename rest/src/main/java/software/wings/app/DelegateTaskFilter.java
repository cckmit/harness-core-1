package software.wings.app;

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcastFilter.BroadcastAction.ACTION;
import org.atmosphere.cpr.BroadcastFilterAdapter;
import software.wings.beans.DelegateTask;
import software.wings.service.intfc.DelegateService;
import software.wings.utils.KryoUtils;

import javax.inject.Inject;

/**
 * Created by peeyushaggarwal on 1/23/17.
 */
public class DelegateTaskFilter extends BroadcastFilterAdapter {
  @Inject private DelegateService delegateService;

  @Override
  public BroadcastAction filter(String broadcasterId, AtmosphereResource r, Object originalMessage, Object message) {
    if (message.getClass() == DelegateTask.class) {
      String delegateId = r.getRequest().getParameter("delegateId");
      DelegateTask task = (DelegateTask) message;
      if (delegateService.filter(delegateId, task)) {
        return new BroadcastAction(KryoUtils.asString(message));
      } else {
        return new BroadcastAction(ACTION.ABORT, message);
      }
    }
    return new BroadcastAction(message);
  }
}
