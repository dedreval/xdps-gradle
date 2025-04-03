package com.wiley.cms.cochrane.process;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Local;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.GroupEntity;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IKibanaRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusVO;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.AbstractCache;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.KibanaUtil;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.XmlUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 02.12.12
 */
@Singleton
@Startup
@Lock(LockType.READ)
@Local(IRecordCache.class)
public class RecordCache extends AbstractCache implements RecordCacheMXBean, IRecordCache {
    private static final Logger LOG = Logger.getLogger(RecordCache.class);

    @PersistenceContext
    private EntityManager manager;

    private final TCache<Integer, ExternalProcess> processes = new TCache<>();

    private final TCache<String, CachedRecord> records = new TCache<>();

    private final KbCache kbRecords = new KbCache();

    private final TSetCache<Integer> onConversionRecIds = new TSetCache<>();

    private volatile Map<Integer, ClDbVO> lastDbIds = Collections.emptyMap();

    private volatile Map<String, GroupVO> crgGroups = Collections.emptyMap();

    private volatile Map<String, UnitStatusVO> statuses = Collections.emptyMap();
    private volatile Map<String, UnitStatusVO> cdsrStatuses = Collections.emptyMap();

    private final TTreeCache<String, Integer, ExternalProcess> entireProcesses = new TTreeCache<>();

    private final Map<String, ManuscriptValue> manuscriptNumbers = new HashMap<>();

