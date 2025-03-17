package com.wiley.cms.cochrane.activitylog;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:oarinosov@wiley.ru'>Oleg Arinosov</a>
 * @version 06.12.2006
 */
public interface IActivityLogService extends IActivityLog {

    Collection<ErrorLogEntity> getErrorLogs(List<Integer> eventIds, List<Integer> entityIds, int fullIssueNumb,
        int start, int bath);

    Collection<ErrorLogEntity> getLatestErrorLogs(List<Integer> eventIds, List<Integer> entityIds, int fullIssueNumb);

    Map<String, ErrorLogEntity> getLatestErrorLogs(int dbType, int fullIssueNumber);

    List<ActivityLogEntity> getActivities(Collection<Integer> eventIds, Collection<Integer> objectIds,
        int start, int bath);

    List<ActivityLogEntity> getActivityLogs(RequestParameters params);

    long getActivityLogsCount(RequestParameters params);

    long getActivityLogsCount();

    String getLastCommentsByEvent(int eventId);

    List<ActivityLogEventVO> getActivityLogEventVos();

    List<FlowLogEntity> getFlowLogs(Integer dfId);

    FlowLogEntity getFlowLog(Long flowLogId);

    FlowLogEntity findLastFlowLog(String entityName, int dbType, Date date);

    FlowLogEntity setFlowLogCompleted(Long flowLogId, int toSend);

    List<FlowLogEntity> getFlowLogsUncompleted(int maxSize);

    /**
     * Returns entity name by it id and level from ActivityLogEntity table
     * @param entityId entity id
     * @param entityLvl entity level
     * @return entity name or null if entity wasn't found
     */
    String getEntityName(int entityId, ActivityLogEntity.EntityLevel entityLvl);
}
