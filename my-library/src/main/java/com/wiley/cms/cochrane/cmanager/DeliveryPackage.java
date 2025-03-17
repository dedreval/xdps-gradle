package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieResponseBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.CCAWorker;
import com.wiley.cms.cochrane.cmanager.contentworker.ContentHelper;
import com.wiley.cms.cochrane.cmanager.contentworker.GoManifest;
import com.wiley.cms.cochrane.cmanager.contentworker.IPackageParser;
import com.wiley.cms.cochrane.cmanager.contentworker.PackageArticleResults;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IKibanaRecord;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.IssueDate;
import com.wiley.cms.notification.SuspendNotificationEntity;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.XmlUtils;
import com.wiley.tes.util.res.Res;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.wiley.cms.cochrane.cmanager.contentworker.AriesSFTPPackage.PACKAGE_HANDLED_ERROR;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 11.12.2006
 */
public class DeliveryPackage extends AbstractZipPackage implements IPackageParser {
    private static final int BUFFER_SIZE = 1024;
    private static final int NUMBER_DIRS = 4;
    private static final int THREE = 3;

    private static final Set<PackageType.EntryType> SUPPORTED_TYPES = new HashSet<>(Arrays.asList(
            PackageType.EntryType.ARTICLE, PackageType.EntryType.IMAGE));

    protected String libName;
    protected String vendor;
    protected Integer fullIssueNumber;
    protected String pckTimestamp;
    protected PackageType packageType;
    protected String packagePath;
    protected List<IRecord> articles;
    protected List<ErrorInfo> errors;
    protected final Map<String, Integer> recordIds = new HashMap<>();
    protected ContentHelper contentHelper;
    protected IFlowLogger flLogger;
    protected IRecordManager rm;
    protected IRecordCache recordCache;
    protected final IRepository rps = RepositoryFactory.getRepository();

    public DeliveryPackage(URI packageUri, int deliveryId, String packageName) {
        super(packageUri, packageName, deliveryId);
    }

    public DeliveryPackage(URI packageUri, String dbName, int deliveryId, int fullIssueNumber) {
        this(packageUri, deliveryId, parsePackageName(packageUri));
        libName = dbName;
        vendor = parseVendor();
        this.fullIssueNumber = fullIssueNumber;
    }

    public DeliveryPackage(URI packageUri) throws DeliveryPackageException {
        super(packageUri);
    }

    public DeliveryPackage(String packageFileName) {
        super(packageFileName);
    }

    @Override
    protected void init() {
        super.init();
        rm = CochraneCMSBeans.getRecordManager();
    }

    @Override
    protected int createDeliveryFile(String dfName) throws Exception {
        int ret;
        if (isJats(dfName)) {
            ret = rs.createDeliveryFile(dfName, DeliveryFileEntity.TYPE_JATS, IDeliveryFileStatus.STATUS_BEGIN,
                    IDeliveryFileStatus.STATUS_PACKAGE_IDENTIFIED);
        } else if (isMl3g(dfName)) {
            ret = rs.createDeliveryFile(dfName, DeliveryFileEntity.TYPE_WML3G, IDeliveryFileStatus.STATUS_BEGIN,
                    IDeliveryFileStatus.STATUS_PACKAGE_IDENTIFIED);
        } else {
            ret = super.createDeliveryFile(dfName);
        }
        return ret;
    }

    public final Integer getYear() {
        return CmsUtils.getYearByIssueNumber(fullIssueNumber);
    }

    public final Integer getIssueNumber() {
        return CmsUtils.getIssueByIssueNumber(fullIssueNumber);
    }

    @Override
    public String getLibName() {
        return libName;
    }

    @Override
    public void parsePackageName() throws DeliveryPackageException {
        String[] parts = packageFileName.split("[-_]");
        libName = parts[0];
        checkDbName(libName);
        try {
            int year = Integer.parseInt(parts[1]);
            int number = Integer.parseInt(parts[2].replace(Extensions.ZIP, ""));
            fullIssueNumber = CmsUtils.getIssueNumber(year, number);
        } catch (Throwable e) {
            throw new DeliveryPackageException("DeliveryPackageName must be: libraryName_issueYear_issueNumber...",
                    IDeliveryFileStatus.STATUS_BAD_FILE_NAME);
        }
        vendor = parseVendor();
    }

    public boolean isAut() {
        return packageType != null && packageType.isEditorialFlat();
    }

    protected boolean forSPD() {
        return false;
    }

    protected String checkForSPD(BaseType bt, String cdNumber, String pubName, int pub, boolean spd,
                                 boolean spdCanceled, boolean spdReprocess) throws CmsException {
        return null;
    }

    private static void checkDbName(String libName) throws DeliveryPackageException {
        Res<BaseType> bt = BaseType.find(libName);
        if (!Res.valid(bt) || !bt.get().legal()) {
            DeliveryPackageException.throwPickUpError(String.format("library name %s is not supported", libName));
        }
    }

    public static boolean isArchie(String packageName) {
        return packageName.startsWith(PackageChecker.CDSR_PREFIX);
    }

    public static boolean isTranslatedAbstract(int type) {
        return DeliveryFileEntity.onlyTranslation(type);
    }

