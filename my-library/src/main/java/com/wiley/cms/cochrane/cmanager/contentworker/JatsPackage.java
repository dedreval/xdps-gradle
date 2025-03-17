package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.notification.SuspendNotificationEntity;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/28/2019
 */
public class JatsPackage extends ArchiePackage {
    public static final String JATS_FOLDER = "jats";

    private static final Logger LOG = Logger.getLogger(JatsPackage.class);

    private static final Set<PackageType.EntryType> SUPPORTED_TYPES = new HashSet<>(Arrays.asList(
        PackageType.EntryType.ARTICLE, PackageType.EntryType.TOPIC, PackageType.EntryType.TA,
            PackageType.EntryType.TA_RETRACTED, PackageType.EntryType.UCS));

    private static final Set<PackageType.EntryType> SUPPORTED_EMBEDDED_TYPES = new HashSet<>(Arrays.asList(
         PackageType.EntryType.ARTICLE, PackageType.EntryType.MANIFEST, PackageType.EntryType.TA,
             PackageType.EntryType.STATS));

    int reviewCount;
    int translationCount;

    private List<TranslatedAbstractVO> translations;

    /** group name -> a topic.xml path in a package folder */
    private Map<String, String> topics;

    private Integer taDbId;

    JatsPackage(URI packageUri) throws DeliveryPackageException {
        super(packageUri);
    }

    JatsPackage(String packageFileName) {
        super(packageFileName);
    }

    public JatsPackage(URI packageUri, String dbName, int deliveryId, int fullIssueNumber) {
        super(packageUri, dbName, deliveryId, fullIssueNumber);
    }

    @Override
    public void parseImage(PackageType.Entry entry, String cdNumber, String zeName, InputStream zis) throws Exception {
        parseAsset(entry, zeName, Constants.JATS_FIG_DIR_SUFFIX, zis);
    }

    void parseStatsFile(PackageType.Entry entry, String zeName, InputStream zis) throws Exception {
        parseAsset(entry, zeName, Constants.JATS_STATS_DIR_SUFFIX, zis);
    }

    public void parseStatsFile(PackageType.Entry entry, @NotNull String cdNumber, String zeName, InputStream zis)
            throws Exception {
        PackageChecker.checkCdNumber(cdNumber, zeName);
    }

    @Override
    public Map<String, IRecord> extractData(Integer issueId, Integer dbId, PackageType pt, IFlowLogger logger)
            throws Exception {
        packageType = pt;
        flLogger = logger;
        IRecordCache cache = logger.getRecordCache();
        File tmp = File.createTempFile(Constants.TMP_STR + packageFileName, "");
        ZipInputStream zis = getZipStream(tmp);
        packagePath = FilePathBuilder.JATS.getPathToPackage(issueId, packageId);
        recordCache = logger.getRecordCache();
        String manuscriptNumber = "N/A";
        try {
            parseZip(issueId, zis, null, rps.getRealFilePath(packagePath));

        } catch (DeliveryPackageException de) {
            manuscriptNumber = getManuscriptNumberFromAriesHelper(manuscriptNumber);
            de.setManuscriptNumber(manuscriptNumber);
            BaseType bt = BaseType.find(getLibName()).get();
            MessageSender.sendFailedLoadPackageMessage(packageFileName, de.getMessage(),
                    bt.getShortName(), manuscriptNumber);
            throw de;

        } finally {
            tmp.delete();
        }
        manuscriptNumber = getManuscriptNumberFromAriesHelper(manuscriptNumber);
        handleTranslations(BaseType.getCDSR().get(), issueId);
        boolean onlyBeingProcessed = errors != null && handleFailedRecords(issueId, manuscriptNumber);
        handleTopics(issueId, onlyBeingProcessed, rps, cache);

        if (isAutRepeat(packageFileName)) {
            reb.commitWhenReady(rm);

        } else if (notifyOnReceived(dbId) != SuspendNotificationEntity.TYPE_NO_ERROR) {
            reb.cancelWhenReady(rm);
            throwCommonError(reb.getErrorMessage() != null
                ? reb.getErrorMessage() : "a call Archie 'on received' failed", NOTIFICATION_REC_ERROR_MSG,
                    IDeliveryFileStatus.STATUS_PICKUP_FAILED, ILogEvent.PRODUCT_NOTIFIED_ON_RECEIVED);
        }
        LOG.info("%s [%d] contains: %d DOIs with %d review(s), %d translation(s); %d topic(s)",
            packageFileName, packageId, getArticleCount(), getReviewCount(), getTranslationCount(), getTopicsCount());

        if (errors != null) {
            errors.forEach(this::completeFailedRecord);
        }

        checkEmpty(issueId, onlyBeingProcessed);
        return getResults();
    }

