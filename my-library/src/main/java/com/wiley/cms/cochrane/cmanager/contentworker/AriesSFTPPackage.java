package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.validation.constraints.Null;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.notification.SuspendNotificationEntity;
import com.wiley.tes.util.Extensions;
import org.apache.commons.lang.StringUtils;
import org.jdom.Document;

import static com.wiley.cms.cochrane.cmanager.contentworker.AriesImportFile.DELIVER_TO_WILEY;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 1/3/2021
 */
public class AriesSFTPPackage extends JatsPackage {

    public static final String PACKAGE_HANDLED_ERROR = "a package handled with errors";

    private static final Set<PackageType.EntryType> SUPPORTED_EMBEDDED_TYPES = new HashSet<>(Arrays.asList(
             PackageType.EntryType.ARTICLE, PackageType.EntryType.IMAGE, PackageType.EntryType.IMPORT,
                 PackageType.EntryType.STATS, PackageType.EntryType.METADATA));

    private final AriesHelper ariesHelper =  new AriesHelper(helper().getDocumentLoader());

    public AriesSFTPPackage(URI packageUri, String dbName, int deliveryId, int fullIssueNumber) {
        super(packageUri, dbName, deliveryId, fullIssueNumber);
    }

    @Override
    protected String parseVendor() {
        return PackageChecker.ARIES;
    }

    @Override
    protected void setAut(int fullIssueNumber) {
        reb = new ArchieResponseBuilder(false, false, packageFileName.replace(FilePathCreator.ZIP_EXT, "0"), packageId);
        reb.sPD(CmsUtils.isScheduledIssueNumber(fullIssueNumber), false);
    }

    @Override
    protected void checkEmpty(Integer issueId, boolean onlyBeingProcessed) throws Exception {
        boolean bp = onlyBeingProcessed && ariesHelper.isDeliver();
        if (bp) {
            AriesConnectionManager.copyPackageToBeingProcessedFolder(packageFileName, packagePath, rps);

        } else if (ariesHelper.isPublish() || ariesHelper.isCancel()) {
            throw errors == null ? new DeliveryPackageException(IDeliveryFileStatus.STATUS_PACKAGE_LOADED)
                : new DeliveryPackageException(PACKAGE_HANDLED_ERROR,
                    IDeliveryFileStatus.STATUS_INVALID_CONTENT);

        } else if (isEmpty() && errors != null && reb.size() > 0) {
            String errorMessage = errors.stream().map(ErrorInfo::getErrorDetail).collect(Collectors.joining(", "));
            String msg = StringUtils.isNotBlank(errorMessage) ? errorMessage : PACKAGE_HANDLED_ERROR;
            throw new DeliveryPackageException(msg, IDeliveryFileStatus.STATUS_PACKAGE_DELETED);
        }
        super.checkEmpty(issueId, bp);
    }

    @Override
    protected boolean parseZip(Integer issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo,
                               String realDirToZipStore) throws DeliveryPackageException {
        parseArticleZip(null, issueId, null, zis, false);
        return true;
    }

    public String getAriesImportFile() {
        return Optional.ofNullable(ariesHelper.getImportFile())
                .map(AriesImportFile::getManuscriptNumber)
                .orElse("N/A");
    }

    @Override
    public Set<PackageType.EntryType> getEntryTypesSupported() {
        return Collections.emptySet();
    }

    @Override
    public Set<PackageType.EntryType> getEmbeddedEntryTypesSupported() {
        return SUPPORTED_EMBEDDED_TYPES;
    }

    @Override
    public void parseStatsFile(PackageType.Entry entry, String cdNumber, String zeName, InputStream zis)
            throws Exception {
        parseStatsFile(entry, zeName, zis);
    }

