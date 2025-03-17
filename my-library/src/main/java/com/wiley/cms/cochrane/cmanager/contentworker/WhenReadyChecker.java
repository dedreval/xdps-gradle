package com.wiley.cms.cochrane.cmanager.contentworker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.IKibanaRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.IScheduledTask;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.KibanaUtil;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/6/2016
 */
public class WhenReadyChecker implements ITaskExecutor, IScheduledTask {
    private static final Logger LOG = Logger.getLogger(WhenReadyChecker.class);
    private static final Res<Property> CONTROL_INTERVAL = CochraneCMSPropertyNames.getAwaitingPublicationInterval();
    private static final Res<Property> LONG_DELAY = Property.get(
            "cms.cochrane.when_ready.awaiting_timeout.schedule.long_delay", "0 0 0/2 * * ?");
    private static final Res<Property> SHORT_DELAY = Property.get(
            "cms.cochrane.when_ready.awaiting_timeout.schedule.short_delay", "0 0/15 * * * ?");

    /* CENTRAL issue db identifier -> amount of the records that are still under QA - last check time **/
    private static final Map<Integer, Pair<Integer, Long>> LAST_UNCOMPLETED_CENTRAL_COUNTS = new HashMap<>();

    private boolean useShortDelay = true;

    public boolean execute(TaskVO task) throws Exception {
        try {
            Map<Integer, ClDbVO> lastDbMap = CochraneCMSPropertyNames.lookupRecordCache().getLastDatabases();
            int checkCentralSeconds = CochraneCMSPropertyNames.checkCentralProcessing();
            if (checkCentralSeconds > 0) {
                checkCentralProcessing(lastDbMap, checkCentralSeconds);
            }

            List<PublishedAbstractEntity> list = CochraneCMSBeans.getPublishStorage().getWhenReadyUnpublished(
                lastDbMap.keySet());
            if (list.isEmpty()) {
                useShortDelay = false;

            } else {
                execute(list, lastDbMap);
            }
            CochraneCMSPropertyNames.lookupFlowLogger().completeFlowLogEvents();

        } catch (Throwable th) {
            LOG.error(th);
        }

        updateSchedule(task);
        return true;
    }

    @Override
    public String getScheduledTemplate() {
        return useShortDelay ? getScheduleShortDelay() : getScheduleLongDelay();
    }

    private static void execute(List<PublishedAbstractEntity> entities, Map<Integer, ClDbVO> lastDbMap) {

        int interval = getControlInterval();

        TimeUnit tm = TimeUnit.MILLISECONDS.toHours(interval) > 0 ? TimeUnit.HOURS
                : TimeUnit.MILLISECONDS.toMinutes(interval) > 0 ? TimeUnit.MINUTES : null;
        if (tm == null) {
            LOG.warn(String.format("cms.cochrane.when_ready.awaiting_timeout in %d is too small", interval));
            return;
        }
        String tmPostfix = TimeUnit.HOURS == tm ? " hour(s)" : " min";

        long current = System.currentTimeMillis();
        Date controlDate = new Date(current - interval);

        Map<Integer, List<PublishedAbstractEntity>> results = new HashMap<>();

        PublishDestination dest = PublishProfile.getProfile().get().getDestination();
        for (PublishedAbstractEntity pae: entities) {

            Date date = getStartDate(pae);
            if (controlDate.before(date)) {
                continue;
            }

            Integer dbId = pae.getDbId();
            List<PublishedAbstractEntity> list = results.computeIfAbsent(dbId, f -> new ArrayList<>());
            list.add(pae);
        }
        results.forEach((dbId, list) -> handleResults(lastDbMap.get(dbId), dest, list, tm, current, tmPostfix));
    }

    public static void checkCentralProcessing(Map<Integer, ClDbVO> lastDbMap, int checkCentralSeconds) {
        BaseType central = BaseType.getCentral().get();
        lastDbMap.forEach((i, db) -> checkCentralProcessing(central, db, checkCentralSeconds));
    }