    private String getManuscriptNumberFromAriesHelper(String originalManuscriptNumber) {
        String manuscriptNumber = originalManuscriptNumber;
        if (this instanceof AriesSFTPPackage) {
            manuscriptNumber = ((AriesSFTPPackage) this).getAriesImportFile();
        }
        return manuscriptNumber;
    }

    @Override
    protected int notifyOnReceived(Integer dbId) {
        return RevmanPackage.notifyReceived(reb, flLogger);
    }

    @Override
    protected void checkEmpty(Integer issueId, boolean onlyBeingProcessed) throws Exception {
        if (isEmpty()) {
            if (onlyBeingProcessed) {
                AriesConnectionManager.copyPackageToBeingProcessedFolder(packageFileName, packagePath, rps);
            }
            if (!onlyBeingProcessed && errors == null && topics != null) {
                throw new DeliveryPackageException("a package has only topics",
                        IDeliveryFileStatus.STATUS_RND_NOT_STARTED, true, null);
            }
            super.checkEmpty(issueId, onlyBeingProcessed);
        }
    }

    @Override
    public int getArticleCount() {
        return recordIds.size();
    }

    public int getTranslationCount() {
        return translationCount;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    private int getTopicsCount() {
        return topics != null ? topics.size() : 0;
    }

    @Override
    protected final String getArticleBasePath(String pubName, String cdNumber) {
        return packagePath + getJatsFolder(pubName, cdNumber, null) + FilePathCreator.SEPARATOR;
    }

    String getTABasePath(Integer issueId, TranslatedAbstractVO tvo, String jatsFolder) {
        return FilePathBuilder.TR.getPathToJatsTADir(issueId, packageId, tvo.getLanguage(), jatsFolder)
                       + FilePathCreator.SEPARATOR;
    }

    protected String getJatsFolder(String pubName, String cdNumber, String language) {
        return cdNumber;
    }

    protected boolean parseZipEntry(Integer issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo, ZipEntry ze)
            throws Exception {
        String path = ze.getName();
        PackageType.Entry entry = PackageChecker.getPackageEntry(path, packageType);
        PackageType.EntryType type = entry.getType();
        if (PackageType.EntryType.UNDEF == type) {
            return false;
        }

        String[] pathParts = PackageChecker.replaceBackslash2Forward(path).split("/");
        String group = pathParts[0].toUpperCase();
        String fileName = pathParts[pathParts.length - 1];

        if (!isEntryTypeSupported(type, false)) {
            throw type.throwUnsupportedEntry(path);
        }

        type.parseZip(entry, issueId, group, fileName, zis, this);
        return true;
    }

    @Override
    public Set<PackageType.EntryType> getEntryTypesSupported() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Set<PackageType.EntryType> getEmbeddedEntryTypesSupported() {
        return SUPPORTED_EMBEDDED_TYPES;
    }

    @Override
    public void parseArticleZip(PackageType.Entry entry, Integer issueId, @NotNull String fileName,
                                ZipInputStream externalZis, boolean ta) throws Exception {
        ZipInputStream zis = new ZipInputStream(externalZis);
        PackageArticleResults<String> results = new PackageArticleResults<>();
        ZipEntry ze;
        ZipEntry zeLast = null;
        String pubName = FileUtils.cutExtension(fileName);
        String[] nameParts = pubName.split(Constants.NAME_SPLITTER);
        String cdNumber = nameParts[0];
        TranslatedAbstractVO tvo = null;
        String basePath;
        try {
            if (ta) {
                tvo = TranslatedAbstractsPackage.parseFileName(nameParts);
                basePath = getTABasePath(issueId, tvo, getJatsFolder(pubName, cdNumber, tvo.getOriginalLanguage()));
            } else {
                basePath = getArticleBasePath(pubName, cdNumber);
            }
            results.setBasePath(basePath).setCdNumber(cdNumber).setPublisherId(pubName);

            while ((ze = zis.getNextEntry()) != null) {
                zeLast = ze;
                if (ze.isDirectory()) {
                    continue;
                }
                String zeName = ze.getName();
                String path = basePath + zeName;
                PackageType.Entry darEntry = entry.match(zeName);
                if (darEntry == null) {
                    LOG.warn("%s - an unknown entry: %s", fileName, zeName);
                    putFileToRepository(rps, zis, path);
                    continue;
                }
                PackageType.EntryType type = darEntry.getType();
                if (isEntryTypeSupported(type, true)) {
                    results.setCurrentPath(path);
                    type.parseFinal(entry, zeName, zis, this, results);
                }
                putFileToRepository(rps, zis, path);
            }

            checkFirstZipEntry(fileName, zeLast);

            String recordPath = results.getArticleResult();
            if (recordPath == null) {
                recordPath = results.getTranslationResult();
            }
            String manifestPath = results.getManifestResult();

            if (manifestPath != null) {
                helper().checkManifest(basePath, manifestPath, rps);
            } else {
                LOG.warn(String.format("%s contains no manifest file", fileName));
            }
            if (recordPath == null) {
                throw new CmsException(String.format("%s contains no document file", fileName));
            }
            if (ta) {
                unpackTranslation(tvo, basePath, recordPath, fileName);
            } else {
                addReview(basePath, cdNumber, RevmanMetadataHelper.parsePubNumber(pubName),
                        results.getStatsResult(), recordPath, 0, null);
            }

        } catch (Throwable tr) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(new ArchieEntry(cdNumber), tr.getMessage()), errors());
        }
    }

    @Override
    public Object parseEmbeddedArticle(PackageType.Entry entry, String zeName, InputStream zis,
                                       PackageArticleResults results) throws Exception {
        PackageChecker.checkCdNumber(results.getCdNumber(), zeName);
        return results.getCurrentPath();
    }

    @Override
    public void parseTopic(PackageType.Entry entry, Integer issueId, String group, String zeName, InputStream zis)
            throws Exception {
        String path = FilePathBuilder.JATS.getPathToTopics(issueId, packageId, group);
        putFileToRepository(rps, zis, path);
        topics().put(group, path);
    }

    private void handleTopics(Integer issueId, boolean onlyBeingProcessed, IRepository rp, IRecordCache cache)
            throws Exception {
        if (topics == null) {
            return;
        }

        if (onlyBeingProcessed) {
            topics = null;
            return;
        }

        for (Map.Entry<String, String> entry: topics.entrySet()) {
            String groupName = entry.getKey();
            String pathTo = FilePathBuilder.getPathToTopics(issueId, groupName);
            String pathFrom = entry.getValue();
            rp.putFile(pathTo, rp.getFile(pathFrom));
            cache.setTopic(groupName, InputUtils.readStreamToString(rp.getFile(pathFrom)));
        }
    }

    @Override
    protected CDSRMetaVO addReview(String basePath, String cdNumber, int pub, boolean statsData, String recordPath,
                                   int highPriority, CDSRMetaVO metaVO) {
        CDSRMetaVO meta = super.addReview(basePath, cdNumber, pub, statsData, recordPath, highPriority, metaVO);
        if (meta != null) {
            if (isAut()) {
                reb.addContent(meta.asSuccessfulElement(reb, flLogger));
            }
            reviewCount++;
        }
        return meta;
    }

    @Override
    protected int validateAndCreate(BaseType bt, String basePath, String cdNumber, int pub, boolean statsData,
                                    int highPriority, CDSRMetaVO meta) throws Exception {
        helper().validate(meta, rps);
        try {
            JatsMl3gAssetsManager.validateAndCreateThumbnails(basePath + cdNumber, rps);
        } catch (CmsException e)  {
            // pass meta info to handle a record correctly
            throw CmsException.create(meta, ErrorInfo.Type.CONTENT, e.getMessage());
        }
        return rm.createRecord(bt, meta, GroupVO.getGroup(cdNumber, pub, meta.getCochraneVersion(), meta.getGroupSid(),
                recordCache), packageId, statsData, isAut(), highPriority);
    }

    @Override
    protected String checkForSPD(BaseType bt, String cdNumber, String pubName, int pub, boolean spd,
                                 boolean spdCanceled, boolean spdReprocess) throws CmsException {
        String ret = spdCanceled ? checkSPDCancelled(cdNumber, pubName, recordCache)
                : checkSPD(bt, cdNumber, pubName, pub, spd, spdReprocess, recordCache);
        if (ret != null && isAut()) {
            reb.sPD(false, false);
        }
        return ret;
    }

    public static String checkSPD(String cdNumber, String pubName, int pub, IRecordCache cache) {
        return checkSPD(BaseType.getCDSR().get(), cdNumber, pubName, pub, false, false, cache);
    }

    private void handleTranslations(BaseType baseType, Integer issueId) {
        if (translations != null) {
            translations.forEach(tr -> addTranslation(baseType, tr, issueId));
        }
    }

    protected void addTranslation(BaseType baseType, TranslatedAbstractVO tvo, Integer issueId) {
        String cdNumber = tvo.getName();
        try {
            synchronized (IRecordCache.RECORD_LOCKER) {
                Integer recordId = checkInCache(recordIds.get(cdNumber), cdNumber, null, true, rps, recordCache);
                boolean aut = isAut();
                if (recordId == null) {
                    recordIds.put(cdNumber, rm.createRecord(baseType, tvo, packageId, getTaDbId(issueId), false, aut));
                    recordCache.addRecord(cdNumber, false);
                    articles().add(tvo);
                    flLogger.updateProduct(tvo, tvo.getStage(), null, tvo.getSid(), null, null, 0);
                } else {
                    rm.addTranslation(tvo, packageId, getTaDbId(issueId), recordId, aut);
                }
                if (aut) {
                    reb.addContent(tvo.asSuccessfulElement(reb, flLogger));
                }
                translationCount++;
            }
        } catch (CmsException | CmsJTException ce) {
            handleError(ce.get(), tvo);
        } catch (Throwable tr) {
            handleError(tr, tvo);
        }
    }

    @Override
    public void parseTranslationRetracted(Integer issueId, String fileName, InputStream zis) {
        String[] nameParts = FileUtils.cutExtension(fileName).split(Constants.NAME_SPLITTER);
        String cdNumber = nameParts[0];
        try {
            TranslatedAbstractVO tvo = TranslatedAbstractsPackage.parseFileName(nameParts);
            tvo.setDeleted(true);
            String basePath = getTABasePath(issueId, tvo, cdNumber);
            String recordPath = basePath + fileName;
            putFileToRepository(rps, zis, recordPath);

            unpackTranslation(tvo, basePath, recordPath, fileName);

        } catch (Throwable tr) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(new ArchieEntry(cdNumber), tr.getMessage()), errors());
        }
    }

    private void validate(TranslatedAbstractVO tvo) throws Exception {
        if (!tvo.isDeleted()) {
            helper().validate(tvo, rps);
        }
    }

    void unpackTranslation(TranslatedAbstractVO tvo, String basePath, String recordPath, String fileName) {
        String cdNumber = tvo.getName();
        try {
            BaseType bt = BaseType.getCDSR().get();
            checkInCache(recordIds.get(cdNumber), cdNumber, basePath, true, rps, recordCache);
            flLogger.onProductReceived(packageFileName, packageId, tvo, vendor, null, bt.hasSFLogging() && isAut());

            ContentHelper helper = helper();
            helper.extractMetadata(tvo, recordPath, fileName, rps);

            String errSpd = checkForSPD(bt, cdNumber, tvo.getPubName(), tvo.getPubNumber(),
                    false, false, false);
            if (errSpd != null) {
                throw CmsException.createForValidation(tvo, errSpd, true);
            }

            flLogger.updateProduct(tvo, null, null, tvo.getSid(), null, null, 0);
            validate(tvo);
            flLogger.onProductValidated(tvo, bt.hasSFLogging() && isAut(), false);
            translations().add(tvo);

        } catch (CmsException ce) {
            handleError(ce, tvo);
        } catch (Throwable tr) {
            handleError(tr, tvo);
        }
    }

    private void handleError(Throwable tr, TranslatedAbstractVO tvo) {
        RecordHelper.handleErrorMessage(new ErrorInfo<>(tvo, tr.getMessage()), errors(), false);
        logError(tvo.toString(), tr);
    }

    private void handleError(CmsException ce, TranslatedAbstractVO tvo) {
        ErrorInfo ei = ce.hasErrorInfo() ? ce.getErrorInfo() : new ErrorInfo<>(tvo, ce.getMessage());
        RecordHelper.handleErrorMessage(ei, errors(), false);
        logError(tvo.toString(), ce);
    }

    @Override
    protected ContentHelper helper() {
        if (contentHelper == null) {
            contentHelper = new JatsHelper();
        }
        return contentHelper;
    }

    private Map<String, String> topics() {
        if (topics == null) {
            topics = new HashMap<>();
        }
        return topics;
    }

    private List<TranslatedAbstractVO> translations() {
        if (translations == null) {
            translations = new ArrayList<>();
        }
        return translations;
    }

    Integer getTaDbId(Integer issueId) {
        if (taDbId == null) {
            taDbId = rm.findDb(issueId, DatabaseEntity.CDSR_TA_KEY);
        }
        return taDbId;
    }
}
