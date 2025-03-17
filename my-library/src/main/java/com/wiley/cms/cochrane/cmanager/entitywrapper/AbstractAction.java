package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public abstract class AbstractAction implements Action {
    private static IActivityLogService logService = null;

    private boolean commentRequested = false;
    private boolean confirmable = false;
    private String confirmMessage = CochraneCMSProperties.getProperty("confirm.perform");

    public boolean isCommentRequested() {
        return commentRequested;
    }

    public void setCommentRequested(boolean value) {
        commentRequested = value;
    }

    public boolean isConfirmable() {
        return confirmable;
    }

    public void setConfirmable(boolean value) {
        confirmable = value;
    }

    public String getConfirmMessage() {
        if (!isConfirmable()) {
            throw new IllegalStateException("Action must be confirmable");
        }
        return confirmMessage;
    }

    public void setConfirmMessage(String value) {
        confirmMessage = value;
    }

    protected void logAction(IVisit visit, ActivityLogEntity.EntityLevel level, int event, int entityId,
                             String entityName) {
        logAction(visit, level, event, entityId, entityName, null);
    }

    protected void logAction(IVisit visit, ActivityLogEntity.EntityLevel level, int event, int entityId,
                             String entityName, String comments) {
        String username = (visit != null) ? visit.getLogin() : "";
        getActivityLog().info(level, event, entityId, entityName, username, comments);
    }

    protected static IActivityLogService getActivityLog() {
        if (logService == null) {
            logService = AbstractManager.getActivityLogService();
        }
        return logService;
    }

    public void perform() {
        perform(null);
    }

}