    public static boolean isArchieAut(String packageName) {
        if (packageName.contains(PackageChecker.TRANSLATIONS)) {
            return true;
        } else {
            return isArchie(packageName) && isAut(packageName);
        }
    }

    public static boolean isAutRepeat(String packageName) {
        return packageName.contains(PackageChecker.AUT_REPEAT_SUFFIX);
    }

    public static boolean isAutReprocess(String packageName) {
        return packageName.contains(PackageChecker.AUT_REPROCESS_SUFFIX);
    }

    public static boolean isRepeat(String packageName) {
        return packageName.contains(PackageChecker.REPEAT_PACKAGE_NAME_POSTFIX);
    }

    public static boolean isAut(String packageName) {
        if (packageName.contains(PackageChecker.TRANSLATIONS)) {
            return true;
        } else {
            return packageName.contains(PackageChecker.AUT_SUFFIX);
        }
    }

    public static boolean isMeshterm(String packageName) {
        return packageName.contains(PackageChecker.MESH_UPDATE_SUFFIX);
    }

    public static boolean isPropertyUpdate(String packageName) {
        return packageName.contains(PackageChecker.PROPERTY_UPDATE_SUFFIX);
    }

    public static boolean isPropertyUpdatePdf(String packageName) {
        return packageName.contains(PackageChecker.PROPERTY_UPDATE_PDF_SUFFIX);
    }

    public static boolean isPropertyUpdateMl3g(String packageName) {
        return packageName.contains(PackageChecker.PROPERTY_UPDATE_WML3G_SUFFIX);
    }

    public static boolean isMl3g(String packageName) {
        return packageName.contains(PackageChecker.WML3G_POSTFIX);
    }

    public static boolean isJats(String packageName) {
        if (packageName.contains(IssueDate.getTranslationPackagePrefix())) {
            return true;
        } else {
            return packageName.contains(PackageChecker.JATS_POSTFIX);
        }
    }

    public static boolean isAries(String path) {
        return path.contains(PackageChecker.ARCHIE_ARIES_POSTFIX) || path.contains(PackageChecker.ARIES_FOLDER);
    }

    public static boolean isPreQA(boolean ml3g, String packageName) {
        return ml3g && !isMl3g(packageName);
    }

    public static boolean isNoCDSRAut(BaseType bt, DeliveryFileEntity de, boolean wr) {
        return (wr && !bt.isCDSR()) || (bt.isEditorial() && isPreQA(
                DeliveryFileEntity.isWml3g(de.getType()), de.getName()));
    }

    public String getVendor() {
        return vendor;
    }

    protected String parseVendor() {
        try {
            String ui = packageUri.getUserInfo();
            return ui.substring(0, ui.indexOf(":"));
        } catch (Exception e) {
            return "";
        }
    }

    public DeliveryPackageInfo extractData(int issueId, int clDbId, int type, IRecordCache cache)
            throws DeliveryPackageException, IOException {
        DeliveryPackageInfo manifest = new DeliveryPackageInfo(issueId, libName, packageId, getPackageFileName());
        manifest.setDbId(clDbId);
        extractData(type == DeliveryFileEntity.TYPE_WML3G ? String.valueOf(packageId)
                : String.valueOf(System.currentTimeMillis()), manifest, cache);
        return manifest;
    }

    public DeliveryPackageInfo extractData(int issueId) throws DeliveryPackageException, IOException {
        DeliveryPackageInfo manifest = new DeliveryPackageInfo(issueId, libName, packageId, getPackageFileName());
        extractData(String.valueOf(System.currentTimeMillis()), manifest, null);
        return manifest;
    }

    public DeliveryPackageInfo extractData(int issueId, int dbId) throws DeliveryPackageException, IOException {
        DeliveryPackageInfo manifest = new DeliveryPackageInfo(issueId, libName, dbId, packageId, getPackageFileName());
        extractData(String.valueOf(packageId), manifest, null);
        return manifest;
    }

    public Map<String, IRecord> extractData(Integer issueId, Integer dbId, PackageType pt, IFlowLogger logger)
            throws Exception {
        packageType = pt;
        flLogger = logger;
        File tmp = File.createTempFile(Constants.TMP_STR + packageFileName, "");
        ZipInputStream zis = getZipStream(tmp);
        packagePath = FilePathBuilder.getPathToIssuePackage(issueId, getLibName(), packageId);
        recordCache = logger.getRecordCache();
        try {
            parseZip(issueId, zis, null, rps.getRealFilePath(packagePath));
        } catch (DeliveryPackageException de) {
            BaseType baseType = BaseType.find(getLibName()).get();
            MessageSender.sendFailedLoadPackageMessage(packageFileName, de.getMessage(), baseType.getShortName(), null);
            throw de;
        } finally {
            tmp.delete();
        }
        boolean onlyBeingProcessed = errors != null && handleFailedRecords(issueId, null);
        notifyOnReceived(dbId);
        LOG.info("%s [%d] contains: %d article(s)", packageFileName, packageId, getArticleCount());
        if (errors != null) {
            errors.forEach(this::completeFailedRecord);
        }
        checkEmpty(issueId, onlyBeingProcessed);
        return getResults();
    }

