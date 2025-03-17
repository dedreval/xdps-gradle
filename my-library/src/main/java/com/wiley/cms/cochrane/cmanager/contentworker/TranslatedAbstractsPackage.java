package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.AbstractZipPackage;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsCorrector;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.notification.SuspendNotificationEntity;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Pair;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.InputUtils;

/**
 * @author Sergey Trofimov
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *           It should be Integer for all the calls related to RevmanPackage as it's supposed to keep pub numbers.
 *           Another case it doesn't use and can be any object.
 */

public class TranslatedAbstractsPackage extends AbstractZipPackage {
    public static final String PACKAGE_NAME_PREFIX = "translated_abstracts_";
    public static final String AUT_PACKAGE_NAME_PREFIX = "translated_abstracts_aut_";

    private static final int PUB_SUFFIX_NUM_NAME = 2;
    private static final String NOTIFICATION_ERROR_MSG = "notification error for received translations";

    private boolean commonRevman = false;

    private Set<String> recordsToGenerate = new HashSet<>();

    private ArchieResponseBuilder reb = null;
    private IRecordCache cache = null;
    private IVersionManager vm = null;
    private IRecordManager rm = CochraneCMSBeans.getRecordManager();
    private Integer taDbId = null;

    public TranslatedAbstractsPackage(int packageId, ArchieResponseBuilder reb) {
        super();
        this.packageId = packageId;
        this.reb = reb;
    }

    public TranslatedAbstractsPackage(URI packageUri) throws DeliveryPackageException {
        super(packageUri);
    }

    public TranslatedAbstractsPackage(RevmanPackage revmanPackage, int issueId) {
        super();
        taDbId = rm.findDb(issueId, DatabaseEntity.CDSR_TA_KEY);
        packageFileName = revmanPackage.getPackageFileName();
        packageUri = revmanPackage.getPackageUri();
        packageId = revmanPackage.getPackageId();

        if (revmanPackage.isAut()) {
            reb = new ArchieResponseBuilder(false, true, packageFileName.replace(FilePathCreator.ZIP_EXT, "0"),
                    packageId);
        }
        commonRevman = true;
    }

    @Override
    public String getLibName() {
        return CochraneCMSPropertyNames.getCDSRDbName();
    }

    private static ArchieResponseBuilder getRevmanElementBuilder(String packageName, int dfId) {

        ArchieResponseBuilder ret = null;
        if (DeliveryPackage.isArchieAut(packageName) && (CochraneCMSPropertyNames.canArchieAut()
                || ArchiePackage.isAutReprocess(packageName))) {
            ret = new ArchieResponseBuilder(false, true, packageName.replace(FilePathCreator.ZIP_EXT, ""), dfId);
        }
        return ret;
    }

    public boolean isAut() {
        return reb != null;
    }

    @Override
    public void parsePackageName() throws DeliveryPackageException {
        reb = getRevmanElementBuilder(packageFileName, packageId);
    }

    InputStream getResponse() {
        return null;
    }

    List<String> getRecordsToGenerate() {
        return new ArrayList<>(recordsToGenerate);
    }

    void clearCache() {
        if (cache != null) {
            cache.removeRecords(recordsToGenerate, false);
        }
    }

    void extractData(DeliveryPackageInfo packageInfo, IActivityLog logger, IRecordCache cache)
            throws Exception {
        rs.setDeliveryFileStatus(packageId, IDeliveryFileStatus.STATUS_PICKED_UP, true);
        extractData(packageInfo, null, new HashSet<>(), logger, cache);
        rs.setDeliveryFileStatus(packageId, IDeliveryFileStatus.STATUS_UNZIPPED, true);
    }

