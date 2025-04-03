package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.res.Property;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/28/2019
 */
public class ImportJatsPackage extends JatsPackage {

    private static final Logger LOG = Logger.getLogger(ImportJatsPackage.class);
    private static final String TO_EXCLUDE = "to_exclude";
    private static final String TO_INCLUDE = "to_include";

    private static final Set<PackageType.EntryType> SUPPORTED_TYPES = new HashSet<>(Arrays.asList(
            PackageType.EntryType.ARTICLE, PackageType.EntryType.TOPIC, PackageType.EntryType.TA,
                PackageType.EntryType.UNDEF));

    private final CDSRHistory history;

    private final Collection<String> included = new HashSet<>();
    private final Collection<String> excluded = new HashSet<>();

    public ImportJatsPackage(URI packageUri, String dbName, int deliveryId, int fullIssueNumber) {
        super(packageUri, dbName, deliveryId, fullIssueNumber);
        history = new CDSRHistory(CochraneCMSBeans.getVersionManager(), rs);
    }

    @Override
    public boolean isAut() {
        return false;
    }

    @Override
    protected Collection<String> getArticleNames() {
        return history.history.keySet();
    }

    @Override
    protected String getJatsFolder(String pubName, String cdNumber, String language) {
        if (language != null) {
            int ind = pubName.indexOf(language);
            if (ind > 0) {
                return pubName.substring(0, ind - 1);
            }
        }
        return pubName;
    }

    @Override
    public Set<PackageType.EntryType> getEntryTypesSupported() {
        return SUPPORTED_TYPES;
    }

    @Override
    protected boolean parseZipEntry(Integer issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo, ZipEntry ze)
            throws Exception {
        String path = ze.getName();
        PackageType.Entry entry = PackageChecker.getPackageEntry(path, packageType);

        String[] pathParts = PackageChecker.replaceBackslash2Forward(path).split("/");
        String fileName = pathParts[pathParts.length - 1];
        if (isExcluded(fileName)) {
            return false;
        }
        PackageType.EntryType type = entry.getType();
        if (!isEntryTypeSupported(type, false)) {
            throw type.throwUnsupportedEntry(path);
        }
        type.parseZip(entry, issueId, null, fileName, zis, this);
        return true;
    }

    @Override
    public void parseTopic(PackageType.Entry entry, Integer issueId, String group, String zeName, InputStream zis) {
        LOG.warn(String.format("an entry for %s: %s is skipped for import packages", entry.getType(), zeName));
    }

    @Override
    public void parseUndefinedEntry(PackageType.Entry entry, String entryName, InputStream zis) throws Exception {
        if (entryName.endsWith(TO_EXCLUDE)) {
            parseDefaultEntry(packagePath + TO_EXCLUDE, zis, excluded, rps);
        } else if (entryName.endsWith(TO_INCLUDE)) {
            parseDefaultEntry(packagePath + TO_INCLUDE, zis, included, rps);
        }
    }

    private static void parseDefaultEntry(String path, InputStream zis, Collection<String> ret, IRepository rp)
            throws Exception {
        putFileToRepository(rp, zis, path);
        String content = InputUtils.readStreamToString(rp.getFile(path));
        String[] strs = content.split("\n");
        for (String str: strs) {
            ret.add(str.trim());
        }
    }

    private boolean isExcluded(String name) {
        return excluded.contains(name) || (!included.isEmpty() && !included.contains(name));
    }