    protected void completeFailedRecord(ErrorInfo err) {
        Object errEntity = err.getErrorEntity();
        ArchieEntry ae = (errEntity instanceof ArchieEntry) ? (ArchieEntry) errEntity : null;
        if (ErrorInfo.Type.RECORD_BLOCKED != err.getErrorType() && ae != null) {
            flLogger.onFlowCompleted(ae.getName(), ae.getLanguage(),
                    ErrorInfo.Type.VALIDATION_SPD != err.getErrorType() && forSPD());
        }
    }

    protected ArchieResponseBuilder getResponseBuilder() {
        return null;
    }

    protected String getArticleBasePath(String pubName, String cdNumber) {
        return packagePath + cdNumber + FilePathCreator.SEPARATOR;
    }

    protected boolean parseZip(Integer issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo,
                               String realDirToZipStore) throws DeliveryPackageException {
        ZipEntry ze;
        ZipEntry zeLast = null;
        try {
            while ((ze = zis.getNextEntry()) != null) {
                if (!ze.isDirectory()) {
                    zeLast = ze;
                    parseZipEntry(issueId, zis, null, ze);
                }
            }
            checkFirstZipEntry(packageFileName, zeLast);
            putPackageToRepository(realDirToZipStore);
        } catch (Exception e)  {
            DeliveryPackageException.throwCannotParsePackageError(e.getMessage());
        } finally {
            IOUtils.closeQuietly(zis);
        }
        return true;
    }

    protected void checkEmpty(Integer issueId, boolean onlyBeingProcessed) throws Exception {
        if (isEmpty()) {
            if (onlyBeingProcessed) {
                LOG.debug("an empty package %s is being removed ...", packageFileName);
                rps.deleteDir(FilePathBuilder.getPathToIssuePackage(issueId, libName, packageId));
                rs.deleteDeliveryFileEntity(packageId);
            }
            String errorMessage = errors.stream().map(ErrorInfo::getErrorDetail).collect(Collectors.joining(", "));
            String msg = StringUtils.isNotBlank(errorMessage) ? errorMessage : PACKAGE_HANDLED_ERROR;
            DeliveryPackageException.throwNoWorkableRecordsError(msg);
        }
    }

    protected boolean isEmpty() {
        return recordIds.isEmpty();
    }