    private volatile Map<String, String> topics = Collections.emptyMap();
    private volatile String allTopics;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void addSingleProcess(String dbName, int eventType) {
        addSingleProcess(dbName, eventType, new ExternalProcess(eventType));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void addSingleProcess(ExternalProcess ep, String dbName, int eventType) {
        addSingleProcess(dbName, eventType, new ExternalProcess(eventType));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    void addSingleProcess(String dbName, int eventType, ExternalProcess pp) {
        entireProcesses.addObject(dbName, eventType, pp);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ExternalProcess getSingleProcess(String dbName, int eventType) {
        return entireProcesses.getObject(dbName, eventType);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ExternalProcess removeSingleProcess(String dbName, int eventType) {
        return entireProcesses.removeObject(dbName, eventType);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void addProcess(ExternalProcess process) {
        processes.addObject(process.getId(), process);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ExternalProcess getProcess(int id) {
        return processes.getObject(id);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Map<Integer, ClDbVO> getLastDatabases() {
        return lastDbIds;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void refreshLastDatabases() {
        Map<Integer, ClDbVO> dbIds = new HashMap<>();
        refreshLastDatabases(BaseType.getCDSR().get(), dbIds);
        refreshLastDatabases(BaseType.getEditorial().get(), dbIds);
        refreshLastDatabases(BaseType.getCCA().get(), dbIds);
        refreshLastDatabases(BaseType.getCentral().get(), dbIds);
        lastDbIds = dbIds;
    }

    private List<ClDbVO> refreshLastDatabases(BaseType bt, Map<Integer, ClDbVO> dbIds) {
        List<ClDbVO> list = ClDbEntity.queryLastClDb(bt.getId(), manager).getResultList();
        List<ClDbEntity> entities = ClDbEntity.queryClDb(bt.getDbId(), Constants.SPD_ISSUE_ID, manager).getResultList();
        entities.forEach(entity -> list.add(new ClDbVO(entity)));
        list.forEach(clDb -> dbIds.put(clDb.getId(), clDb));
        return list;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void addLastCDSRDb(int clDbId, IssueVO issueVO) {
        getLastDatabases().computeIfAbsent(clDbId, f -> new ClDbVO(clDbId,
                CochraneCMSPropertyNames.getCDSRDbName(), issueVO));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Lock(LockType.WRITE)
    public String getTopicsSummary() {
        if (allTopics == null) {
            StringBuilder sb = new StringBuilder(XmlUtils.XML_HEAD).append(
                    "\n<topics xmlns=\"http://ct.wiley.com/ns/xdps/topics\">\n");
            Map<String, String> tmpTopics = topics;
            tmpTopics.forEach((key, value) -> sb.append(value));
            sb.append("</topics>");
            allTopics = sb.toString();
            LOG.debug("the topics summary for ML3G conversion was re-assembled");
        }
        return allTopics;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Lock(LockType.WRITE)
    public void setTopic(String groupName, String topicsSource) {
        allTopics = null;
        topics.put(groupName, XmlUtils.cutXMLHead(topicsSource));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ExternalProcess removeProcess(int id) {
        return processes.removeObject(id);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean addRecord(String recordKey, boolean spd) {
        boolean ret = records.addKey(recordKey);
        if (!ret) {
            CachedRecord cr = records.getObject(recordKey);
            if (cr != null && !cr.active()) {
                cr.setRecord(null, spd, true);
                ret = true;
            }
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean containsRecord(String recordKey) {
        CachedRecord cr = records.getObject(recordKey);
        return cr == null ? isRecordExists(recordKey) : cr.active();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean isRecordExists(String recordKey) {
        return records.getObjects().containsKey(recordKey);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String activateRecord(String recordKey, String publisherId, boolean active) {
        synchronized (RECORD_LOCKER) {
            CachedRecord cr = records.getObject(recordKey);
            return cr != null ? (active ? cr.setActive(publisherId) : cr.resetActiveSPD(publisherId)) : null;
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public IKibanaRecord removeRecord(String recordKey) {
        return removeActiveRecord(recordKey, records.getObject(recordKey));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public IKibanaRecord removeRecord(String recordKey, boolean spd) {
        IKibanaRecord ret = null;
        CachedRecord cr = records.getObject(recordKey);
        if (cr != null) {
            ret = cr.resetRecord(spd);
            if (cr.isEmpty()) {
                records.removeObject(recordKey);
            }
        } else {
            records.removeObject(recordKey);
        }
        return ret;
    }

    private IKibanaRecord removeActiveRecord(String recordKey, CachedRecord cr) {
        IKibanaRecord ret = null;
        if (cr != null) {
            ret = cr.resetActiveRecord(true);
            if (cr.isEmpty()) {
                records.removeObject(recordKey);
            }
        } else {
            records.removeObject(recordKey);
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void removeRecords(Collection<String> records, boolean spd) {
        records.forEach(record -> removeRecord(record, spd));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ManuscriptValue checkAriesRecordOnReceived(String manuscriptNumber, String cdNumber, int whenReadyId) {
        synchronized (manuscriptNumbers) {
            if (manuscriptNumbers.containsKey(manuscriptNumber)) {
                LOG.warn(String.format("manuscript %s for %s already registered", manuscriptNumber,
                        manuscriptNumbers.get(manuscriptNumber)));
            }
            ManuscriptValue ret = new ManuscriptValue(cdNumber, whenReadyId, null);
            manuscriptNumbers.put(manuscriptNumber, ret);
            return ret;
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ManuscriptValue getAriesRecord(String manuscriptNumber) {
        return manuscriptNumbers.get(manuscriptNumber);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ManuscriptValue checkAriesRecordOnPublished(String manuscriptNumber, String cdNumber, int whenReadyId,
                                                       Integer dfId) {
        if (manuscriptNumber != null && !manuscriptNumber.trim().isEmpty()) {
            synchronized (manuscriptNumbers) {
                if (manuscriptNumbers.containsKey(manuscriptNumber)) {
                    return checkManuscriptValue(manuscriptNumber, cdNumber, dfId);
                }
                LOG.warn(String.format("manuscript %s was not registered", manuscriptNumber));
                if (DbUtils.exists(whenReadyId)) {
                    manuscriptNumbers.put(manuscriptNumber, new ManuscriptValue(cdNumber, whenReadyId, dfId));
                }
            }
        }
        return ManuscriptValue.EMPTY;
    }

    private ManuscriptValue checkManuscriptValue(String manuscriptNumber, String cdNumber, Integer dfId) {
        ManuscriptValue value = manuscriptNumbers.get(manuscriptNumber);
        value.setAckId(dfId);
        if (cdNumber != null) {
            value.published = true;
            value.setCdNumber(cdNumber);
        }
        if (value.readyForAcknowledgement()) {
            return manuscriptNumbers.remove(manuscriptNumber);
        }
        return ManuscriptValue.EMPTY;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void putKibanaRecord(String recordKey, IKibanaRecord record, boolean spd, boolean setActive) {
        CachedRecord cr = records.getObject(recordKey, CachedRecord.class);
        cr.setRecord(record, spd, setActive);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public IKibanaRecord getKibanaRecord(String recordKey) {
        CachedRecord cr = records.getObject(recordKey);
        return cr == null ? null : (cr.activeFirst() ? cr.first : (cr.activeSPD() ? cr.spdSecond : null));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public IKibanaRecord getKibanaRecord(String recordKey, boolean spd) {
        CachedRecord cr = records.getObject(recordKey);
        return cr == null ? null : (spd ? cr.spdSecond : cr.first);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void addKibanaTransaction(String transactionId, String kibanaRecord, String cdNumber) {
        kbRecords.addTransaction(transactionId, kibanaRecord, cdNumber);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Pair<List<String>, Set<String>> removeKibanaTransaction(String transactionId) {
        return kbRecords.removeTransaction(transactionId);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public IKibanaRecord getPreRecord(Integer dfId, String cdNumber) {
        return kbRecords.getPreRecord(dfId, cdNumber);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public IKibanaRecord checkPreRecord(Integer dfId, String cdNumber, IKibanaRecord kr) {
        return kbRecords.checkPreRecord(dfId, cdNumber, kr);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public IKibanaRecord checkPostRecord(String cdNumber, IKibanaRecord kr) {
        return kbRecords.checkPostRecord(cdNumber, kr);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void addOnConversionRecordIds(Collection<Integer> recIds) {
        for (Integer recId: recIds) {
            onConversionRecIds.addObject(recId);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean containsOnConversionRecordId(Integer recId) {
        return onConversionRecIds.contains(recId);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void removeOnConversionRecordIds(Collection<Integer> recIds) {
        for (Integer recId: recIds) {
            onConversionRecIds.removeObject(recId);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public GroupVO getCRGGroup(String sid) {
        return crgGroups.get(sid);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<String> getCRGGroupCodes() {
        return crgGroups.keySet().iterator();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<UnitStatusVO> getUnitStatuses(boolean isCdsr) {
        return isCdsr ? cdsrStatuses.values().iterator() : statuses.values().iterator();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public UnitStatusVO getUnitStatus(String name, boolean cdsr) {
        if (!cdsr) {
            return statuses.get(name);
        }
        UnitStatusVO ret = cdsrStatuses.get(name);
        if (ret == null) {
            ret = cdsrStatuses.get(name.toLowerCase());
        }
        return ret;
    }

    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public void start() {
        registerInJMX();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void update() {
        LOG.info("record cache updating ...");

        clear();

        //String dbName = CochraneCMSPropertyNames.getCDSRDbName();
        //Collection<ClDbVO> list = (List<ClDbVO>) ClDbEntity.queryLastClDb(dbName, manager).getResultList();
        //List<ClDbEntity> clDbList = ClDbEntity.queryClDb(DatabaseEntity.CDSR_KEY,
        //        Constants.SPD_ISSUE_ID, manager).getResultList();
        //clDbList.forEach(f -> list.add(new ClDbVO(f)));

        refreshLastDatabases();

        //Map<Integer, ClDbVO> dbIds = new HashMap<>();
        //Collection<ClDbVO> list = refreshLastDatabases(BaseType.getCDSR().get(), dbIds);
        //refreshLastDatabases(BaseType.getEditorial().get(), dbIds);
        //refreshLastDatabases(BaseType.getCCA().get(), dbIds);

        //list.forEach(clDb -> dbIds.put(clDb.getId(), clDb));

        List<PublishedAbstractEntity> unpublished = CochraneCMSBeans.getPublishStorage().getWhenReadyUnpublished(
                lastDbIds.keySet());

        for (Map.Entry<Integer, ClDbVO> entry: lastDbIds.entrySet()) {
            List<RecordEntity> recs =
                    RecordEntity.queryRecordsUnfinished(null, entry.getKey(), manager).getResultList();
            recs.forEach(r -> putKibanaRecord(BaseType.find(r.getDb().getTitle()).get(), r, unpublished,
                    CochraneCMSBeans.getPublishStorage(), ResultStorageFactory.getFactory().getInstance()));
        }

        addCRGGroups();
        addStatuses();
        addTopics();
        addAcknowledgement(lastDbIds.keySet());

        //lastDbIds = dbIds;

        LOG.info("record cache has updated - " + this);
    }

    private void addAcknowledgement(Collection<Integer> dbIds) {
        Map<String, List<DeliveryFileVO>> map = AriesHelper.mapToManuscriptNumbers(
                DeliveryFileEntity.queryAcknowledgementUnfinished(dbIds, manager).getResultList());
        List<PublishedAbstractEntity> list = map.isEmpty()
            ? PublishedAbstractEntity.queryAbstractsNoAcknowledgement(dbIds, manager).getResultList()
            : PublishedAbstractEntity.queryAbstractsNoAcknowledgement(dbIds, map.keySet(), manager).getResultList();
        int pubNotified = PublishProfile.getProfile().get().getDestination().ordinal();
        list.forEach(pae -> addAcknowledgement(pae, pubNotified, map.remove(pae.getManuscriptNumber())));
    }

    private void addAcknowledgement(PublishedAbstractEntity pae, int pubNotified, List<DeliveryFileVO> dfs) {
        if (PublishedAbstractEntity.isCanceled(pae.getNotified())
                || manuscriptNumbers.containsKey(pae.getManuscriptNumber())) {
            return;
        }

        boolean published = pae.getPubNotified() == pubNotified;
        Integer ackId = pae.getAcknowledgementId();
        String cdNumber = pae.getRecordName();
        ManuscriptValue value = null;

        if (dfs != null) {
            for (DeliveryFileVO df : dfs) {
                if (df.getId().equals(ackId) || (ackId == null && pae.getDate().before(df.getDate()))) {
                    value = checkAriesRecordOnReceived(pae.getManuscriptNumber(), cdNumber, pae.getId());
                    value.ackId = df.getId();
                    break;
                }
            }
        }
        if (value == null && ackId == null) {
            value = checkAriesRecordOnReceived(pae.getManuscriptNumber(), cdNumber, pae.getId());
        }
        if (value != null) {
            value.published = published;
            if (value.readyForAcknowledgement()) {
                LOG.warn(String.format("%s is ready for acknowledgement on publication", value));
            }
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void clear() {
        processes.clear();
        records.clear();
        kbRecords.clear();
        entireProcesses.clear();
        onConversionRecIds.clear();
    }

    @PreDestroy
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void stop() {

        unregisterFromJMX();
        clear();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String toString() {
        return String.format("%d records: %s, kibana: %s, %d manuscripts: %s, entire processes: %s, %d conversions: %s,"
            + " latest db [%s], %d crg groups", records.getObjects().size(), getRecordCacheState(),
                kbRecords.getState(), manuscriptNumbers.size(), getManuscriptsState(), entireProcesses.getObjects(),
                onConversionRecIds.getObjects().size(), onConversionRecIds.getObjects(),
                getLatestDbIds(), crgGroups.size());
    }

    private String getRecordCacheState() {
        StringBuilder sb = new StringBuilder();
        records.getObjects().forEach((k, v) -> sb.append(k).append(":").append(v == null ? "" : v).append(" "));
        return sb.toString();
    }

    private String getManuscriptsState() {
        StringBuilder sb = new StringBuilder();
        manuscriptNumbers.forEach((k, v) -> sb.append(k).append(":").append(v).append(" "));
        return sb.toString();
    }

    private void putKibanaRecord(BaseType bt, RecordEntity rec, List<PublishedAbstractEntity> unpublished,
                                 IPublishStorage ps, IResultsStorage rs) {
        IKibanaRecord kr = KibanaUtil.getKibanaRecord(bt, rec, unpublished, ps, rs);
        if (kr != null) {
            FlowProduct.SPDState spd = kr.getFlowProduct().sPD();
            putKibanaRecord(rec.getName(), kr, spd.is(), !spd.is() || !rec.isAwaitingPublication());
        } else {
            addRecord(rec.getName(), CmsUtils.isScheduledIssue(rec.getDb().getIssue().getId()));
        }
    }

    private String getLatestDbIds() {
        StringBuilder sb = new StringBuilder();
        lastDbIds.forEach((id, db) -> sb.append(id).append(" "));
        return sb.toString();
    }

    private void addCRGGroups() {
        List<GroupEntity> list = GroupEntity.queryGroups(manager).getResultList();
        Map<String, GroupVO> tmpGroups = new LinkedHashMap<>();
        list.forEach(f -> tmpGroups.put(f.getSid(), new GroupVO(f)));

        crgGroups = tmpGroups;
    }

    private void addStatuses() {
        List<UnitStatusEntity> list = UnitStatusEntity.queryAll(manager).getResultList();

        Map<String, UnitStatusVO> tmpStatuses = new LinkedHashMap<>();
        Map<String, UnitStatusVO> tmpCdsrStatuses = new LinkedHashMap<>();

        list.forEach(f -> addStatus(f, tmpStatuses, tmpCdsrStatuses));

        statuses = tmpStatuses;
        cdsrStatuses = tmpCdsrStatuses;
    }

    private void addTopics() {
        Map<String, String> tmpTopics = new HashMap<>();
        IRepository rp = RepositoryFactory.getRepository();
        Iterator<String> groups = getCRGGroupCodes();
        try {
            while (groups.hasNext()) {
                String groupName = groups.next();
                String entirePath = FilePathBuilder.getPathToTopics(groupName);
                if (rp.isFileExists(entirePath)) {
                    tmpTopics.put(groupName, XmlUtils.cutXMLHead(InputUtils.readStreamToString(
                            rp.getFile(entirePath))));
                }
            }
        } catch (Exception e) {
            LOG.error(e);
        }
        topics = tmpTopics;
        allTopics = null;
    }

    private void addStatus(UnitStatusEntity status, Map<String, UnitStatusVO> tmpStatuses,
                           Map<String, UnitStatusVO> tmpCdsrStatuses) {
        if (status.is4Cdsr()) {
            tmpCdsrStatuses.put(status.getName(), new UnitStatusVO(status));
            UnitStatusVO vo = new UnitStatusVO(status);
            vo.setUiShow(false);
            tmpCdsrStatuses.put(status.getName().toLowerCase(), vo);

        } else {
            tmpStatuses.put(status.getName(), new UnitStatusVO(status));
        }
    }

    /**
     * The cached record for WR processing and SF flow logging
     */
    public static class CachedRecord {
        private IKibanaRecord first;       // usual article | next SPD (to be rejected)
        private IKibanaRecord spdSecond;   // SPD article
        private int active;

        void setRecord(IKibanaRecord kr, boolean spd, boolean setActive) {
            if (spd) {
                if (kr != null) {
                    spdSecond = kr;
                }
                if (setActive) {
                    setActiveSPD();
                }
            } else {
                if (kr != null) {
                    first = kr;
                }
                if (setActive) {
                    setActiveFirst();
                }
            }
        }

        IKibanaRecord resetRecord(boolean spd) {
            IKibanaRecord ret;
            if (spd) {
                ret = spdSecond;
                spdSecond = null;
                if (activeSPD()) {
                    resetActive();
                }
            } else {
                ret = first;
                first = null;
                if (activeFirst()) {
                    resetActive();
                }
            }
            return ret;
        }

        void resetActive() {
            active = 0;
        }

        String setActive(String publisherId) {
            String ret = null;
            if (first != null && first.getPubName().equals(publisherId)) {
                if (activeSPD()) {
                    ret = spdSecond == null ? "" : spdSecond.getPubName();
                } else {
                    setActiveFirst();
                }

            } else if (spdSecond != null && spdSecond.getPubName().equals(publisherId)) {
                if (activeFirst()) {
                    ret = first == null ? "" : first.getPubName();
                } else {
                    setActiveSPD();
                }
            }
            return ret;
        }

        String resetActiveSPD(String publisherId) {
            if (spdSecond != null && spdSecond.getPubName().equals(publisherId) && activeSPD()) {
                resetActive();
            }
            return null;
        }

        boolean active() {
            return active > 0;
        }

        boolean activeSPD() {
            return active == 2;
        }

        void setActiveSPD() {
            active = 2;
        }

        boolean activeFirst() {
            return active == 1;
        }

        void setActiveFirst() {
            active = 1;
        }

        IKibanaRecord getActive() {
            return activeFirst() ? first : spdSecond;
        }

        IKibanaRecord resetActiveRecord(boolean resetActiveFlag) {
            IKibanaRecord ret = null;
            if (activeFirst()) {
                ret = first;
                first = null;

            } else if (activeSPD()) {
                ret = spdSecond;
                spdSecond = null;
            }
            if (resetActiveFlag) {
                active = 0;
            }
            return ret;
        }

        boolean isEmpty() {
            return !active() && first == null && spdSecond == null;
        }

        @Override
        public String toString() {
            if (isEmpty()) {
                return "-";
            }
            StringBuilder sb = new StringBuilder("[");
            append(first, false, activeFirst(), sb);
            append(spdSecond, true, activeSPD(), sb);
            sb.append("]");
            return sb.toString();
        }

        private void append(IKibanaRecord kr, boolean spd, boolean active, StringBuilder sb) {
            if (kr != null) {
                sb.append(spd ? " spd:" : " ").append(active ? "*" : "").append(kr.getPubName());
            } else if (active) {
                sb.append(spd ? " spd:* " : " *");
            }
        }
    }

    private static class KbCache {
        private final Map<String, Pair<List<String>, Set<String>>> transactionRecords;
        private final Map<String, IKibanaRecord> preRecords;   // to keep records on first flow step (just received)
        private final Map<String, IKibanaRecord> postRecords;  // to keep records failed on last flow step (DS sending)

        private KbCache() {
            transactionRecords = new HashMap<>();
            preRecords = new HashMap<>();
            postRecords =  new HashMap<>();             
        }

        synchronized IKibanaRecord getPreRecord(Integer dfId, String recordKey) {
            IKibanaRecord ret =  preRecords.get(recordKey);
            return ret != null && dfId.equals(ret.getDfId()) ? ret : null;
        }

        synchronized IKibanaRecord checkPreRecord(Integer dfId, String recordKey, IKibanaRecord record) {
            IKibanaRecord ret;
            if (record != null) {
                ret = preRecords.remove(recordKey);
                preRecords.put(recordKey, record);
            } else {
                ret = preRecords.get(recordKey);
                if (ret == null || !dfId.equals(ret.getDfId())) {
                    return null;
                }
                preRecords.remove(recordKey); 
            }
            return ret;
        }

        synchronized IKibanaRecord checkPostRecord(String recordKey, IKibanaRecord record) {
            IKibanaRecord ret = postRecords.remove(recordKey);
            if (record != null) {
                postRecords.put(recordKey, record);
            }
            return ret;
        }

        synchronized void addTransaction(String transactionId, String record, String cdNumber) {
            Pair<List<String>, Set<String>> lists = transactionRecords.computeIfAbsent(
                    transactionId, f -> new Pair<>(new ArrayList<>(), new HashSet<>()));
            lists.first.add(record);
            lists.second.add(cdNumber);
        }

        synchronized Pair<List<String>, Set<String>> removeTransaction(String transactionId) {
            return transactionRecords.remove(transactionId);
        }

        synchronized String getState() {
            StringBuilder sb = new StringBuilder().append(String.format(
                "%d 'pre' records: %s, %d 'post' records: %s, %d transactions: ", preRecords.size(),
                    preRecords.keySet(), postRecords.size(), postRecords.keySet(), transactionRecords.size()));
            transactionRecords.forEach((k, v) -> sb.append(k).append(":[").append(v).append("] "));
            return sb.toString();
        }

        synchronized void clear() {
            transactionRecords.clear();
            postRecords.clear();
            preRecords.clear();
        }
    }

    /**
     * Aries When Ready record mapped to the manuscript number
     */
    public static class ManuscriptValue {
        private static final ManuscriptValue EMPTY = new ManuscriptValue(null, null, null);

        final Integer whenReadyId;
        String cdNumber;
        Integer ackId;
        boolean published;

        ManuscriptValue(String cdNumber, Integer whenReadyId, Integer ackId) {
            this.cdNumber = cdNumber;
            this.whenReadyId = whenReadyId;
            this.ackId = ackId;
        }

        public Integer whenReadyId() {
            return whenReadyId;
        }

        public Integer ackId() {
            return ackId;
        }

        public String cdNumber() {
            return cdNumber;
        }

        public boolean isEmpty() {
            return cdNumber == null && ackId == null;
        }

        private void setCdNumber(String cdNumber) {
            if (cdNumber != null) {
                this.cdNumber = cdNumber;
            }
        }

        private void setAckId(Integer ackId) {
            if (ackId != null) {
                this.ackId = ackId;
            }
        }

        public boolean readyForAcknowledgement() {
            return published && ackId != null;
        }
        
        @Override
        public String toString() {
            return published ? String.format("%s [%d]*", cdNumber, ackId) : String.format("%s [%d]", cdNumber, ackId);
        }
    }
}