    private boolean isExcludedByPubName(String pubName) {
        for (String name: excluded) {
            if (pubName.startsWith(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected CDSRMetaVO addReview(String basePath, String cdNumber, int pub, boolean statsData, String recordPath,
            int highPriority, CDSRMetaVO metaVO) {
        try {
            synchronized (IRecordCache.RECORD_LOCKER) {
                String pubName = RevmanMetadataHelper.buildPubName(cdNumber, pub);
                checkInCache(recordIds.get(pubName), cdNumber, basePath, false, rps, recordCache);
                BaseType bt = BaseType.getCDSR().get();
                CDSRMetaVO meta = helper().extractMetadata(
                        cdNumber, pub, recordPath, fullIssueNumber, bt, rps, recordCache);
                helper().validate(meta, rps);
                JatsMl3gAssetsManager.validateAndCreateThumbnails(basePath + cdNumber, rps);
                GroupVO groupVO = GroupVO.getGroup(cdNumber, pub, meta.getCochraneVersion(), meta.getGroupSid(),
                        recordCache);
                ICDSRMeta existedMeta = history.findExistedMeta(cdNumber, pub);
                if (existedMeta != null) {
                    history.check(existedMeta, meta);
                    meta.setHistoryNumber(existedMeta.getHistoryNumber());
                } else {
                    meta.setHistoryNumber(history.chooseFreeHistoricalNumber(cdNumber, pub));
                }
                meta.setId(rm.createRecord(bt, meta, groupVO, packageId, statsData, false, 0));
                recordIds.put(pubName, meta.getId());
                history.putImportedMeta(cdNumber, pub, meta);
                articles().add(meta);
                reviewCount++;
                return meta;
            }
        } catch (CmsException | CmsJTException ce) {
            excluded.add(RevmanMetadataHelper.buildPubName(cdNumber, pub));
            handleError(ce.get(), cdNumber, pub);
            
        } catch (Throwable tr) {
            excluded.add(RevmanMetadataHelper.buildPubName(cdNumber, pub));
            handleError(tr, cdNumber, pub);
        }
        return null;
    }

    @Override
    protected void addTranslation(BaseType bt, TranslatedAbstractVO tvo, Integer issueId) {
        String cdNumber = tvo.getName();
        String pubName = RevmanMetadataHelper.buildPubName(cdNumber, tvo.getPubNumber());
        if (isExcluded(pubName)) {
            LOG.warn(String.format("%s is excluded because of its article %s failed", tvo, pubName));
            return;
        }
        try {
            synchronized (IRecordCache.RECORD_LOCKER) {
                Integer recordId = checkInCache(recordIds.get(pubName), cdNumber, null, true, rps, recordCache);
                if (recordId == null) {
                    ICDSRMeta existedMeta = history.findExistedMeta(cdNumber, tvo.getPubNumber());
                    if (existedMeta == null) {
                        throw new CmsException("can't find related version to link");
                    }
                    int maxPub = history.getMaxPub(cdNumber);
                    tvo.setId(rm.createCDSRRecord(tvo, existedMeta, packageId, getTaDbId(issueId)));
                    recordIds.put(pubName, tvo.getId());
                    articles().add(tvo);
                    //return

                } else {
                    rm.addTranslation(tvo, packageId, getTaDbId(issueId), recordId, false);
                }
                translationCount++;
            }
        } catch (CmsException ce) {
            handleError(ce, cdNumber, tvo.getPubNumber());
        } catch (Throwable tr) {
            handleError(tr, cdNumber, tvo.getPubNumber());
        }
    }

    @Override
    protected Map<String, IRecord> handleResults() {
        Map<String, IRecord> ret = new HashMap<>();
        for (IRecord record: articles()) {
            String cdNumber = record.getName();
            ret.put(RevmanMetadataHelper.buildPubName(cdNumber, record.getPubNumber()), record);
        }
        return ret;
    }

    @Override
    protected void handleFailedRecord(BaseType baseType, Integer issueId, ErrorInfo err,
                                      StringBuilder errBuffer, String manuscriptNumber) {
        Object errEntity = err.getErrorEntity();
        ArchieEntry rel = (errEntity instanceof ArchieEntry) ? (ArchieEntry) errEntity : null;
        String recName = rel != null ? rel.toString() : errEntity.toString();

        MessageSender.addMessage(errBuffer, recName, err.getErrorDetail());
    }

    /**
     * It represents a full metadata with a history for required cd numbers
     */
    public static class CDSRHistory {

        private final IVersionManager vm;
        private final IResultsStorage rs;

        /** CD number -> MaxPubNumber:{PubNumber -> Meta}*/
        private final Map<String, Pair<Integer[], Map<Integer, ICDSRMeta>>> history = new HashMap<>();

        private final int validation = Property.get("cms.cochrane.jats.import.validation", "1").get().asInteger();

        private final boolean groupCheck = Property.getBooleanProperty(
                "cms.cochrane.jats.import.validation-group", true);
        private final boolean stageCheck = Property.getBooleanProperty(
                "cms.cochrane.jats.import.validation-stage", true);
        private final boolean statusCheck = Property.getBooleanProperty(
                "cms.cochrane.jats.import.validation-status", true);
        private final boolean issueCheck = Property.getBooleanProperty(
                "cms.cochrane.jats.import.validation-issue", true);

        public CDSRHistory(IVersionManager vm, IResultsStorage rs) {
            this.vm = vm;
            this.rs = rs;
        }

        private void putImportedMeta(String cdNumber, int pub, ICDSRMeta imported) {
            Pair<Integer[], Map<Integer, ICDSRMeta>> pair = history.get(cdNumber);
            pair.second.put(pub, imported);
            if (pub > pair.first[0]) {
                history.put(cdNumber, new Pair<>(new Integer[]{pub, imported.getHistoryNumber()}, pair.second));
            }
        }

        public ICDSRMeta findExistedMeta(String cdNumber, int pub) {
            return history.computeIfAbsent(cdNumber, v -> getHistory(cdNumber)).second.get(pub);
        }

        private Pair<Integer[], Map<Integer, ICDSRMeta>> getHistory(String cdNumber) {
            List<PrevVO> list = vm.getVersions(cdNumber);
            if (list.isEmpty()) {
                return new Pair<>(new Integer[] {0, 0}, Collections.emptyMap());
            }
            Map<Integer, ICDSRMeta> ret = new HashMap<>();
            PrevVO prev = list.remove(0);
            int maxPub = 0;
            Integer lastVersion = prev.version;
            ICDSRMeta last = getExistedMeta(cdNumber, prev, ret);
            if (last != null) {
                last.setHistoryNumber(RecordEntity.VERSION_LAST);
                maxPub = last.getPubNumber();
            }
            list.forEach(p -> getExistedMeta(cdNumber, p, ret));
            return new Pair<>(new Integer[] {maxPub, lastVersion}, ret);
        }

        private int getMaxPub(String cdNumber) {
            return history.get(cdNumber).first[0];
        }

        private ICDSRMeta getExistedMeta(String cdNumber, PrevVO prev, Map<Integer, ICDSRMeta> map)  {
            ICDSRMeta ret = rs.getCDSRMetadata(cdNumber, prev.pub);
            if (ret == null) {
                LOG.error(String.format("cannot find metadata for %s", prev.buildPubName()));
            } else {
                ret.setHistoryNumber(prev.version);
                map.put(prev.pub, ret);
            }
            return ret;
        }

        private Integer chooseFreeHistoricalNumber(String cdNumber, int pub) throws CmsException {
            Pair<Integer[], Map<Integer, ICDSRMeta>> scope = getHistory(cdNumber);
            if (scope.first[0] == 0 || scope.first[0] < pub) {
                throw new CmsException(String.format("can't import %s.pub%d over the latest scope", cdNumber, pub));
            }
            Integer ret = pub;
            for (Map.Entry<Integer, ICDSRMeta> entry: scope.second.entrySet()) {
                int curPub = entry.getKey();
                int curVersion = entry.getValue().getHistoryNumber();
                if (curVersion == RecordEntity.VERSION_LAST) {
                    curVersion = scope.first[1];
                }
                if (ret == curVersion) {
                    throw new CmsException(String.format(
                            "can't use %d version for %s.pub%d as it already exists for pub%d",
                            ret, cdNumber, pub, curPub));
                }
                if (curPub > pub && curVersion < ret || curPub < pub && curVersion > ret) {
                    throw new CmsException(String.format(
                            "can't use %d version for %s.pub%d as it's in wrong order with pub%d - %d",
                            ret, cdNumber, pub, curPub, curVersion));
                }
            }
            return ret;
        }

        public void check(ICDSRMeta existed, ICDSRMeta imported) throws CmsException {

            check(existed.getGroupSid(), imported.getGroupSid(), existed, "CRG group", true);
            if (!needCheck()) {
                return;
            }
            if (needGroupCheck()) {
                String groupTitle = imported.getGroupTitle();
                if (groupTitle == null || groupTitle.trim().isEmpty() || groupTitle.contains("Unknown")) {
                    throw new CmsException(getErrorStr("CRG group title",
                            groupTitle, existed.getGroupTitle(), existed.getId()));
                }
            }

            check(existed.getStage(), imported.getStage(), existed, "stage", needStageCheck());

            if (existed.getStatus() != imported.getStatus()
                    && RecordMetadataEntity.RevmanStatus.UNCHANGED.dbKey != existed.getStatus())  {
                String msg = getErrorStr("status",
                        RecordMetadataEntity.RevmanStatus.getStatusName(imported.getStatus()),
                        RecordMetadataEntity.RevmanStatus.getStatusName(existed.getStatus()), existed.getId());
                if (needStatusCheck() && RecordMetadataEntity.RevmanStatus.UNCHANGED.dbKey != existed.getStatus()) {
                    throw new CmsException(msg);
                }
                LOG.warn(msg);
            }

            check("" + existed.getCitationIssue(), "" + imported.getCitationIssue(), existed, "citation-last-changed",
                    needIssueCheck());
        }

        private static void check(String existed, String imported, ICDSRMeta meta, String attrName, boolean error)
                throws CmsException {
            if (!existed.equals(imported)) {
                if (error) {
                    throw new CmsException(getErrorStr(attrName, imported, existed, meta.getId()));
                }
                LOG.warn(String.format("%s - %s", meta, getErrorStr(attrName, imported, existed, meta.getId())));
            }
        }

        private boolean needCheck() {
            return validation > 0;
        }

        private boolean needGroupCheck() {
            return groupCheck;
        }

        private boolean needStageCheck() {
            return stageCheck;
        }

        private boolean needStatusCheck() {
            return statusCheck;
        }

        private boolean needIssueCheck() {
            return issueCheck;
        }

        private static String getErrorStr(String field, String newValue, String oldValue, int metaId) {
            //String optMsg = optional ? " (the check is optional and can be disabled)" : "";
            return String.format("the imported %s '%s' doesn't match to '%s' in legacy metadata [%d]",
                    field, newValue, oldValue, metaId);
        }
    }
}
