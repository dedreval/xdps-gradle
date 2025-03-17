package com.wiley.cms.cochrane.activitylog;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.RecordLightVO;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Logger;

import org.apache.commons.lang.StringUtils;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:oarinosov@wiley.ru'>Oleg Arinosov</a>
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 06.12.2006
 */
@Stateless
@Local({IActivityLogService.class})
public class ActivityLogService implements IActivityLogService, Serializable {
    public static final String COCHRANE_SFTP = "Cochrane SFTP";
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ActivityLogService.class);

    @PersistenceContext
    private EntityManager manager;

    private ActivityLogEntity createMessage(ActivityLogEntity.LogLevel logLevel,
                                            ActivityLogEntity.EntityLevel entityLevel,
                                            int eventId,
                                            int entityId,
                                            String entityName,
                                            String who,
                                            String comments) {
        ActivityLogEntity activityLogEntity = new ActivityLogEntity();

        initLogEntity(activityLogEntity, logLevel, entityLevel, entityId, entityName, comments);

        activityLogEntity.setEvent(manager.find(ActivityLogEventEntity.class, eventId));
        activityLogEntity.setWho(who);
        return activityLogEntity;
    }

    private ActivityLogEntity addMessage(ActivityLogEntity.LogLevel logLevel,
                                         ActivityLogEntity.EntityLevel entityLevel,
                                         int eventId,
                                         int entityId,
                                         String entityName,
                                         String who,
                                         String comments) {
        return addMessage(createMessage(logLevel, entityLevel, eventId, entityId, entityName, who, comments),
                new Date());
    }

    private ActivityLogEntity addMessage(ActivityLogEntity logEntity, Date date) {
        logEntity.setDate(date);
        manager.persist(logEntity);
        return logEntity;
    }

    private FlowLogEntity addMessage(FlowLogEntity logEntity, int event, Date date, Integer sourcePackageId,
                                     String who, int dbType, int toSend) {
        logEntity.setEvent(manager.find(ActivityLogEventEntity.class, event));
        logEntity.setDate(date);
        logEntity.setPackageId(sourcePackageId);
        logEntity.setWho(who);
        logEntity.setDbType(dbType);
        logEntity.setSendTo(toSend);

        manager.persist(logEntity);
        return logEntity;
    }

    private void initLogEntity(LogEntity logEntity, ActivityLogEntity.LogLevel logLevel,
        ActivityLogEntity.EntityLevel entityLevel, int entityId, String entityName, String comments) {

        logEntity.setLogLevel(logLevel);
        logEntity.setEntityLevel(entityLevel);
        logEntity.setEntityId(entityId);
        logEntity.setEntityName(entityName);
        logEntity.setComments(comments != null ? comments : "");
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void logRecordWarning(int event, int entityId, String recordName, int dbType, int issue, String comments) {
        logRecord(ActivityLogEntity.LogLevel.WARN, event, entityId, recordName, dbType, issue, comments);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void logRecordError(int event, int entityId, String recordName, int dbType, int issue, String comments) {
        logRecord(ActivityLogEntity.LogLevel.ERROR, event, entityId, recordName, dbType, issue, comments);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void logRecord(ActivityLogEntity.LogLevel level, int event, int entityId, String recordName, int dbType,
                          int issue, String comments) {
        ErrorLogEntity logEntity = new ErrorLogEntity();

        initLogEntity(logEntity, level, ActivityLogEntity.EntityLevel.RECORD, entityId,
                recordName, comments);
        logEntity.setDate(new Date());
        logEntity.setEvent(event);
        logEntity.setIssue(issue);
        logEntity.setDbType(dbType);

        manager.persist(logEntity);

        ActivityLogFactory.getFactory().increaseActivityLogCount();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long getActivityLogsCount(RequestParameters params) {
        if (params.filterSize() > 0) {
            try {
                Query query = manager.createQuery("SELECT COUNT(e) FROM ActivityLogEntity e WHERE e.id > 0"
                        + extendQueryString(params));
                return ((Number) query.getSingleResult()).intValue();
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        }
        return ActivityLogFactory.getFactory().updateActivityLogCount(this);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public long getActivityLogsCount() {
        return ((Number) ActivityLogEntity.queryCount(manager).getSingleResult()).longValue();
    }

    private String extendQueryString(RequestParameters params) throws Exception {
        String addQuery = params.buildSearchTemplate();
        if (StringUtils.isNotEmpty(addQuery)) {
            addQuery = " AND " + addQuery;
        }

        addQuery += getSearchRecordsOrder(params.getOrderByField(), params.isDescOrder());
        return addQuery;
    }

    private String getSearchRecordsOrder(ActivityLogEntity.Field field, boolean orderDesc) {
        String addQuery = " ORDER BY e.";
        String desc = getOrderDesc(orderDesc);
        switch (field) {
            case ENTITY_ID:
            case ENTITY_NAME:
                addQuery += ActivityLogEntity.Field.ENTITY_ID.getColumnName()
                        + ", e." + ActivityLogEntity.Field.ENTITY_NAME.getColumnName() + " " + desc;
                break;
            default:
                addQuery += field.getColumnName() + " " + desc;
        }
        addQuery += ", e.id " + desc;
        return addQuery;
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ActivityLogEntity> getActivityLogs(RequestParameters params) {
        try {
            final String sqlQuery = getSqlQuery(params);
            Query query = manager.createQuery(sqlQuery);
            if (params.getLimit() > 0) {
                query = query.setFirstResult(params.getBeginIndex()).setMaxResults(params.getLimit());
            }
            return query.getResultList();
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
        return Collections.emptyList();
    }

    private String getSqlQuery(RequestParameters params) throws Exception {
        boolean useJoinFetch = StringUtils.isEmpty(params.buildSearchTemplate());
        String joinFetchByEvent = useJoinFetch ? "JOIN FETCH e.event " : "";
        return "SELECT e FROM ActivityLogEntity e " + joinFetchByEvent + "WHERE e.id > 0"
                + extendQueryString(params);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String getLastCommentsByEvent(int eventId) {
        Object result = ActivityLogEntity.queryLastCommentsByEvent(eventId, manager).getSingleResult();
        return (result == null ? "" : result.toString());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ActivityLogEventVO> getActivityLogEventVos() {
        return ActivityLogEventEntity.qVOs(manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<FlowLogEntity> getFlowLogs(Integer dfId) {
        return FlowLogEntity.queryByPackageId(dfId, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public FlowLogEntity findLastFlowLog(String entityName, int dbType, Date date) {
        List<FlowLogEntity> list = FlowLogEntity.queryByEntityName(entityName, dbType, date, manager).getResultList();

        return list.isEmpty() ? null : list.get(0);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public FlowLogEntity getFlowLog(Long flowLogId) {
        return manager.find(FlowLogEntity.class, flowLogId);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public FlowLogEntity setFlowLogCompleted(Long flowLogId, int sendTo) {
        FlowLogEntity flowLog = getFlowLog(flowLogId);
        if (flowLog != null) {
            flowLog.setSendTo(sendTo);
            manager.merge(flowLog);
        }
        return flowLog;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<FlowLogEntity> getFlowLogsUncompleted(int maxSize) {
        return FlowLogEntity.queryUncompleted(maxSize, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String getEntityName(int entityId, ActivityLogEntity.EntityLevel entityLvl) {
        List<?> resLst = ActivityLogEntity.queryEntityName(entityId, entityLvl, manager).getResultList();
        return resLst.isEmpty() ? null : (String) resLst.get(0);
    }

    private String getOrderDesc(boolean desc) {
        return desc ? "desc" : "";
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Collection<ErrorLogEntity> getErrorLogs(List<Integer> eventIds, List<Integer> entityIds,
                                                   int fullIssueNumb, int start, int bath) {
        return ErrorLogEntity.queryErrorLog(entityIds, eventIds, fullIssueNumb, start, bath, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Collection<ErrorLogEntity> getLatestErrorLogs(List<Integer> eventIds, List<Integer> entityIds,
                                                         int fullIssueNumb) {
        Collection<ErrorLogEntity> logEntities =
                getErrorLogs(eventIds, entityIds, fullIssueNumb, Constants.UNDEF, Constants.UNDEF);
        Map<String, ErrorLogEntity> latestLogEntities = new HashMap<String, ErrorLogEntity>();
        for (ErrorLogEntity logEntity : logEntities) {
            String entityName = logEntity.getEntityName();
            ErrorLogEntity latestLogEntity = logEntity;
            if (latestLogEntities.containsKey(entityName)) {
                latestLogEntity = chooseLatestErrorLog(logEntity, latestLogEntities.get(entityName));
            }
            latestLogEntities.put(entityName, latestLogEntity);
        }
        return latestLogEntities.values();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Map<String, ErrorLogEntity> getLatestErrorLogs(int dbType, int fullIssueNumber) {
        Collection<ErrorLogEntity> logEntities = ErrorLogEntity.queryErrorLog(
                dbType, fullIssueNumber, manager).getResultList();
        Map<String, ErrorLogEntity> ret = new HashMap<>();
        for (ErrorLogEntity logEntity : logEntities) {
            Integer entityId = logEntity.getEntityId();
            String entityKey = DbUtils.exists(entityId) ? entityId.toString() : logEntity.getEntityName();
            ret.put(entityKey, chooseLatestErrorLog(logEntity, ret.get(entityKey)));
        }
        return ret;
    }

    private ErrorLogEntity chooseLatestErrorLog(ErrorLogEntity error1, ErrorLogEntity error2) {
        return error2 == null || error1.getId() > error2.getId() ? error1 : error2;
    }

    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ActivityLogEntity> getActivities(Collection<Integer> eventIds, Collection<Integer> objectIds,
        int start, int bath) {
        return ActivityLogEntity.queryByEventAndEntity(eventIds, objectIds, start, bath, manager).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void log(ActivityLogEntity.LogLevel logLevel, ActivityLogEntity.EntityLevel entityLevel,
                int event, int entityId, String entityName, String who, String comments) {
        addMessage(logLevel, entityLevel, event, entityId, entityName, who, comments);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void info(ActivityLogEntity.EntityLevel entityLevel,
                     int event, int entityId, String entityName, String who, String comments) {
        addMessage(ActivityLogEntity.LogLevel.INFO, entityLevel, event, entityId, entityName, who, comments);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void logInfoPublishEvent(ActivityLogEntity.EntityLevel entityLevel,
                     int event, int entityId, String entityName, String who, String comments) {
        addMessage(ActivityLogEntity.LogLevel.INFO, entityLevel, event, entityId, entityName, who, comments);
    }

    public void error(ActivityLogEntity.EntityLevel entityLevel,
                      int event, int entityId, String entityName, String who, String comments) {
        addMessage(ActivityLogEntity.LogLevel.ERROR, entityLevel, event, entityId, entityName, who, comments);
    }

    public void warn(ActivityLogEntity.EntityLevel entityLevel,
                     int event, int entityId, String entityName, String who, String comments) {
        addMessage(ActivityLogEntity.LogLevel.WARN, entityLevel, event, entityId, entityName, who, comments);
    }

    public void logRecordList(List<RecordLightVO> list, int event, String who, String comments) {
        if ((list == null) || (list.isEmpty())) {
            return;
        }

        ActivityLogEntity.LogLevel logLevel;
        String recordComment;

        for (RecordLightVO record : list) {
            if (record.isSuccessful()) {
                logLevel = ActivityLogEntity.LogLevel.INFO;
                recordComment = comments;
            } else {
                logLevel = ActivityLogEntity.LogLevel.ERROR;
                recordComment = comments + ", " + record.getErrorMessage();
            }

            addMessage(logLevel, ActivityLogEntity.EntityLevel.RECORD,
                    event, record.getId(), record.getName(), who, recordComment);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void logDeliveryFileError(int event, int dfId, String dfName, String logUser, String message) {
        error(ActivityLogEntity.EntityLevel.FILE, event, dfId, dfName, logUser, message);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void logDeliveryFileProcess(int event, int processId, int dfId, String dfName, String logUser) {
        info(ActivityLogEntity.EntityLevel.FILE, event, dfId, dfName, logUser, "process id=" + processId);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void logDeliveryFile(int event, int dfId, String dfName, String logUser) {
        logDeliveryFile(event, dfId, dfName, logUser, null);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void logDeliveryFile(int event, int dfId, String dfName, String logUser, String message) {
        info(ActivityLogEntity.EntityLevel.FILE, event, dfId, dfName, logUser, message);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public FlowLogEntity logDeliveryFlow(ActivityLogEntity.LogLevel logLevel, int event, Integer sourcePackageId,
                                         String eventPackageName, Date date, IFlowProduct product, int toSend) {
        FlowLogEntity flowEntity = new FlowLogEntity();
        initLogEntity(flowEntity, logLevel, ActivityLogEntity.EntityLevel.FLOW, product.getEntityId(),
                product.getPubCode(), FlowLogCommentsPart.buildComments(eventPackageName, null, product));
        String vendor;
        if (CochraneCMSPropertyNames.isCochraneSftpPublicationNotificationFlowEnabled()
                && event == ILogEvent.PRODUCT_NOTIFIED_ON_PUBLISHED) {
            vendor = COCHRANE_SFTP;
        } else {
            vendor = product.getVendor();
        }
        return addMessage(flowEntity, event, date, sourcePackageId, vendor, product.getDbType(), toSend);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public FlowLogEntity logDeliveryFlowError(int event, Integer sourcePackageId,
                String eventPackageName, Date date, String err, IFlowProduct product, int toSend) {
        FlowLogEntity flowEntity = new FlowLogEntity();

        initLogEntity(flowEntity, ActivityLogEntity.LogLevel.ERROR, ActivityLogEntity.EntityLevel.FLOW,
            product.getEntityId(), product.getPubCode(),
                FlowLogCommentsPart.buildComments(eventPackageName, err, product));
        if (err.contains("Operation 'on published' was failed for")){
            return addMessage(flowEntity, event, date, sourcePackageId, COCHRANE_SFTP, product.getDbType(), toSend);
        }
        return addMessage(flowEntity, event, date, sourcePackageId, product.getVendor(), product.getDbType(), toSend);
    }
}