    private static void checkCentralProcessing(BaseType bt, ClDbVO db, int checkCentralSeconds) {
        if (!bt.getId().equals(db.getTitle())) {
            return;
        }
        List<DeliveryFileVO> list = ResultStorageFactory.getFactory().getInstance().getDeliveryFileList(
                db.getId(), IDeliveryFileStatus.STATUS_BEGIN, 0);
        List<RecordEntity> records = list.isEmpty() ? Collections.emptyList()
                : RecordStorageFactory.getFactory().getInstance().getRecordsQasUncompleted(list.get(0).getId());
        if (records.isEmpty()) {
            LAST_UNCOMPLETED_CENTRAL_COUNTS.remove(db.getId());
            return;
        }
        int dfId = list.get(0).getId();
        LOG.debug(String.format("%d uncompleted records found for central package [%d]", records.size(), dfId));
        Pair<Integer, Long> lastCount = LAST_UNCOMPLETED_CENTRAL_COUNTS.get(db.getId());
        if (lastCount != null && records.size() == lastCount.first) {  // noting has changed since latest check
            int diff = (int) (System.currentTimeMillis() - lastCount.second);
            if (diff > checkCentralSeconds * Now.MS_IN_SEC && diff > Now.calculateMillisInHour() * 2) {
                LOG.debug(String.format(
                    "uncompleted QA process for central package [%d] was unchanged during %s and will be restarted",
                        dfId, Now.buildTime(diff)));
                LAST_UNCOMPLETED_CENTRAL_COUNTS.remove(db.getId());
                CochraneCMSBeans.getCMSProcessManager().resendCentralQAService(dfId, records);
            }
        } else {
            LAST_UNCOMPLETED_CENTRAL_COUNTS.put(db.getId(), new Pair<>(records.size(), System.currentTimeMillis()));
        }
    }

    private static void handleResults(ClDbVO db, PublishDestination dest, List<PublishedAbstractEntity> results,
                                      TimeUnit tm, long current, String postfix) {
        boolean spd = CmsUtils.isScheduledIssue(db.getIssue().getId());
        int count = 0;
        results.sort(WhenReadyChecker::compare);

        Map<String, RecordEntity> unpublished = new HashMap<>();
        List<RecordEntity> list = CochraneCMSBeans.getRecordManager().getUnfinishedRecords(db.getId());
        list.forEach(r -> unpublished.put(r.getName(), r));

        Map<Integer, StringBuilder> resultsToPrint = null;
        IRecordCache cache = CochraneCMSBeans.getRecordCache();
        BaseType bt = BaseType.find(db.getTitle()).get();

        for (PublishedAbstractEntity pae: results) {

            String cdNumber = pae.getRecordName();
            RecordEntity re = unpublished.get(cdNumber);
            int delay = getDelayInterval(current, tm, pae, spd ? re : null);
            String msg = determineState(bt, pae, re, dest, delay);
            if (msg == null) {
                continue;
            }
            if (resultsToPrint == null) {
                resultsToPrint = new LinkedHashMap<>();
            }
            StringBuilder sb  = resultsToPrint.computeIfAbsent(delay, f -> delay == 0
                    ? new StringBuilder("\n-- no actual SLA delaying yet, but a possible failure on processing --\n")
                    : new StringBuilder("\n-- more than ").append(delay).append(postfix).append(" --\n"));

            IKibanaRecord kr = spd && delay == 0 ? null : cache.getKibanaRecord(cdNumber);
            if (kr != null) {
                KibanaUtil.logKibanaRecEvent(kr, pae.getLanguage(), KibanaUtil.Event.DELAY,
                        String.format("Record is delayed more than %d %s: %s", delay, postfix, msg));
            }
            sb.append(String.format("%s    [%s]", pae.toString(), msg)).append(";\n\n");
            
            count++;
        }

        if (resultsToPrint != null) {
            StringBuilder sb = new StringBuilder();
            resultsToPrint.values().forEach(sb::append);

            String tag = CmsUtils.buildIssueYear(db.getIssue(), spd) + "-"
                    + CmsUtils.buildIssueNumber(db.getIssue(), spd);
            MessageSender.sendSomethingForIssue(db.getTitle(), tag, sb.toString(),
                    MessageSender.MSG_TITLE_PUBLISH_TIMEOUT, spd ? "scheduled" : "");
            LOG.warn(String.format("%d delayed records have been found for issue %s", count, tag));
        }
    }

    private static int getDelayInterval(long current, TimeUnit tm, PublishedAbstractEntity pae, RecordEntity re) {
        Date date = re != null ? re.getMetadata().getPublishedDate() : getStartDate(pae);
        long ret = current - date.getTime();
        if (ret < 0) {
            return 0;
        }
        if (ret == 0) {
            ret = getControlInterval();
        }
        return (int) tm.convert(ret, TimeUnit.MILLISECONDS);
    }

    private static Date getStartDate(PublishedAbstractEntity pae) {
        if (pae.isReprocessed()) {
            DeliveryFileEntity de = ResultStorageFactory.getFactory().getInstance().getDeliveryFileEntity(
                    pae.getInitialDeliveryId());
            if (de != null) {
                return de.getDate();
            }
        }
        return pae.getDate();
    }

