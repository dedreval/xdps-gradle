package com.wiley.cms.cochrane.cmanager.publish;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.activitylog.LogEntity;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.EntireRecordVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;

import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.handler.SendToPublishHandler;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessHelper;

import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.jmx.JMXHolder;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.process.task.ITaskManager;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/4/2019
 */
@Local(IPublishScheduler.class)
@Singleton
@Startup
@DependsOn("CochraneCMSProperties")
@Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PublishScheduler extends JMXHolder implements PublishSchedulerMXBean, IPublishScheduler {
    private static final Logger LOG = Logger.getLogger(PublishScheduler.class);

    @EJB(lookup = ProcessHelper.LOOKUP_TASK_MANAGER)
    private ITaskManager tm;

    @EJB(beanName = "CMSProcessManager")
    private ICMSProcessManager pm;

    @EJB(beanName = "EntireDBStorage")
    private IEntireDBStorage edbs;

    @EJB(beanName = "RecordStorage")
    private IRecordStorage rs;

    @EJB(beanName = "DbStorage")
    private IDbStorage dbs;

    @EJB
    private IActivityLogService logService;

    public PublishScheduler() {
        resetPrefix();
    }

    @PostConstruct
    public void start() {
        registerInJMX();
    }

    @PreDestroy
    public void stop() {
        unregisterFromJMX();
    }

    public String[] scheduleSending(String cdNumber, Integer dbId, String inDateStr, String user) throws Exception {
        return scheduleSending(cdNumber, dbId, inDateStr, Now.DATE_TIME_FORMATTER_OUT,
                TimeZone.getDefault().toZoneId(), user);
    }

    public String[] scheduleSending(Integer recordId, String inDateStr, DateTimeFormatter formatter,
                                    ZoneId zoneId, String user) throws Exception {

        RecordEntity record = findAndValidate(recordId);
        String cdNumber = record.getName();

        Date startDate = getStartDate(inDateStr, formatter, zoneId);
        ClDbEntity clDb = record.getDb();
        scheduleSending(cdNumber, new SendToPublishHandler(clDb.getIssue().getFullNumber(), clDb.getIssue().getId(),
                        clDb.getTitle(), clDb.getId()), startDate, user);
        String finalDate = Now.formatDate(startDate, formatter, zoneId);
        logService.info(LogEntity.EntityLevel.SYSTEM, ILogEvent.SCHEDULE_SENDING, record.getId(), cdNumber,
                user, startDate.toString());
        return new String[]{CmsUtils.unescapeEntities(record.getUnitTitle()), finalDate};
    }

    public String[] scheduleSending(String cdNumber, Integer dbId, String inDateStr,
                                    DateTimeFormatter formatter, ZoneId zoneId, String user) throws Exception {

        EntireRecordVO record = findAndValidate(dbId, cdNumber);
        Date startDate = getStartDate(inDateStr, formatter, zoneId);
        scheduleSending(cdNumber, new SendToPublishHandler(record.getLastIssue(), dbId),
                        startDate, user);
        String finalDate = Now.formatDate(startDate, formatter, zoneId);
        logService.info(LogEntity.EntityLevel.SYSTEM, ILogEvent.SCHEDULE_SENDING, record.getId(), cdNumber,
                user, startDate.toString());
        return new String[]{CmsUtils.unescapeEntities(record.getUnitTitle()), finalDate};
    }

    public String[] cancelSending(Integer recordId, String user) throws Exception {

        RecordEntity record = findAndValidate(recordId);
        String cdNumber = record.getName();
        cancelSending(cdNumber);

        logService.info(LogEntity.EntityLevel.SYSTEM, ILogEvent.CANCEL_SENDING, record.getId(), cdNumber, user, "");
        return new String[]{CmsUtils.unescapeEntities(record.getUnitTitle()), null};
    }

    public String[] cancelSending(String cdNumber, Integer dbId, String user) throws Exception {
        EntireRecordVO record = findAndValidate(dbId, cdNumber);
        cancelSending(cdNumber);

        logService.info(LogEntity.EntityLevel.SYSTEM, ILogEvent.CANCEL_SENDING, record.getId(), cdNumber, user, "");
        return new String[]{CmsUtils.unescapeEntities(record.getUnitTitle()), null};
    }

    public String[] findScheduledSending(String cdNumber, Integer dbId) throws Exception {
        return findScheduledSending(cdNumber, dbId, Now.DATE_TIME_FORMATTER_OUT, TimeZone.getDefault().toZoneId());
    }

    public String[] findScheduledSending(String cdNumber, Integer dbId, DateTimeFormatter formatter, ZoneId zoneId)
            throws Exception {
        EntireRecordVO record = findAndValidate(dbId, cdNumber);
        ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_SEND_TO_PUBLISH).get();
        TaskVO task = pm.findProcessTask(pt.getName(), cdNumber);

        return new String[]{CmsUtils.unescapeEntities(record.getUnitTitle()), task != null
                ? Now.formatDate(task.getStartDate(), formatter, zoneId) : null};
    }

    public void scheduleSendingDS(Integer dbId, String startDate, String user) throws Exception {
        DbVO clDb = dbs.getDbVO(dbId);
        if (clDb == null) {
            throw new CmsException(String.format("no database by [%d] found ", dbId));
        }
        scheduleSendingDS(clDb, Now.parseDate(startDate), user);
    }

    private RecordEntity findAndValidate(Integer recordId) throws CmsException {
        RecordEntity record = rs.getRecordEntityById(recordId);
        if (record == null) {
            throw new CmsException(String.format("no records by [%d] found ", recordId));
        }
        String recordName = record.getName();
        if (!record.isQasSuccessful() || !record.isRenderingSuccessful()) {
            throw new CmsException(String.format("%s [%d] is not valid to be published", recordName, recordId));
        }
        List<EntireDBEntity> entireRecords = edbs.getRecordList(record.getDb().getTitle(), 0, 0,
                new String[] {recordName}, null, 0, false);
        if (entireRecords.isEmpty()
                || entireRecords.get(0).getLastIssuePublished() != record.getDb().getIssue().getFullNumber()) {
            throw new CmsException(String.format("%s [%d] is not latest to be published", recordName, recordId));
        }
        return record;
    }

    private EntireRecordVO findAndValidate(Integer dbId, String cdNumber) throws CmsException {
        EntireRecordVO entireRecord = edbs.findRecordByName(dbId, cdNumber);
        if (entireRecord == null) {
            throw new CmsException(String.format("a record %s not found ", cdNumber));
        }
        return entireRecord;
    }

    private void scheduleSending(String cdNumber, SendToPublishHandler handler, Date startDate, String user)
            throws Exception {
        long delay = startDate.getTime() - System.currentTimeMillis();
        if (delay < 0) {
            throw new CmsException("a publishing date is expired");
        }

        ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_SEND_TO_PUBLISH).get();

        TaskVO task = pm.findProcessTask(pt.getName(), cdNumber);
        if (task != null) {
            task = tm.restartTask(task.getId(), delay, false);
            if (task == null) {
                throw new CmsException("sending has been already started and can not be re-scheduled");
            }

        } else {
            ProcessVO pvo = ProcessHelper.createIdPartsProcess(DbEntity.NOT_EXIST_ID, handler, pt,
                    IProcessManager.USUAL_PRIORITY, user, new String[] {cdNumber});
            pm.createProcessTask(pvo.getId(), pt.getName(), delay);
        }
        LOG.info(String.format("sending of %s is scheduled at %s", cdNumber, startDate));
    }

    private void scheduleSendingDS(DbVO clDb, Date startDate, String user) throws Exception {
        long delay = startDate.getTime() - System.currentTimeMillis();
        if (delay < 0) {
            delay = 0;
        }
        ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_SEND_TO_PUBLISH_DS).get();
        TaskVO task = pm.findProcessTask(pt.getName(), clDb.getId().toString());
        if (task == null) {
            ProcessVO pvo = ProcessHelper.createIdPartsProcess(DbEntity.NOT_EXIST_ID, clDb.getIssue() == null
                ? new SendToPublishHandler() : new SendToPublishHandler(clDb.getIssue().getFullNumber(),
                    clDb.getIssue().getId(), clDb.getTitle(), clDb.getId()), pt, IProcessManager.USUAL_PRIORITY,
                        user, new String[]{clDb.getId().toString()});
            pm.createProcessTask(pvo.getId(), pt.getName(), delay);
            logService.info(LogEntity.EntityLevel.SYSTEM, ILogEvent.SCHEDULE_SENDING, clDb.getId(), clDb.getTitle(),
                    user, startDate.toString());
            LOG.info(String.format("sending of database [%d] to DS is scheduled at %s", clDb.getId(), startDate));
        }
    }

    private void cancelSending(String recordName) throws Exception {

        ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_SEND_TO_PUBLISH).get();
        TaskVO task = pm.findProcessTask(pt.getName(), recordName);
        if (task != null) {
            task = tm.stopTask(task.getId());
            if (task == null) {
                throw new CmsException("sending has been already started and can not be canceled");
            }
            tm.deleteTask(task.getId());
            LOG.info(String.format("sending of %s is canceled", recordName));

        } else {
            LOG.warn(String.format("can't find a scheduled sending of %s to cancel", recordName));
        }
    }

    private Date getStartDate(String dateStr, DateTimeFormatter formatter, ZoneId zoneId) throws CmsException {
        try {
            Date date = Now.parseDate(dateStr, formatter, zoneId);
            LOG.debug(String.format("a local date to publish is: %s", date));
            return date;

        } catch (Throwable tr) {
            LOG.error(tr.getMessage());
            throw new CmsException(String.format("can't parse a date %s with format: %s", dateStr, formatter));
        }
    }
}