    private void extractData(String packageFolder, DeliveryPackageInfo manifest, IRecordCache cache)
            throws DeliveryPackageException, IOException {
        LOG.debug("extract data start");
        int issueId = manifest.getIssueId();
        int dfId = manifest.getDfId();
        String dbName = manifest.getDbName();
        File tmp = File.createTempFile(Constants.TMP_STR + manifest.getDfName(), "");
        pckTimestamp = packageFolder;
        recordCache = cache;
        ZipInputStream zis = getZipStream(tmp);
        rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_PICKED_UP, true);
        if (dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))
                || dbName.equals(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCMR))) {
            parseCentralLib(issueId, zis, manifest);
        } else if (dbName.equals(CochraneCMSPropertyNames.getCcaDbName())) {
            parseCcaLib(issueId, zis, manifest);
        } else {
            parseCommonLib(packageFolder, issueId, zis, manifest, cache, rps);
        }
        try {
            zis.close();
        } catch (IOException e) {
            throw new DeliveryPackageException(e.getMessage(), IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
        tmp.delete();
        rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_UNZIPPED, true);
        LOG.debug("extract data finished");
    }

    private void parseCcaLib(int issueId, ZipInputStream zis, DeliveryPackageInfo dpi) throws DeliveryPackageException {
        ZipEntry ze;
        try {
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    continue;
                }
                String zipEntryName = PackageChecker.replaceBackslash2Forward(ze.getName());
                String recordName = StringUtils.substringBefore(zipEntryName, "/");
                String tail = zipEntryName.replaceFirst(recordName, "").replaceFirst("/", "");
                String path;
                if (tail.equals(recordName + Extensions.XML)) {
                    path = FilePathCreator.getFilePathToSource(pckTimestamp, String.valueOf(issueId), libName,
                            recordName);
                    dpi.addRecordPath(recordName, path);
                } else {
                    path = FilePathCreator
                            .getFilePathForEnclosure(pckTimestamp, String.valueOf(issueId), libName, recordName, tail);
                }
                putFileToRepository(rps, zis, path);
                dpi.addFile(recordName, path);
            }
        } catch (Exception e) {
            DeliveryPackageException.throwCannotParsePackageError(e.getMessage());
        }
    }

    private static DeliveryPackageInfo parseCommonLib(String packageFolder, int issueId, ZipInputStream zis,
        DeliveryPackageInfo packageInfo, IRecordCache cache, IRepository rp) throws DeliveryPackageException {

        boolean puUpdate = isPropertyUpdate(packageInfo.getDfName());
        boolean skipNotification = !puUpdate;
        String dbName = packageInfo.getDbName();
        ZipEntry ze;
        try {
            boolean isFirst = true;
            Set<String> currentCached = new HashSet<>();
            while ((ze = zis.getNextEntry()) != null) {
                if (!ze.isDirectory()) {
                    if (skipNotification && ze.getName().contains(RevmanPackage.RESPONSE_FILE_NAME)) {
                        skipNotification = false;
                        continue;
                    }
                    String zipEntryName = PackageChecker.replaceBackslash2Forward(ze.getName());
                    int pozLibName = getPozLibName(zipEntryName, dbName);
                    String tail = zipEntryName.substring(pozLibName + dbName.length());
                    if (isFirst) {
                        checkPckStructure(tail, zipEntryName);
                        isFirst = false;
                    }
                    String recordName = tail.substring(1, tail.indexOf("/", 1));
                    if (!puUpdate && isUploadingWithOtherPackage(recordName, currentCached, cache)) {
                        LOG.warn(String.format("%s %s", recordName, MessageSender.MSG_RECORD_PROCESSED));
                        continue;
                    }
                    String tail2 = tail.replaceFirst(recordName, "").replaceFirst("/", "").replaceFirst("/", "");
                    String path;
                    if (tail2.equals(recordName + Extensions.XML)) {
                        path = FilePathCreator.getFilePathToSource(packageFolder, String.valueOf(issueId), dbName,
                                recordName);
                        packageInfo.addRecordPath(recordName, path);
                    } else {
                        path = FilePathCreator.getFilePathForEnclosure(packageFolder, String.valueOf(issueId), dbName,
                                recordName, tail2);
                    }
                    putFileToRepository(rp, zis, path);
                    packageInfo.addFile(recordName, path);
                }
            }
        } catch (Exception e) {
            DeliveryPackageException.throwCannotParsePackageError(e.getMessage());
        }
        return packageInfo;
    }

    protected boolean parseZipEntry(Integer issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo, ZipEntry ze)
            throws Exception {
        String zeName = ze.getName();
        PackageType.Entry entry = PackageChecker.getPackageEntry(zeName, packageType);
        PackageType.EntryType type = entry.getType();
        if (PackageType.EntryType.UNDEF == type) {
            return false;
        }
        if (!isEntryTypeSupported(type, false)) {
            throw type.throwUnsupportedEntry(zeName);
        }
        type.parseZip(entry, issueId, null, zeName, zis, this);
        return true;
    }

    protected final boolean isEntryTypeSupported(PackageType.EntryType entryType, boolean embedded) {
        return embedded ? getEmbeddedEntryTypesSupported().contains(entryType)
                : getEntryTypesSupported().contains(entryType);
    }

    @Override
    public Set<PackageType.EntryType> getEntryTypesSupported() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Set<PackageType.EntryType> getEmbeddedEntryTypesSupported() {
        return Collections.emptySet();
    }

    @Override
    public void parseImage(PackageType.Entry entry, @Null String cdNumber, String zeName, InputStream zis)
            throws Exception {
        parseAsset(entry, zeName, null, zis);
    }

    protected String parseAsset(PackageType.Entry entry, String zeName, String destFolderPostfix, InputStream zis)
            throws Exception {
        String[] pathParts = PackageChecker.replaceBackslash2Forward(zeName).split(FilePathCreator.SEPARATOR);
        String fileName = pathParts[pathParts.length - 1];
        try {
            String articleName = parseArticleName(zeName);
            String destFolder = entry.getDestinationFolder();
            if (destFolder == null) {
                destFolder = FilePathCreator.SEPARATOR;
                if (destFolderPostfix != null) {
                    destFolder += articleName + destFolderPostfix + FilePathCreator.SEPARATOR;
                }
            } else if (!destFolder.isEmpty()) {
                destFolder = FilePathCreator.SEPARATOR + destFolder + FilePathCreator.SEPARATOR;
            }
            String path = packagePath + articleName + destFolder + fileName;
            putFileToRepository(rps, zis, path);
            return articleName;
        } catch (Exception e) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(fileName, e.getMessage()), errors());
            throw e;
        }
    }

    protected String parseArticleName(String zeName) throws CmsException {
        String articleName = packageType.parseArticleName(zeName);
        if (articleName == null) {
            throw new CmsException("cannot extract article name from: " + zeName);
        }
        return articleName;
    }

    @Override
    public String parseManifest(PackageType.Entry entry, String path, InputStream zis) {
        return path;
    }

    @Override   // no actions is required according to CPP5-341 resolution (Won't Have)
    public final void parseUSC(PackageType.Entry entry, String zeName, InputStream zis) {
    }

    @Override
    public void parseArticleZip(PackageType.Entry entry, Integer issueId, @NotNull String zeName, ZipInputStream zis,
                                boolean ta) throws Exception {
        String cdNumber = parseArticleName(zeName);
        String path = packagePath + cdNumber + Extensions.XML;
        try {
            putFileToRepository(rps, zis, path);
            addReview(getArticleBasePath(cdNumber, cdNumber), cdNumber, Constants.FIRST_PUB, false, path, 0, null);
        } catch (Throwable tr) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(cdNumber, tr.getMessage()), errors());
        }
    }

    protected final void parseAriesEntry(GoManifest goManifest, ZipEntry ze, InputStream is,
                                         PackageArticleResults<CDSRMetaVO> results) throws Exception {
        String zeName = ze.getName();
        goManifest.removeAssetFileName(zeName);
        if (ze.isDirectory()) {
            LOG.warn("%s is a directory, directories are not allowed for Aries package structure", zeName);
        } else if (zeName.equals(goManifest.getMetadataFileName())) {
            parseMetadata(null, zeName, is);
        } else {
            PackageType.Entry zipEntry = PackageChecker.getPackageEntry(zeName, packageType);
            PackageType.EntryType type = zipEntry.getType();
            if (type == PackageType.EntryType.UNDEF || type == PackageType.EntryType.METADATA) {
                LOG.warn("%s - an unknown entry type for %s", packageFileName, zeName);
                putFileToRepository(rps, is, packagePath + zeName);
                return;
            }
            if (!isEntryTypeSupported(type, true)) {
                throw type.throwUnsupportedEntry(zeName);
            }
            type.parseFinal(zipEntry, zeName, is, this, results);
        }
    }

    protected CDSRMetaVO addReview(String basePath, String cdNumber, int pub, boolean statsData, String recordPath,
                                   int hp, CDSRMetaVO metaVO) {
        BaseType bt = BaseType.find(getLibName()).get();
        boolean aut = isAut();
        boolean dash = aut && bt.hasSFLogging();
        boolean reprocess = isAutReprocess(getPackageFileName());
        String spdDate = forSPD() ? (metaVO == null ? "" : metaVO.getPublishedOnlineFinalForm()) : null;
        try {
            synchronized (IRecordCache.RECORD_LOCKER) {
                boolean puUpdate = isPropertyUpdate(getPackageFileName());
                if (!puUpdate) {
                    checkInCache(recordIds.get(cdNumber), cdNumber, basePath, false, rps, recordCache);
                }
                String pubName = RecordHelper.buildPubName(cdNumber, pub);
                boolean spd = metaVO != null && metaVO.isScheduled() || forSPD();
                String errSpd = checkForSPD(bt, cdNumber, pubName, pub, spd, false, spd && reprocess);
                CDSRMetaVO meta = metaVO;
                if (meta == null) {
                    if (!reprocess) {
                        flLogger.onProductReceived(packageFileName, packageId, cdNumber, pub, vendor, spdDate, dash);
                    }
                    meta = helper().extractMetadata(cdNumber, pub, recordPath, fullIssueNumber, bt, rps, recordCache);
                    meta.setWasReprocessed(reprocess);
                    spdDate = forSPD() ? meta.getPublishedOnlineFinalForm() : null;
                } else {
                    meta.setWasReprocessed(reprocess);
                    flLogger.onProductReceived(packageFileName, packageId, meta, vendor, spdDate, dash);
                }
                if (errSpd != null) {
                    throw CmsException.createForValidation(meta, errSpd, true);
                }
                checkCCA(bt, meta, recordPath, new Date());
                meta.setHistoryNumber(RecordEntity.VERSION_SHADOW);
                if (!reprocess) {
                    flLogger.updateProduct(meta, meta.getStage(), meta.getStatus(), meta.getManuscriptNumber(),
                            meta.getWMLPublicationType(), spdDate, hp);
                }
                recordIds.put(cdNumber, validateAndCreate(bt, basePath, cdNumber, pub, statsData, hp, meta));
                onReprocessed(bt, meta, hp, spdDate, dash);
                if (!puUpdate) {
                    flLogger.onProductValidated(meta, dash && bt.isCDSR(), forSPD());
                }
                recordCache.addRecord(cdNumber, meta.isScheduled());
                articles().add(meta);
                return meta;
            }
        } catch (CmsException | CmsJTException ce) {
            handleError(ce.get(), cdNumber, pub);
        } catch (Throwable tr) {
            handleError(tr, cdNumber, pub);
        }
        return null;
    }

    private void checkCCA(BaseType bt, CDSRMetaVO meta, String recordPath, Date date) throws CmsException {
        if (bt.isCCA() && (CCAWorker.extractCDSRDoi(getPackageFileName(), getPackageId(), meta, recordPath, date,
                helper(), rs) == null || !CCAWorker.checkRecordUnitStatus(getPackageFileName(), getPackageId(), meta,
                date, null, helper(), null))) {
            String report = helper().getMessageTagReport();
            throw new CmsException(new ErrorInfo<>(meta, report == null ? "report is not " : report));
        }
    }

    protected int validateAndCreate(BaseType bt, String basePath, String cdNumber, int pub, boolean statsData,
                                    int highPriority, CDSRMetaVO meta) throws Exception {
        return rm.createRecord(bt, meta, GroupVO.getGroup(cdNumber, pub, meta.getCochraneVersion(),
                meta.getGroupSid(), recordCache), packageId, statsData, isAut(), highPriority);
    }

    private void onReprocessed(BaseType bt, CDSRMetaVO meta, int hp, String spdDate, boolean dashboard) {
        if (meta.wasReprocessed()) {
            flLogger.onProductReceived(packageFileName, packageId, meta, vendor, spdDate, dashboard);
            flLogger.updateProduct(meta, meta.getStage(), meta.getStatus(), meta.getManuscriptNumber(),
                    meta.getWMLPublicationType(), spdDate, hp);
        }
    }

    protected int notifyOnReceived(Integer dbId) {
        return SuspendNotificationEntity.TYPE_NO_ERROR;
    }

    protected void handleError(CmsException ce, String cdNumber, int pub) {
        ErrorInfo ei = ce.hasErrorInfo() ? ce.getErrorInfo() : new ErrorInfo<>(new ArchieEntry(cdNumber, pub),
                ce.getMessage());
        RecordHelper.handleErrorMessage(ei, errors());
        logError(RecordHelper.buildPubName(cdNumber, pub), ce);
    }

    protected void handleError(Throwable tr, String cdNumber, int pub) {
        RecordHelper.handleErrorMessage(new ErrorInfo<>(new ArchieEntry(cdNumber, pub), tr.getMessage()), errors());
        logError(RecordHelper.buildPubName(cdNumber, pub), tr);
    }

    protected boolean handleError(ICDSRMeta meta, Throwable tr) {
        boolean handled = false;
        if (tr instanceof ICmsException) {
            ICmsException ce = (ICmsException) tr;
            ErrorInfo errorInfo = ce.getErrorInfo();
            if (errorInfo != null && errorInfo.getErrorEntity() instanceof ArchieEntry) {
                ArchieEntry ae = (ArchieEntry) errorInfo.getErrorEntity();
                RecordHelper.handleErrorMessage(errorInfo, errors());
                logError(ae.getPubName(), ce.get());
                handled = true;

            } else if (meta != null) {
                handleError(ce.get(), meta.getCdNumber(), meta.getPubNumber());
                handled = true;
            }
        } else if (meta != null) {
            handleError(tr, meta.getCdNumber(), meta.getPubNumber());
            handled = true;
        }
        return handled;
    }

    protected void throwCommonError(String flowMsg, String msg, int status, int event) throws DeliveryPackageException {
        flLogger.onPackageError(event, getPackageId(), packageFileName, getArticleNames(), flowMsg, isAut(), forSPD());
        throw new DeliveryPackageException(msg, status);
    }

    protected void throwCommonError(Throwable tr, int event) throws DeliveryPackageException {
        throwCommonError(String.format("Package '%s' parsing: %s", packageFileName, tr.getMessage()),
            String.format("Couldn't parse package '%s': %s", packageFileName, tr.getMessage()),
                IDeliveryFileStatus.STATUS_CORRUPT_ZIP, event);
    }

    protected void logError(String pubName, Throwable tr) {
        flLogger.getActivityLog().logRecordError(ILogEvent.QAS_FAILED, DbEntity.NOT_EXIST_ID, pubName, BaseType.find(
                libName).get().getDbId(), CmsUtils.getIssueNumber(getYear(), getIssueNumber()), tr.getMessage());
    }

    protected Collection<String> getArticleNames() {
        return recordIds.keySet();
    }

    protected static String checkSPD(BaseType bt, String cdNumber, String pubName, int pub, boolean spd,
                                     boolean reprocess, IRecordCache cache) {
        IKibanaRecord kr = cache.getKibanaRecord(cdNumber, true);
        if (kr != null) {
            String publisherId = kr.getPubName();
            int spdPubNUmber = bt.getProductType().parsePubNumber(publisherId);
            if (spd && (pub != spdPubNUmber || !reprocess) || (!spd && pub >= spdPubNUmber)) {
                return pub == spdPubNUmber ? PreviousVersionException.msgAlreadySubmittedSpd(publisherId)
                    : PreviousVersionException.msgAlreadySubmittedSpd(publisherId, spd ? Constants.SPD : "", pubName);
            }
        }
        return null;
    }

    protected static String checkSPDCancelled(String cdNumber, String pubName, IRecordCache cache) throws CmsException {
        IKibanaRecord kr = cache.getKibanaRecord(cdNumber, true);
        if (kr == null || !kr.getPubName().equals(pubName)) {
            if (cache.getKibanaRecord(cdNumber, false) != null) {
                throw CmsException.createForBeingProcessed(cdNumber);
            }
            return CochraneCMSPropertyNames.getSPDNotFoundMsg();
        }
        IKibanaRecord activeKr = cache.getKibanaRecord(cdNumber);
        if (activeKr != null && activeKr.getPubName().equals(pubName)) {
            throw CmsException.createForBeingProcessed(pubName);
        }
        return null;
    }

    private static boolean isUploadingWithOtherPackage(String recordName, Set<String> curCached, IRecordCache cache) {
        if (cache != null && !curCached.contains(recordName)) {
            synchronized (IRecordCache.RECORD_LOCKER) {
                if (cache.containsRecord(recordName)) {
                    return true; // now this record is being uploaded with other package
                }
                cache.addRecord(recordName, false);
                curCached.add(recordName);
            }
        }
        return false;
    }

    private static void checkPckStructure(String tail, String name) throws DeliveryPackageException {
        String[] parts = tail.split("/");
        if (parts.length > NUMBER_DIRS
            || parts.length < THREE
            || (parts.length == NUMBER_DIRS
            && !CochraneCMSPropertyNames.getImageDirectories().contains(parts[2]))) {
            throw new DeliveryPackageException("Wrong package structure " + name,
                IDeliveryFileStatus.STATUS_INVALID_CONTENT);
        }
    }

    private static int getPozLibName(String zipEntryName, String dbName) throws DeliveryPackageException {
        int pozLibName = zipEntryName.lastIndexOf(dbName);
        if (pozLibName == -1) {
            throw new DeliveryPackageException("Wrong package structure, can't find libname in " + zipEntryName,
                IDeliveryFileStatus.STATUS_INVALID_CONTENT);
        }
        return pozLibName;
    }

    private void parseCentralLib(int issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo)
        throws DeliveryPackageException {
        ZipEntry ze;
        Set<String> rejectedRecords = new HashSet<>();
        try {
            while ((ze = zis.getNextEntry()) != null) {
                if (!ze.isDirectory()) {
                    sliceXml(zis, issueId, libName, rps, packageInfo, rejectedRecords);
                }
            }
            rejectCentralRecords(rejectedRecords, packageInfo);
        } catch (IOException e) {
            throw new DeliveryPackageException(e.getMessage(), IDeliveryFileStatus.STATUS_CORRUPT_ZIP);
        }
    }

    private String readXmlLine(StringBuffer buf, String line, String filePath, int issueId, String db,
        DeliveryPackageInfo packageInfo, Set<String> rejectedRecords) throws DeliveryPackageException {
        if (buf == null) {
            invalidContentError();
        }
        String ret = filePath;
        if (line == null) {
            buf.append(XmlUtils.XML_HEAD);
        } else {
            if (line.startsWith("<REFERENCE ID=")) {
                ret = createRecordInfo(buf, line, issueId, db, packageInfo, rejectedRecords);
            }
            buf.append(line);
        }
        buf.append("\n\r");
        return ret;
    }

    private void sliceXml(ZipInputStream inputStream, int issueId, String db, IRepository repository,
        DeliveryPackageInfo packageInfo, Set<String> rejectedRecords) throws IOException, DeliveryPackageException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = reader.readLine();
        StringBuffer buf = null;
        String filePath = null;
        while (line != null) {
            line = line.trim();
            if (line.startsWith(XmlUtils.XML_HEAD)) {
                buf = saveNewFile(buf, repository, filePath, true);
                readXmlLine(buf, null, filePath, issueId, db, packageInfo, rejectedRecords);
                if (line.length() > XmlUtils.XML_HEAD.length()) {
                    line = line.substring(XmlUtils.XML_HEAD.length());
                    filePath = readXmlLine(buf, line, filePath, issueId, db, packageInfo, rejectedRecords);
                }
            } else {
                filePath = readXmlLine(buf, line, filePath, issueId, db, packageInfo, rejectedRecords);
            }
            line = reader.readLine();
        }
        saveNewFile(buf, repository, filePath, false);
    }

    private String createRecordInfo(StringBuffer buf, String line, int issueId, String db,
        DeliveryPackageInfo packageInfo, Set<String> rejectedRecords) throws DeliveryPackageException {
        int pos = line.indexOf("ID=\"") + THREE;
        int pos2 = line.indexOf("\"", pos + 1);
        if (pos == -1 || pos2 == -1) {
            throw new DeliveryPackageException("Bad line " + line, IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
        String recName = line.substring(pos + 1, pos2);
        String filePath;
        if (packageInfo.getRecords().containsKey(recName)) {
            filePath = null;
            rejectedRecords.add(recName);
        } else {
            filePath = FilePathCreator.getFilePathToSource(pckTimestamp, String.valueOf(issueId), db, recName);
            packageInfo.addFile(recName, filePath);
            packageInfo.addRecordPath(recName, filePath);
        }
        return filePath;
    }

    private static void invalidContentError() throws DeliveryPackageException {
        throw new DeliveryPackageException("Line <?xml version=\"1.0\" encoding=\"UTF-8\">  must be first in file",
            IDeliveryFileStatus.STATUS_INVALID_CONTENT);
    }

    private void rejectCentralRecords(Set<String> rejectedRecords, DeliveryPackageInfo packageInfo) {
        if (!rejectedRecords.isEmpty()) {
            Set<String> records = packageInfo.getRecords().keySet();
            Set<String> recordPaths = packageInfo.getRecordPaths().keySet();
            StringBuilder strb = new StringBuilder("Rejected records:");
            for (String record : rejectedRecords) {
                records.remove(record);
                recordPaths.remove(record);
                strb.append("\n").append(record);
            }
            String messageDf = packageFileName + " - package contains several records with same ID";
            String report = strb.toString();
            Map<String, String> notifyMessage = new HashMap<>();
            notifyMessage.put(MessageSender.MSG_PARAM_DELIVERY_FILE, messageDf);
            notifyMessage.put(MessageSender.MSG_PARAM_REQUEST, report);
            MessageSender.sendMessage(MessageSender.MSG_TITLE_CENTRAL_WARNINGS, notifyMessage);
            LOG.warn(messageDf + "\n" + report);
        }
    }

    private StringBuffer saveNewFile(StringBuffer buf, IRepository repository, String filePath, boolean startNext)
            throws DeliveryPackageException {
        if (buf != null && filePath != null) {
            try {
                repository.putFile(filePath, new ByteArrayInputStream(buf.toString().getBytes()), false);
            } catch (IOException e) {
                throw new DeliveryPackageException(e.getMessage(), IDeliveryFileStatus.STATUS_PICKUP_FAILED);
            }
        }
        return startNext ? new StringBuffer(BUFFER_SIZE) : null;
    }

    protected final Map<String, IRecord> getResults() {
        return articles == null ? Collections.emptyMap() : handleResults();
    }

    protected Map<String, IRecord> handleResults() {
        Map<String, IRecord> ret = new HashMap<>();
        boolean dashboard = BaseType.find(getLibName()).get().hasSFLogging();
        boolean toLog = isAut() && !isPropertyUpdate(getPackageFileName());
        for (IRecord record: articles) {
            String cdNumber = record.getName();
            record.setId(recordIds.get(cdNumber));
            ret.put(cdNumber, record);
            if (toLog) {
                flLogger.onProductUnpacked(getPackageFileName(), null, cdNumber, null, dashboard, forSPD());
            }
        }
        return ret;
    }

    public int getArticleCount() {
        return articles == null ? 0 : articles.size();
    }

    protected List<IRecord> articles() {
        if (articles == null) {
            articles = new ArrayList<>();
        }
        return articles;
    }

    protected void handleFailedRecord(BaseType baseType, Integer issueId, ErrorInfo err,
                                      StringBuilder errBuffer, String manuscriptNumber) {
        Object errEntity = err.getErrorEntity();
        String msg = err.getErrorDetail();
        ArchieEntry ae = (errEntity instanceof ArchieEntry) ? (ArchieEntry) errEntity : null;
        String recName = ae != null ? ae.toString() : errEntity.toString();
        String title = ae != null ? ae.getTitle() : "";
        if (ErrorInfo.Type.RECORD_BLOCKED == err.getErrorType() || ae == null) {
            MessageSender.addMessage(errBuffer, recName, msg);
            return;
        }
        if (isAut() && getResponseBuilder() != null) {
            getResponseBuilder().addContent(ae.asErrorElement(getResponseBuilder(), err, flLogger));
        }
        if (baseType.isCCA()) {
            String ccaReport = helper().initMessageTags() ? CCAWorker.reportFailedValidation(getPackageFileName(),
                    getPackageId(), recName, msg, err, helper(), null) : helper().getMessageTagReport();
            if (ccaReport != null) {
                msg = ccaReport;
            }
        }
        if (err.isCommonFlowRecordValidationError()) {
            boolean x = ErrorInfo.Type.VALIDATION_SPD != err.getErrorType();
            flLogger.onProductUnpacked(getPackageFileName(), getPackageId(), ae.getName(), ae.getLanguage(),
                isAut() && baseType.hasSFLogging(), ErrorInfo.Type.VALIDATION_SPD != err.getErrorType() && forSPD());
            logOnError(baseType, ae, ILogEvent.PRODUCT_VALIDATED, msg, err);
        } else {
            logOnError(baseType, ae, ILogEvent.PRODUCT_UNPACKED, msg, err);
        }
        MessageSender.sendRecordFailedValidationReport(baseType, getPackageFileName(),
                recName, title, msg, manuscriptNumber);
    }

    private void logOnError(BaseType bt, ArchieEntry ae, int event, String msg, ErrorInfo err) {
        flLogger.onProductError(event, getPackageId(), ae.getName(), ae.getLanguage(), "Record extraction: " + msg,
                true, isAut() && bt.hasSFLogging(), ErrorInfo.Type.VALIDATION_SPD != err.getErrorType() && forSPD());
    }

    protected final boolean handleFailedRecords(Integer issueId, String manuscriptNumber) {
        boolean onlyBeingProcessed = true;
        StringBuilder errBuffer = new StringBuilder();
        BaseType baseType = BaseType.find(getLibName()).get();
        for (ErrorInfo ri: errors) {
            handleFailedRecord(baseType, issueId, ri, errBuffer, manuscriptNumber);
            if (ErrorInfo.Type.RECORD_BLOCKED != ri.getErrorType())  {
                onlyBeingProcessed = false;
            }
        }
        if (errBuffer.length() > 0) {
            MessageSender.addMessage(errBuffer, null, null);
            MessageSender.sendFailedLoadPackageMessage(packageFileName, errBuffer.toString(),
                    baseType.getShortName(), manuscriptNumber);
        }
        return onlyBeingProcessed;
    }

    protected boolean addCanceledReview(BaseType bt, String cdNumber, int pub, CDSRMetaVO meta, boolean dashboard) {
        try {
            synchronized (IRecordCache.RECORD_LOCKER) {
                String errSpd = checkForSPD(bt, cdNumber, meta.getPubName(), pub, false, true, false);
                if (errSpd != null) {
                    throw CmsException.createForValidation(meta, errSpd, true);
                }
                flLogger.onProductCanceled(packageFileName, packageId, meta, vendor, dashboard);
                recordIds.put(cdNumber, rm.updateSPDRecordToCancel(meta, bt.getDbId(), packageId));
                flLogger.onProductValidated(meta, dashboard, true);
                articles().add(meta);
            }
        } catch (CmsException ce) {
            handleError(ce, cdNumber, pub);
        } catch (Throwable tr) {
            handleError(tr, cdNumber, pub);
        }
        return false;
    }

    protected final List<ErrorInfo> errors() {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        return errors;
    }

    protected ContentHelper helper() {
        if (contentHelper == null) {
            contentHelper = new ContentHelper();
        }
        return contentHelper;
    }
}