    private static String determineState(BaseType bt, PublishedAbstractEntity pae, RecordEntity re,
                                         PublishDestination dest, int delay) {
        int notified = pae.getNotified();
        boolean notificationNotSent = !PublishedAbstractEntity.isNotifiedOnPublished(notified);

        String ret;
        if (re == null) {
            ret = notificationNotSent && bt.isCDSR() ? "a notification on publishing wasn't sent" : null;
        } else {
            Collection<Integer> wrTypesIds = dest.getWhenReadyTypeIds();
            ret = "processing";
            int state = re.getState();
            DeliveryFileEntity df = re.getDeliveryFile();
            boolean inProcess = isInProcess(state, df);
            if (state == RecordEntity.STATE_WAIT_WR_CANCELLED_NOTIFICATION) {
                ret = "awaiting cancellation of publication"
                        + determinePublishPackages(pae, PublishDestination.SEMANTICO.getWhenReadyTypeIds());
            } else if (state == RecordEntity.STATE_WAIT_WR_PUBLISHED_NOTIFICATION) {
                ret = delay > 0 ? dest.getAwaitingStr(notified) + determinePublishPackages(pae, wrTypesIds) : null;
            }  else if (RecordEntity.isPublishing(state)) {
                ret = "sending"  + determinePublishPackages(pae, wrTypesIds);
            } else if (!re.isProcessed())  {
                ret = dest.getAwaitingStr(notified);
            } else if (re.isQasCompleted() && !re.isQasSuccessful()) {
                ret = "QA might fail";
            } else if (re.isRenderingCompleted() && !re.isRenderingSuccessful()) {
                ret = "rendering or WML3G conversion might fail";
            } else if (state == RecordEntity.STATE_WR_ERROR && !re.isQasCompleted() && !re.isQasSuccessful()) {
                ret = "RevMan conversion might fail";
            } else if (inProcess) {
                ret = getStuckCause(df, ret);
            }
        }
        return ret;
    }

    private static String determinePublishPackages(PublishedAbstractEntity pae, Collection<Integer> pubTypesIds) {
        Integer dfId = pae.getDeliveryId();
        if (dfId == null) {
            return "";
        }
        List<PublishRecordEntity> list = CochraneCMSBeans.getPublishStorage().getPublishRecords(pae.getNumber(),
                dfId, pubTypesIds);
        StringBuilder ret = new StringBuilder();
        String hwFail4Article = null;
        for (PublishRecordEntity pre: list) {
            String packageName = pre.getPublishPacket().getFileName();
            if (packageName == null || packageName.isEmpty()) {
                continue;
            }
            if (pae.getPubNumber() != pre.getPubNumber()) {
                continue;
            }
            ret.append("\n\t");
            ret.append(packageName);
            if (pre.isFailed()) {
                hwFail4Article = ", failed on load to publish";
            }
        }
        return hwFail4Article != null ? hwFail4Article + ret : ret.toString();
    }

    private static boolean isInProcess(int state, DeliveryFileEntity df) {
        int status = df.getStatus().getId();
        return (state == RecordEntity.STATE_PROCESSING && (IDeliveryFileStatus.STATUS_BEGIN == status)
                            || IDeliveryFileStatus.STATUS_RND_NOT_STARTED == status);
    }

    private static String getStuckCause(DeliveryFileEntity df, String ret) {
        String stuck = ret;
        ExternalProcess failPvo = CochraneCMSBeans.getCMSProcessManager().findFailedPackageProcess(df.getId());
        if (failPvo == null) {
            return stuck;
        }
        stuck = String.format(
                "processing, but it belongs to '%s' that might be stuck because of a process %s [%d] failed",
            df.getName(), failPvo.getLabel(), failPvo.getId());
        String err = failPvo.getMessage();
        if (err != null) {
            stuck = stuck + " with: " + err;
        }
        LOG.warn(stuck);
        return stuck;
    }

    private static int compare(PublishedAbstractEntity p1, PublishedAbstractEntity p2) {
        int ret = p1.getPubName().compareTo(p2.getPubName());
        return (ret != 0) ? ret : p1.toString().compareTo(p2.toString());
    }

    private static String getScheduleLongDelay() {
        return LONG_DELAY.get().getValue();
    }

    private static String getScheduleShortDelay() {
        return SHORT_DELAY.get().getValue();
    }

    private static int getControlInterval() {
        return CONTROL_INTERVAL.get().asInteger();
    }
}