    void extractData(DeliveryPackageInfo packageInfo, Map<String, Integer[]> existedReviews,
        Set<String> failedNames, IActivityLog logger, IRecordCache processCache) throws Exception {

        if (taDbId == null) {
            throw new DeliveryPackageException("No database for CDSR translations",
                    IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
        cache = processCache;
        vm = CochraneCMSBeans.getVersionManager();

        File tmp = File.createTempFile("tmp" + packageFileName, "");
        ZipInputStream zis = getZipStream(tmp);
        List<ErrorInfo<TranslatedAbstractVO>> failedQa = parseAbstracts(zis, packageInfo, existedReviews, failedNames);
        try {
            zis.close();
        } catch (IOException e) {
            throw new DeliveryPackageException(e.getMessage(), IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
        tmp.delete();

        if (!failedQa.isEmpty()) {
            prepareErrors(packageId, rs.getIssue(packageInfo.getIssueId()).getFullNumber(), failedQa, logger);
        }
        notifyReceived();
    }

    private void prepareErrors(int packageId, int issueNumber, List<ErrorInfo<TranslatedAbstractVO>> failedQa,
                               IActivityLog logService) {
        StringBuilder failedSB = new StringBuilder();
        StringBuilder warnSB = null;

        for (ErrorInfo<TranslatedAbstractVO> err : failedQa) {
            if (!err.isError()) {

                if (warnSB == null) {
                    warnSB = new StringBuilder();
                }
                addErrorMsg(packageId, issueNumber, err, warnSB, true, logService);
                continue;
            }

            addErrorMsg(packageId, issueNumber, err, failedSB, false, logService);

            if (reb != null) {
                reb.addContent(reb.asErrorElement(err.getErrorEntity(), err));
            }
        }
        if (failedSB.toString().length() > 0) {
            sendErrorMsg(MessageSender.MSG_TITLE_TA_LOAD_FAILED, failedSB, BaseType.getCDSR().get().getShortName());
        }
        if (warnSB != null) {
            sendErrorMsg(MessageSender.MSG_TITLE_TA_LOAD_WARNINGS, warnSB, BaseType.getCDSR().get().getShortName());
        }
    }

    private void sendErrorMsg(String title, StringBuilder failedSB, String dbName) {

        Map<String, String> notifyMessage = new HashMap<>();
        String identifiers =  MessageSender.getCDnumbersFromMessageByPattern(failedSB.toString(), notifyMessage);

        failedSB.delete(failedSB.length() - 2, failedSB.length());

        notifyMessage.put(MessageSender.MSG_PARAM_RECORD_ID, identifiers);
        notifyMessage.put(MessageSender.MSG_PARAM_DELIVERY_FILE, packageFileName);
        notifyMessage.put(MessageSender.MSG_PARAM_DATABASE, dbName);
        notifyMessage.put(MessageSender.MSG_PARAM_LIST, failedSB.toString());
        MessageSender.sendMessage(title, notifyMessage);
    }

    private void addErrorMsg(int packageId, int issueNumber, ErrorInfo<TranslatedAbstractVO> err,
                             StringBuilder failedSB, boolean warn, IActivityLog logService) {
        String name = err.getErrorEntity().getName();
        String msg = err.getErrorDetail();

        MessageSender.addMessage(failedSB, err.getErrorEntity().toString(), msg);

        if (warn) {
            logService.logRecordWarning(ILogEvent.PACKAGE_UNZIPPED, packageId, name,
                    BaseType.getCDSR().get().getDbId(), issueNumber, msg);
        } else {
            logService.logRecordError(ILogEvent.DATA_EXTRACTION_FAILED, packageId, name,
                    BaseType.getCDSR().get().getDbId(), issueNumber, msg);
        }
    }

    private List<ErrorInfo<TranslatedAbstractVO>> parseAbstracts(ZipInputStream zis,
        DeliveryPackageInfo packInfo, Map<String, Integer[]> existedReviews, Set<String> failedNames)
        throws DeliveryPackageException {

        boolean skipSuccess = true;

        ZipEntry ze;
        List<ErrorInfo<TranslatedAbstractVO>> failedQa = new ArrayList<>();
        Map<String, Pair<Integer, Set<String>>> records = new HashMap<>();
        int limit = CochraneCMSPropertyNames.getArchieDownloaderLimit();
        try {
            while ((ze = zis.getNextEntry()) != null) {

                if (ze.isDirectory()) {
                    continue;
                }

                Pair<String, String> fileName = commonRevman ? RevmanPackage.getTranslationFileName(ze.getName())
                        : new Pair<>(ze.getName(), null);
                if (fileName == null) {
                    continue;
                }

                if (skipSuccess && ArchiePackage.SUCCESS_LOG.equals(fileName.first)) {
                    skipSuccess = false;
                    continue;
                }

                if (ArchiePackage.ERROR_LOG.equals(fileName.first)) {
                    throw new Exception(InputUtils.readStreamToString(zis));
                }

                TranslatedAbstractVO record = parseFileName(fileName.first);
                String cdNumber = record.getName();

                synchronized (IRecordCache.RECORD_LOCKER) {
                    if (!validateInCache(record, failedQa, existedReviews == null ? null : existedReviews.keySet(),
                            records.containsKey(cdNumber) ? records.get(cdNumber).second : null, packInfo)) {
                        continue;
                    }

                    record.setPath(ze.getName());

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    IOUtils.copy(zis, bos);
                    InputStream proxyStream = new ByteArrayInputStream(bos.toByteArray());
                    if (parseAbstract(record, proxyStream, failedQa, packInfo, existedReviews, failedNames, fileName)) {
                        addInCache(record, cdNumber, record.getLanguage(), existedReviews, records);
                    }
                }
                int sumCount = existedReviews != null ? existedReviews.size() : records.size();
                if (limit > 0 && sumCount == limit) {
                    LOG.info(String.format(
                        "%d RevMan translations or articles were picked up while a total limit is %d"
                            + " - package parsing is suspended", sumCount, limit));
                    break;
                }
            }
        } catch (Exception e) {
            throw new DeliveryPackageException("Couldn't parse package: " + e.getMessage(),
                IDeliveryFileStatus.STATUS_CORRUPT_ZIP);
        }
        return failedQa;
    }

    private void addInCache(TranslatedAbstractVO record, String cdNumber, String language,
        Map<String, Integer[]> existedReviews, Map<String, Pair<Integer, Set<String>>> records)
            throws CmsJTException {

        cache.addRecord(cdNumber, false);
        Pair<Integer, Set<String>> langs = records.get(cdNumber);
        if (langs == null) {
            Integer[] pubAndId = existedReviews.get(cdNumber);
            Integer id = pubAndId != null ? pubAndId[1] : null;
            if (id == null) {
                id  = rm.createRecord(BaseType.getCDSR().get(), record, packageId, taDbId, false, isAut());
            } else {
                rm.addTranslation(record, packageId, taDbId, id, isAut());
            }
            langs = new Pair<>(id, new HashSet<>());
            records.put(cdNumber, langs);

        } else  {
            // just add a translation record
            rm.addTranslation(record, packageId, taDbId, langs.first, isAut());
        }
        langs.second.add(language);

        if (reb != null) {
            reb.addContent(reb.asSuccessfulElement(record));
        }
    }

    private boolean parseAbstract(TranslatedAbstractVO record, InputStream validStream,
        List<ErrorInfo<TranslatedAbstractVO>> failedQa,  DeliveryPackageInfo packageInfo,
        Map<String, Integer[]> existedReviews, Set<String> failedNames, Pair<String, String> fileName)
        throws DeliveryPackageException {

        boolean isParsed = true;
        try {
            if (!TranslatedAbstractsCorrector.parseAbstract(validStream, record, failedQa)) {
                return false;
            }
            String cdNumber = record.getName();
            if (record.isDoiExist()) {

                if (!validateXmlVersion(record, failedQa, fileName)
                    || !validateSid(record, failedQa)
                    || !validateFailedReviews(record, failedQa, failedNames)
                    || !validateLanguage(record, failedQa)
                    || !validateDoi(record, failedQa, existedReviews, fileName)
                    || !validateState(record, failedQa)) {

                    isParsed = false;
                }

                String errSpd = JatsPackage.checkSPD(cdNumber, record.getPubName(), record.getPubNumber(), cache);
                if (errSpd != null) {
                    failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.VALIDATION_SPD, errSpd));
                    isParsed = false;
                }

            } else {
                failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT,
                    "translations of old format is not supported"));
                isParsed = false;
            }

            if (isParsed) {
                packageInfo.addTranslation(fileName.second, fileName.first, record);
                updateRecordsToGeneration(cdNumber, record, existedReviews);
            }

        } catch (Exception e) {
            throw new DeliveryPackageException("Problem during parsing: " + e.getMessage(),
                IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        } finally {
            IOUtils.closeQuietly(validStream);
        }

        return isParsed;
    }

    void clear(boolean cache) {
        if (cache) {
            clearCache();
        }
    }

    private boolean validateState(TranslatedAbstractVO tvo, List<ErrorInfo<TranslatedAbstractVO>> failedQa) {
        boolean ret = true;
        if (tvo.isDeleted()) {

            DbRecordVO ta = rm.getTranslation(RecordHelper.buildRecordNumberCdsr(tvo.getName()), tvo.getLanguage());
            if (ta == null) {
                ErrorInfo<TranslatedAbstractVO> err = new ErrorInfo<>(
                        tvo, getErrorType(), "the retracted translation not exists or was deleted");
                failedQa.add(err);
                ret = false;

            } else if (ta.isDeleted()) {
                ErrorInfo<TranslatedAbstractVO> err = new ErrorInfo<>(
                        tvo, getErrorType(), "the retracted translation already was deleted");
                failedQa.add(err);
                ret = false;
            }
        }

        return ret;
    }

    private ErrorInfo.Type getErrorType() {
        return RevmanPackage.isAutReprocess(packageFileName) ? ErrorInfo.Type.SYSTEM : ErrorInfo.Type.CONTENT;
    }

    private boolean validateSid(TranslatedAbstractVO record, List<ErrorInfo<TranslatedAbstractVO>> failedQa) {

        if (isAut() && !record.isSidExist())  {

            ErrorInfo<TranslatedAbstractVO> err = new ErrorInfo<>(
                    record, ErrorInfo.Type.CONTENT, "has a null identifier");
            failedQa.add(err);
            LOG.error(err);
            return false;
        }

        return true;
    }

    private boolean validateXmlVersion(TranslatedAbstractVO record, List<ErrorInfo<TranslatedAbstractVO>> failedQa,
        Pair<String, String> fileName) {

        boolean thisVersion3 = record.isVersion3();
        boolean ret = true;
        //if (thisVersion3) {
        if (fileName.second == null) {
            ret = false;
            failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.SYSTEM,
                    "a package structure is not supported for version 3"));
        }
        //} else {
        //    failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, "a version less than 3 is not supported"));
        //    ret = false;
        //}
        return ret;
    }

    private static boolean validateLanguage(TranslatedAbstractVO record, List<ErrorInfo<TranslatedAbstractVO>> errs) {
        ErrorInfo<TranslatedAbstractVO> err = validateLanguage(record);
        if (err == null) {
            return true;
        }
        errs.add(err);
        return false;
    }

    static ErrorInfo<TranslatedAbstractVO> validateLanguage(TranslatedAbstractVO record) {
        if (record.isLanguageNotExist()) {
            String originalLang = record.getOriginalLanguage();

            return originalLang != null && TranslatedAbstractVO.getMappedLanguage(originalLang) == null
                    ? new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, String.format(
                            "doesn't support the '%s' language code", originalLang))
                    : new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, "doesn't content a language meta-data");
        }
        return null;
    }

    private boolean validateFailedReviews(TranslatedAbstractVO record, List<ErrorInfo<TranslatedAbstractVO>> failedQa,
        Set<String> failedNames) {

        if (!failedNames.contains(record.getName()))  {
            return true;
        }
        failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, record.getName()
                + " - the parent review of the translation was failed"));
        return false;
    }

    private boolean validateInCache(TranslatedAbstractVO record, List<ErrorInfo<TranslatedAbstractVO>> failedQa,
        Set<String> existedReviews, Set<String> languages, DeliveryPackageInfo packageInfo) {

        String cdNumber = record.getName();
        packageInfo.addStatAll();

        if (!cache.containsRecord(cdNumber) || (existedReviews != null && existedReviews.contains(cdNumber))
                || (languages != null && !languages.contains(record.getLanguage()))) {
            return true;
        }
        packageInfo.addStatBeingProcessed();
        failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.SYSTEM, MessageSender.MSG_RECORD_PROCESSED));
        return false;
    }

    private boolean validateDoi(TranslatedAbstractVO record, List<ErrorInfo<TranslatedAbstractVO>> failedQa,
        Map<String, Integer[]> existedReviews, Pair<String, String> fileName) {

        boolean ret = true;
        String recName = record.getName();

        if (recName.equals(record.getSid())) {

            record.setSid(null);
            failedQa.add(new ErrorInfo<>(record, false, ErrorInfo.Type.CONTENT,
                "the 'ID' attribute of a translation is equal to the 'CD Number' of its record", ""));
        }

        int pubNumDoi = RevmanMetadataHelper.parsePubNumber(record.getDoi());
        int pubNumTa = record.getPubNumber();
        if (pubNumDoi != pubNumTa) {
            failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, String.format(
                "conflicting pub number: metadata has pub%d, but file name is %s", pubNumDoi, fileName.first)));
            ret = false;

        } else if (!record.isDeleted() && existedReviews != null && existedReviews.containsKey(recName)) {

            Integer pubNumReview = existedReviews.get(recName)[0];
            if (pubNumReview == null || !pubNumReview.equals(pubNumTa)) {
                failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, String.format(
                    "a translation pub%d doesn't match a review pub%s", pubNumTa, pubNumReview)));
                ret = false;
            }
            return ret;
        }

        if (ret) {
            PrevVO p = vm.getLastVersion(recName);
            if (p == null) {
                failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT,
                    "entire version of a review doesn't exist"));
                ret = false;

            } else if (!record.isDeleted() && !record.getDoi().equals(p.buildDoi())) {
                failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, String.format(
                    "a doi %s of a translation doesn't match a latest entire version of the review %s",
                        record.getDoi(), p.buildDoi())));
                ret = false;

            }
            // XDPS-1120
            //else if (!p.getGroupName().equalsIgnoreCase(fileName.second)) {
            //    failedQa.add(new ErrorInfo<>(record, ErrorInfo.Type.CONTENT, String.format(
            //        "CRG folder - %s of a translation doesn't match a latest CRG group of the review - %s",
            //            fileName.second, p.getGroupName())));
            //    ret = false;
            //}
        }

        return ret;
    }

    private void updateRecordsToGeneration(String recName, TranslatedAbstractVO vo,
                                           Map<String, Integer[]> existedReviews) {
        if (existedReviews == null) {
            recordsToGenerate.add(recName);

        } else if (!existedReviews.containsKey(recName)) {
            recordsToGenerate.add(recName);
            Integer[] ids = {vo.getPubNumber(), null};
            existedReviews.put(recName, ids);
        }
    }

    public static Pair<String, String> parseLanguage(String filename) {
        String name = FileUtils.cutExtension(filename);
        String[] parts = name.split(Constants.NAME_SPLITTER);
        return parts.length > 1 ? new Pair<>(parts[0], parts[parts.length - 1]) : new Pair<>(null, null);
    }

    public static TranslatedAbstractVO parseFileName(String filename) throws Exception {

        boolean deleted = filename.endsWith(Extensions.RETRACTED);
        String name = FileUtils.cutExtension(filename);
        String[] parts = name.split(Constants.NAME_SPLITTER);

        TranslatedAbstractVO ret = parseFileName(parts);

        ret.setDeleted(deleted);
        return ret;
    }

    public static TranslatedAbstractVO parseFileName(String[] filenameParts) throws Exception {
        if (filenameParts.length > 1) {

            String language = filenameParts[filenameParts.length - 1];
            TranslatedAbstractVO ret = new TranslatedAbstractVO(filenameParts[0]);
            ret.setLanguage(language);

            if (filenameParts.length > PUB_SUFFIX_NUM_NAME) {
                ret.setPubNumber(filenameParts[PUB_SUFFIX_NUM_NAME - 1]);
            }
            return ret;

        }
        throw new Exception("cannot parse a translation file name");
    }

    private boolean notifyReceived() throws Exception {
        if (reb == null || RevmanPackage.checkEmpty(reb)) {
            return true;
        }

        LOG.debug("try notifying on translations receive ...");
        if (RevmanPackage.isAutRepeat(packageFileName)) {
            reb.commitWhenReady(CochraneCMSBeans.getRecordManager());

        } else if (RevmanPackage.notifyReceived(reb, null) != SuspendNotificationEntity.TYPE_NO_ERROR) {

            reb.cancelWhenReady(CochraneCMSBeans.getRecordManager());
            cache.removeRecords(recordsToGenerate, false);
            throw new DeliveryPackageException(NOTIFICATION_ERROR_MSG, IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
        return true;
    }
}
