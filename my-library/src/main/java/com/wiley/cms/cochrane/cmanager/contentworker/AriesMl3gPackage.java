package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.validation.constraints.Null;

import org.apache.commons.io.IOUtils;

import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.notification.SuspendNotificationEntity;
import com.wiley.tes.util.Extensions;
import org.apache.commons.lang.StringUtils;
import org.jdom.Document;

import static com.wiley.cms.cochrane.cmanager.contentworker.AriesImportFile.DELIVER_TO_WILEY;
import static com.wiley.cms.cochrane.cmanager.contentworker.AriesSFTPPackage.PACKAGE_HANDLED_ERROR;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 1/3/2021
 */
public class AriesMl3gPackage extends DeliveryPackage {

    private static final Set<PackageType.EntryType> SUPPORTED_EMBEDDED_TYPES = new HashSet<>(Arrays.asList(
        PackageType.EntryType.ARTICLE, PackageType.EntryType.IMAGE, PackageType.EntryType.IMPORT,
            PackageType.EntryType.METADATA));

    private AriesHelper ariesHelper =  new AriesHelper(helper().getDocumentLoader());
    private FlowProduct.SPDState spd = FlowProduct.SPDState.NONE;

    public AriesMl3gPackage(URI packageUri, String dbName, int deliveryId, int fullIssueNumber) {
        super(packageUri, dbName, deliveryId, fullIssueNumber);

        if (CmsUtils.isScheduledIssueNumber(fullIssueNumber)) {
            spd = FlowProduct.SPDState.ON;
        }
    }

    @Override
    protected String parseVendor() {
        return PackageChecker.ARIES;
    }

    @Override
    public boolean isAut() {
        return true;
    }

    @Override
    protected boolean forSPD() {
        return spd.is();
    }

    @Override
    protected boolean parseZip(Integer issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo,
                               String realDirToZipStore) throws DeliveryPackageException {
        parseArticleZip(null, issueId, null, zis, false);
        return true;
    }

    @Override
    protected void checkEmpty(Integer issueId, boolean onlyBeingProcessed) throws Exception {
        boolean bp = onlyBeingProcessed && ariesHelper.isDeliver();
        if (bp) {
            AriesConnectionManager.copyPackageToBeingProcessedFolder(packageFileName, packagePath, rps);

        } else if (ariesHelper.isPublish() || ariesHelper.isCancel()) {
            String errorMessage = errors.stream().map(ErrorInfo::getErrorDetail).collect(Collectors.joining(", "));
            String msg = StringUtils.isNotBlank(errorMessage) ? errorMessage : PACKAGE_HANDLED_ERROR;
            throw errors == null ? new DeliveryPackageException(IDeliveryFileStatus.STATUS_PACKAGE_LOADED)
                : new DeliveryPackageException(msg,
                    IDeliveryFileStatus.STATUS_INVALID_CONTENT);
        }
        super.checkEmpty(issueId, bp);
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
    protected String checkForSPD(BaseType bt, String cdNumber, String pubName, int pub, boolean spd,
                                 boolean spdCanceled, boolean spdReprocess) throws CmsException {
        String ret = spdCanceled ? checkSPDCancelled(cdNumber, pubName, recordCache)
                : checkSPD(bt, cdNumber, pubName, pub, spd, spdReprocess, recordCache);
        if (ret != null && isAut()) {
            this.spd = FlowProduct.SPDState.NONE;
        }
        return ret;
    }

    @Override
    public void parseArticleZip(@Null PackageType.Entry entry, Integer issueId, @Null String fileName,
                                ZipInputStream zis, boolean ta) throws DeliveryPackageException {
        ZipEntry ze;
        ZipEntry zeLast = null;
        CDSRMetaVO meta;
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
                helper().validateZipEntryForEmptyFiles(zipEntries, meta);
            }

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
                addReview(results.getBasePath(), meta.getCdNumber(), meta.getPubNumber(), false,
                        meta.getRecordPath(), ariesHelper.getHighPriority(), meta);

            } else if (ariesHelper.isCancel()) {
                if (meta == null) {
                    throw CmsException.createForMissingMetadata(packageFileName);
                }
                addCanceledReview(bt, meta.getCdNumber(), meta.getPubNumber(), meta, dashboard);
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
    public Object parseEmbeddedArticle(PackageType.Entry entry, String zeName,
                                       InputStream zis, PackageArticleResults results) throws Exception {
        ByteArrayInputStream bais = null;
        CDSRMetaVO meta;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            IOUtils.copy(zis, baos);
            bais = new ByteArrayInputStream(baos.toByteArray());
            meta = helper().extractMetadata(zeName, bais, fullIssueNumber,
                    BaseType.find(getLibName()).get(), recordCache);
            bais.reset();
            String cdNumber = meta.getName();
            String recordPath = packagePath + cdNumber + Extensions.XML;
            meta.setRecordPath(recordPath);
            results.setBasePath(getArticleBasePath(cdNumber, cdNumber));
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
        return ariesHelper.parseImportFile(packagePath, zeName, zis, forSPD(), this::errors, rps);
    }

    @Override
    protected boolean isEmpty() {
        return super.isEmpty() && (ariesHelper.importFile == null || !ariesHelper.importFile.isPublish());
    }

    @Override
    protected int notifyOnReceived(Integer dbId) {
        ariesHelper.acknowledgementOnReceived(getLibName(), dbId, getPackageFileName(), packageId,
                getArticleCount() > 0 ? articles().get(0) : null, forSPD(), flLogger);
        return SuspendNotificationEntity.TYPE_NO_ERROR;
    }
}
