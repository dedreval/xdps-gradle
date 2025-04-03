package com.wiley.cms.cochrane.activitylog;

import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.RecordLightVO;

/**
 * @author <a href='mailto:oarinosov@wiley.ru'>Oleg Arinosov</a>
 * @version 06.12.2006
 */
public interface IActivityLog {

    void logRecordList(List<RecordLightVO> records, int event, String who, String comments);

    /**
     * Writes a error record into the error log for an article.
     * @param event         Error type or a type of event that caused an error
     * @param entityId      An external identifier of a process or a package or something else to identify this record
     * @param recordName    The article name
     * @param dbType        The id of DatabaseEntity for this article
     * @param issue         The full issue number or Constants.NO_ISSUE for entire
     * @param comments      The error detail
     */
    void logRecordError(int event, int entityId, String recordName, int dbType, int issue, String comments);

    void logRecordWarning(int event, int entityId, String recordName, int dbType, int issue, String comments);

    void logRecord(ActivityLogEntity.LogLevel logLevel, int event, int entityId, String recordName, int dbType,
        int issue, String comments);

    void info(ActivityLogEntity.EntityLevel entityLevel,
        int event, int entityId, String entityName, String who, String comments);

    void error(ActivityLogEntity.EntityLevel entityLevel,
        int event, int entityId, String entityName, String who, String comments);

    void warn(ActivityLogEntity.EntityLevel entityLevel,
        int event, int entityId, String entityName, String who, String comments);

    void log(ActivityLogEntity.LogLevel logLevel, ActivityLogEntity.EntityLevel entityLevel,
            int event, int entityId, String entityName, String who, String comments);

    void logDeliveryFileError(int event, int dfId, String dfName, String logUser, String message);

    void logDeliveryFileProcess(int event, int processId, int dfId, String dfName, String logUser);

    void logInfoPublishEvent(ActivityLogEntity.EntityLevel entityLevel, int event,
                             int dfId, String entityName, String who, String comments);

    void logDeliveryFile(int event, int dfId, String dfName, String logUser);

    void logDeliveryFile(int event, int dfId, String dfName, String logUser, String message);

    FlowLogEntity logDeliveryFlow(ActivityLogEntity.LogLevel logLevel, int event, Integer sourcePackageId,
            String eventPackageName, Date date, IFlowProduct product, int toSend);

    FlowLogEntity logDeliveryFlowError(int event, Integer sourcePackageId,
            String eventPackageName, Date date, String err, IFlowProduct product, int toSend);
}