    @Override
    public void parseArticleZip(@Null PackageType.Entry entry, Integer issueId, @Null String fileName,
                                ZipInputStream zis, boolean ta) throws DeliveryPackageException {
        ZipEntry ze;
        ZipEntry zeLast = null;
        CDSRMetaVO meta = null;
        PackageArticleResults<CDSRMetaVO> results = new PackageArticleResults<>();
        try {
            GoManifest goManifest = ariesHelper.parseGoManifest(packageFileName, packageUri, packagePath,
                    this::errors, rps);
            Set<String> entryNames = new HashSet<>();
            Set<ZipEntry> zipEntries = new HashSet<>();
            Document document = null;
            String articleName = null;

            while ((ze = zis.getNextEntry()) != null) {
                zeLast = ze;

                if (!helper().isArticleEntry(zeLast, packageType)) {
                    parseAriesEntry(goManifest, ze, zis, results);
                } else {
                    articleName = zeLast.getName();
                    ByteArrayInputStream byteArrayInputStream = null;
                    try {
                        byteArrayInputStream = helper().uploadACopy(zis);
                        parseAriesEntry(goManifest, ze, byteArrayInputStream, results);
                        byteArrayInputStream.reset();
                        document = helper().getArticleDocument(byteArrayInputStream);
                    } finally {
                        IOUtils.closeQuietly(byteArrayInputStream);
                    }
                }
                entryNames.add(zeLast.getName());
                zipEntries.add(zeLast);
            }

            meta = results.getArticleResult();
            meta.setArchiveFileName(goManifest.getArchiveFileName());
            meta.setArticleName(articleName);

            if (CochraneCMSPropertyNames.isPackageValidationActive()
                    && DELIVER_TO_WILEY.equalsIgnoreCase(ariesHelper.importFile.getProductionTaskDescription())) {
                helper().validateXmlForImagesAndGraphics(entryNames, document, packageType, meta);
                helper().validateXmlForSupplementaryMaterials(entryNames, document, packageType, meta);
                helper().validateZipEntryForEmptyFiles(zipEntries, meta);
            }

            boolean statsData = results.getStatsResult();

            checkFirstZipEntry(packageFileName, zeLast);

            BaseType bt = BaseType.find(getLibName()).get();
            boolean dashboard = isAut() && bt.hasSFLogging();

            ariesHelper.checkOnReceive(packageFileName, packageId, goManifest, meta, forSPD(), flLogger);

            if (ariesHelper.isDeliver()) {
                if (meta == null) {
                    throw CmsException.createForMissingMetadata(packageFileName);
                }
                if (ariesHelper.importFile.isScheduled()) {
                    meta.setVersionPubDates(meta.getPublishedIssue(), meta.getPublishedDate(), PublishHelper.checkSPD(
                            bt, ariesHelper.importFile.getTargetOnlinePubDateSrc(), LocalDate.now(), false));
                }
                meta.setManuscriptNumber(ariesHelper.importFile.getManuscriptNumber());
                addReview(results.getBasePath(), meta.getCdNumber(), meta.getPubNumber(), statsData,
                        meta.getRecordPath(), ariesHelper.getHighPriority(), meta);

            } else if (ariesHelper.isCancel()) {
                if (meta == null) {
                    throw CmsException.createForMissingMetadata(packageFileName);
                }
                if (addCanceledReview(bt, meta.getCdNumber(), meta.getPubNumber(), meta, dashboard)) {
                    reb.setPackageId(null);  // not to update already received WR
                    reviewCount++;
                }
            }
            putPackageToRepository(rps.getRealFilePath(packagePath));

        } catch (Throwable tr) {
            if (!handleError(results.getArticleResult(), tr)) {
                throwCommonError(tr, ILogEvent.PRODUCT_UNPACKED);
            }
        } finally {
            IOUtils.closeQuietly(zis);
        }
    }

    @Override
    public Object parseEmbeddedArticle(PackageType.Entry entry, String zeName, InputStream zis,
                                       PackageArticleResults results) throws Exception {
        ByteArrayInputStream bais = null;
        CDSRMetaVO meta;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            IOUtils.copy(zis, baos);
            bais = new ByteArrayInputStream(baos.toByteArray());
            meta = helper().extractMetadata(zeName, bais, fullIssueNumber, BaseType.getCDSR().get(), recordCache);
            bais.reset();
            String cdNumber = meta.getName();
            String basePath = getArticleBasePath(cdNumber, cdNumber);
            String recordPath = basePath + cdNumber + Extensions.XML;
            meta.setRecordPath(recordPath);
            results.setBasePath(basePath);
            putFileToRepository(rps, bais, recordPath);
            return meta;

        } catch (Throwable tr) {
            RecordHelper.handleErrorMessage(new ErrorInfo<>(zeName, tr.getMessage()), errors());
            throw tr;

        } finally {
            IOUtils.closeQuietly(bais);
        }
    }

    @Override
    public Object parseMetadata(@Null PackageType.Entry entry, String zeName, InputStream zis) {
        return ariesHelper.parseMetadata(packagePath, zeName, zis, this::errors, rps);
    }

    public Object parseImportFile(PackageType.Entry entry, String zeName, InputStream zis) {
        return ariesHelper.parseImportFile(packagePath, zeName, zis, reb.sPD().is(), this::errors, rps);
    }

    @Override
    protected boolean isEmpty() {
        return super.isEmpty() && (ariesHelper.importFile == null || !ariesHelper.importFile.isPublish());
    }

    @Override
    protected int notifyOnReceived(Integer dbId) {
        int ret = super.notifyOnReceived(dbId);
        if (ret == SuspendNotificationEntity.TYPE_NO_ERROR) {
            ariesHelper.acknowledgementOnReceived(getLibName(), dbId, getPackageFileName(), packageId,
                    getArticleCount() > 0 ? articles().get(0) : null, forSPD(), flLogger);

        } else if (ret != SuspendNotificationEntity.TYPE_DEFINED_ERROR) {
            AriesConnectionManager.copyPackageToBeingProcessedFolder(packageFileName, packagePath, rps);
        }
        return ret;
    }
}
