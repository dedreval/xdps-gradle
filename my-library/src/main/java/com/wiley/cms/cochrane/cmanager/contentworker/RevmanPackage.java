package com.wiley.cms.cochrane.cmanager.contentworker;

import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.send.cochrane.CochraneSender;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.SSLChecker;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.notification.SuspendNotificationEntity;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.BitValue;
import com.wiley.tes.util.ExceptionParser;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;
import org.apache.commons.io.IOUtils;
import org.cochrane.archie.service.Publishing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.wiley.cms.cochrane.cmanager.FilePathCreator.XML_EXT;
import static com.wiley.cms.cochrane.cmanager.res.PubType.TYPE_COCHRANE_P;
import static com.wiley.cms.cochrane.utils.Constants.JATS_FIG_DIR_SUFFIX;
import static com.wiley.cms.cochrane.utils.Constants.JATS_FINAL_EXTENSION;
import static com.wiley.cms.cochrane.utils.Constants.JATS_STATS_DIR_SUFFIX;
import static com.wiley.tes.util.Now.DATE_TIME_STANDARD_FORMAT;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 07.06.12
 */
public class RevmanPackage extends ArchiePackage {

    public static final String REVMAN_FOLDER = "revman";

    public static final String METADATA_SOURCE = "metadata.xml";
    public static final String TOPICS_PREV_SOURCE = "previous.xml";
    public static final String RESPONSE_FILE_NAME = "response.xml";
    public static final String SEND_TO_COCHRANE_BY_SFTP = "sendToCochraneBySftp";
    private static final Logger LOG = Logger.getLogger(RevmanPackage.class);
    private static final int BUFFER_SIZE = 1024;
    private static final int POSITION_ZIP_DATA  = 2;
    private static final int POSITION_ZIP_GROUP = 1;

    private static final FilenameFilter LOG_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith(Extensions.LOG);
        }
    };
    /** review cd number -> pub number, record id */
    private Map<String, Integer[]> reviewNames = new HashMap<>();

    private TranslatedAbstractsPackage taPackage;
    private final RevmanMetadataHelper helper;

    public RevmanPackage(URI packageUri) throws DeliveryPackageException {
        super(packageUri);
        helper =  new RevmanMetadataHelper(CochraneCMSBeans.getRecordManager(), rs);
    }

    public RevmanPackage(String packageFileName) {
        super(packageFileName);
        helper =  new RevmanMetadataHelper(CochraneCMSBeans.getRecordManager(), rs);
    }

    public DeliveryPackageInfo extractData(int issueId, int clDbId, int type,
        IActivityLog logger, IRecordCache cache) throws DeliveryPackageException, IOException {

        DeliveryPackageInfo dfInfo = new DeliveryPackageInfo(issueId, libName, packageId, getPackageFileName());
        dfInfo.setDbId(clDbId);
        DeliveryPackageInfo taDfInfo = null;

        File tmp = File.createTempFile("tmp" + packageFileName, "");
        ZipInputStream zis = getZipStream(tmp);
        rs.setDeliveryFileStatus(packageId, IDeliveryFileStatus.STATUS_PICKED_UP, true);
        pckTimestamp = String.valueOf(packageId);
        recordCache = cache;

        String realDirToZipStore = rps.getRealFilePath(getRevmanFolder(pckTimestamp, issueId, libName));
        boolean all = parseZip(issueId, zis, dfInfo, realDirToZipStore);
        tmp.delete();

        Set<String> failedNames  = new HashSet<>();
        boolean valid = quickValidate(dfInfo, failedNames, rps, cache);

        int typeUpdated = reviewNames.isEmpty() ? DeliveryFileEntity.TYPE_DEFAULT : DeliveryFileEntity.TYPE_REVMAN;

        if (hasTranslatedPackage()) {
            typeUpdated = BitValue.setBit(1, typeUpdated);
            taDfInfo = processTranslations(dfInfo, logger, cache, failedNames);
        } else if (typeUpdated == DeliveryFileEntity.TYPE_DEFAULT) {
            typeUpdated = DeliveryFileEntity.TYPE_REVMAN;
        }
        removeLogFiles(realDirToZipStore);
        rs.setDeliveryFileStatus(packageId, IDeliveryFileStatus.STATUS_UNZIPPED, true, typeUpdated);

        if (isAutRepeat(packageFileName)) {
            reb.commitWhenReady(CochraneCMSBeans.getRecordManager());

        } else if (notifyReceived(reb, null) != SuspendNotificationEntity.TYPE_NO_ERROR) {
            reb.cancelWhenReady(CochraneCMSBeans.getRecordManager());
            cache.removeRecords(reviewNames.keySet(), false);
            throw new DeliveryPackageException(NOTIFICATION_REC_ERROR_MSG, IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
        if (valid && all) {
            // reset included names
            reviewNames.clear();
            reviewNames = null;
        }
        if (containsOnlyBeingProcessedArticles(dfInfo, taDfInfo)) {
            if (isAut()) {
                new File(ArchieDownloader.INPUT_FOLDER + fullIssueNumber + FilePathCreator.SEPARATOR
                        + packageFileName).delete();
            }
            throw new DeliveryPackageException(dfInfo);
        }
        return dfInfo;
    }

    private static boolean containsOnlyBeingProcessedArticles(DeliveryPackageInfo dfInfo,
                                                              DeliveryPackageInfo taDfInfo) {
        int all = dfInfo.getStatAll();
        int beingProc = dfInfo.getStatBeingProcessed();
        if (taDfInfo != null) {
            all += taDfInfo.getStatAll();
            beingProc += taDfInfo.getStatBeingProcessed();
        }
        return beingProc > 0 && beingProc >= all;
    }

    private static void removeLogFiles(String realDirToZipStore) {
        File logDir = new File(realDirToZipStore);
        File[] logs = logDir.listFiles(LOG_FILTER);
        if (logs != null) {
            for (File fl: logs) {
                fl.delete();
            }
        }
    }

    public boolean hasOnlyErrors() {
        return reviewNames != null && reviewNames.isEmpty();
    }

    public Set<String> getIncludedNames() {
        return reviewNames == null ? null : reviewNames.keySet();
    }

    public boolean hasTranslatedPackage() {
        return taPackage != null;
    }

    static DeliveryPackageInfo collectUnpackedData(IRepository rp, DeliveryFileEntity df, String libName) {
        LOG.debug("revman unpacked data are collecting, packageId=" + df.getId());

        DeliveryPackageInfo packageInfo = new DeliveryPackageInfo(df.getIssue().getId(), libName, df.getId(),
                df.getName());
        String path = getRevmanFolder(String.valueOf(df.getId()), packageInfo.getIssueId(), libName);
        File[] groups = rp.getFilesFromDir(path);
        if (groups == null) {
            return packageInfo;
        }
        for (File group: groups) {
            if (!group.isDirectory()) {
                continue;
            }
            String groupDirName = group.getName();
            String groupDirPath = path + FilePathCreator.SEPARATOR + groupDirName;
            packageInfo.addRecordPath(groupDirName, groupDirPath);
        }
        return packageInfo;
    }

    public static boolean hasPreviousEditorial() {
        return hasPreviousEditorial(null);
    }

    private static boolean hasPreviousEditorial(Date checkDate) {
        IRepository rps = RepositoryFactory.getRepository();
        String previousTopicPath = FilePathBuilder.getPathToEntireRevmanGroup(GroupVO.SID_EDITORIAL)
                + TOPICS_PREV_SOURCE;
        synchronized (RevmanPackage.class) {
            try {
                if (rps.isFileExists(previousTopicPath)) {
                    return checkDate == null || rps.getFileLastModified(previousTopicPath) < checkDate.getTime();
                }
            } catch (IOException ie) {
                logger().error(ie);
            }
        }
        return false;
    }

    public static void removePreviousEditorial() {
        IRepository rps = RepositoryFactory.getRepository();
        String previousTopicPath = rps.getRealFilePath(FilePathBuilder.getPathToEntireRevmanGroup(GroupVO.SID_EDITORIAL)
                + TOPICS_PREV_SOURCE);
        synchronized (RevmanPackage.class) {
            try {
                if (rps.isFileExists(previousTopicPath)) {
                    rps.deleteFile(previousTopicPath);
                }
            } catch (IOException ie){
                logger().error(ie);
            }
        }
    }

    public static boolean checkEditorial(String groupName, String topicTo, String revmanDirPath, IRepository rps)
        throws IOException {
        if (!GroupVO.SID_EDITORIAL.equalsIgnoreCase(groupName)) {
            return false;
        }
        String topicPrev = revmanDirPath + groupName + FilePathCreator.SEPARATOR + TOPICS_PREV_SOURCE;
        synchronized (RevmanPackage.class) {
            if (!rps.isFileExists(topicPrev)) {
                rps.putFile(topicPrev, rps.getFile(topicTo), true);
            }
        }
        return true;
    }

    public static String getRecordNameByFileName(String filename) {
        return RecordHelper.getRecordAttrsByFileName(filename)[0];
    }

    static Pair<String, String> getTranslationFileName(String filename) {
        String[] names = parseNamePath(filename);
        if (names == null || names.length <= POSITION_ZIP_DATA
                || !TRANSLATION_FOLDER.equals(names[POSITION_ZIP_DATA])) {
            return null;
        }
        return new Pair<>(names[names.length - 1], names[POSITION_ZIP_GROUP]);
    }

    public static String checkPackage(byte[] packet, MessageSender.Help callback) throws Exception {
        if (packet == null) {
            throw new Exception(ERROR_INVALID_ZIP);
        }
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet));
        ZipEntry ze1 = zis.getNextEntry();
        ZipEntry ze2 = zis.getNextEntry();
        ZipEntry ze3 = zis.getNextEntry();
        try {
            if (ze3 != null) {
                // the packet is not empty => it's OK
                zis.close();
                return null;
            }
            if (ze1 == null) {
                throw new Exception(ERROR_EMPTY_ZIP);
            }
            if (ze2 != null && ze1.getName().equalsIgnoreCase(OUTPUT_LOG)) {
                // skip output.log
                ze1 = ze2;
            }

            String ret = null;
            if (ze1.getName().equalsIgnoreCase(ERROR_LOG)) {
                // zip contains err
                ret = getErrorLog(packet);
                String err = buildArchieResponseMsg(ERROR_LOG + ". Details:\n" + (ret.isEmpty() ? "<empty>" : ret));
                logger().error(err);
                if (callback != null) {
                    callback.sendMessage(err);
                } else {
                    throw new Exception(err);
                }
            } else if (!ze1.getName().equalsIgnoreCase(SUCCESS_LOG)) {
                ret = ERROR_INVALID_ZIP + ". It contains unknown record: " + ze1.getName();
                logger().error(ret);
                throw new Exception(ret);
            }
            return ret;

        } finally {
            IOUtils.closeQuietly(zis);
        }
    }

    private static String getErrorLog(byte[] packet) throws Exception {
        ZipEntry ze;
        String ret = "";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet))) {
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equalsIgnoreCase(ERROR_LOG)) {
                    ret = InputUtils.readStreamToString(zis);
                    break;
                }
            }
        }
        return ret;
    }

    static boolean notifyReceived(String out) throws Exception {
        return CochraneCMSPropertyNames.isArchieDownloadTestMode() || notifyReceived(out, ON_GET, true) == null;
    }

    public static String notifyPublishedRepeated(String fileName, String out) {
        if (CochraneCMSPropertyNames.isArchieDownloadTestMode()) {
            log(null, new NotificationInfo(out, "", NOTIFICATION_ON_PUBLISH_POSTFIX), null, true, fileName);
            return null;
        }
        String err = null;
        try {
            err = notifyPublished(out, ON_PUB, true);
            if (err != null) {
                // Controlled error
                sendCriticalError(err, null, out, null, OPERATION_PUBLISHED);
            }

        } catch (javax.xml.ws.WebServiceException we) {
            // Uncontrolled error. Suspend sending till next downloading time.
            ON_PUB.sendMessage(buildArchieCallMsg(ExceptionParser.buildMessage(we)));
            throw we;

        } catch (Exception e) {
            // Uncontrolled and undefined error. At first, try again.
            if (!handleUndefinedError(null, out, e, OPERATION_PUBLISHED, true, ON_PUB)) {
                err = e.getMessage();
            }
        }
        log(null, new NotificationInfo(out, "", NOTIFICATION_ON_PUBLISH_POSTFIX), err, err == null, fileName);
        return err;
    }

    static boolean checkEmpty(ArchieResponseBuilder reb) {
        boolean ret = reb.size() == 0;
        if (ret) {
            reb.commitWhenReady(CochraneCMSBeans.getRecordManager());
        }
        return ret;
    }

    static int notifyReceived(ArchieResponseBuilder reb, IFlowLogger flLogger) {
        if (reb == null || checkEmpty(reb)) {
            return SuspendNotificationEntity.TYPE_NO_ERROR;
        }

        int ret = SuspendNotificationEntity.TYPE_NO_ERROR;
        String out = reb.getPrettyBody();
        String err = null;
        if (CochraneCMSPropertyNames.isArchieDownloadTestMode()
                || CochraneCMSPropertyNames.isCochraneSftpPublicationNotificationFlowEnabled()) {
            if (CochraneCMSPropertyNames.isArchieImitateError()) {
                ret = handleUndefinedError(reb, out, new Exception(ERROR_INVALID_ZIP),
                    OPERATION_RECEIVED, false, ON_RECEIVE) ? SuspendNotificationEntity.TYPE_NO_ERROR
                        : SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
            }
            boolean noErr = ret == SuspendNotificationEntity.TYPE_NO_ERROR;
            endDashboardEvent(reb, flLogger, noErr);
            reb.commitWhenReady(CochraneCMSBeans.getRecordManager());
        } else {
            try {
                logger().debug("notify on CDSR WhenReady received");
                ret = notifyReceived(out, ON_RECEIVE, true) == null ? SuspendNotificationEntity.TYPE_NO_ERROR
                        : SuspendNotificationEntity.TYPE_DEFINED_ERROR;

            }  catch (javax.xml.ws.WebServiceException we) {
                // Uncontrolled error. The package will be stopped till next downloading time.
                String archieCallMsg = buildArchieCallMsg(ExceptionParser.buildMessage(we));
                reb.setErrorMessage(archieCallMsg);
                ON_RECEIVE.sendMessage(archieCallMsg);
                logger().error(we);
                ret = SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
                err = we.getMessage();

            } catch (Exception e) {
                // Uncontrolled and undefined error. At first, try again.
                ret = handleUndefinedError(reb, out, e, OPERATION_RECEIVED, false, ON_RECEIVE)
                        ? SuspendNotificationEntity.TYPE_NO_ERROR : SuspendNotificationEntity.TYPE_UNDEFINED_ERROR;
                err = e.getMessage();
            }
            boolean noErr = ret == SuspendNotificationEntity.TYPE_NO_ERROR;
            endDashboardEvent(reb, flLogger, noErr);
            log(reb, new NotificationInfo(out, "received: [", NOTIFICATION_ON_RECEIVE_POSTFIX), err, noErr);
        }
        return ret;
    }

    private static void endDashboardEvent(ArchieResponseBuilder reb, IFlowLogger flLogger, boolean noErr) {
        if (flLogger != null) {
            flLogger.onDashboardEventEnd(reb.getProcess(), reb.getPackageId(), noErr, reb.sPD().is());
        }
    }

    private static void log(ArchieResponseBuilder reb, NotificationInfo info, String err, boolean ret) {
        log(reb, info, err, ret, null);
    }

    private static void log(ArchieResponseBuilder reb, NotificationInfo info, String err, boolean ret,
                            String fileName) {
        if (ret) {
            printNotification4Debug(info.out, info.postfix, false);

            if (reb != null){
                reb.commitWhenReady(CochraneCMSBeans.getRecordManager());
            }

        } else if (fileName != null) {
            CmsUtils.saveNotification(info.out, ArchieDownloader.RESPONSE_FOLDER + fileName);
            info.out = err != null ? info.out + "\n\n" + err : info.out;
            printNotification4Debug(info.out, info.postfix, true);

        } else {
            saveOutputNotification(reb, info.out, info.postfix);
            info.out = err != null ? info.out + "\n\n" + err : info.out;
            printNotification4Debug(info.out, info.postfix, true);
        }
    }

    private static String notifyPublished(String out, MessageSender.Help h, boolean repeat) throws Exception {
        if (CochraneCMSPropertyNames.isCochraneSftpPublicationNotificationFlowEnabled()) {
            return null;
        }
        return performNotificationOnPublished(out, h, repeat);
    }

    private static String performNotificationOnPublished(String out,
                                                         MessageSender.Help h, boolean repeat) throws Exception {
        try {
            Publishing pub = WebServiceUtils.getPublishing();
            byte[] packet = pub.contentPublished(CochraneCMSPropertyNames.getArchieDownloadPassword(), out);
            return checkPackage(packet, h);

        } catch (Exception e) {
            if (repeat && SSLChecker.checkCertificate(CochraneCMSPropertyNames.getArchieDownloadService() , e)) {
                return notifyPublished(out, h, false);
            } else {
                throw  e;
            }
        }
    }

    private static String notifyReceived(String out, MessageSender.Help h, boolean repeat) throws Exception {
        if (CochraneCMSPropertyNames.isCochraneSftpPublicationNotificationFlowEnabled()) {
            return null;
        }
        return performNotificationOnReceived(out, h, repeat);
    }

    private static String performNotificationOnReceived(String out,
                                                        MessageSender.Help h, boolean repeat) throws Exception {
        try {
            Publishing pub = WebServiceUtils.getPublishing();
            byte[] packet = pub.contentReceived(CochraneCMSPropertyNames.getArchieDownloadPassword(), out);
            return checkPackage(packet, h);

        } catch (Exception e) {
            if (repeat && SSLChecker.checkCertificate(CochraneCMSPropertyNames.getArchieDownloadService(), e)) {
                return notifyReceived(out, h, false);
            } else {
                throw e;
            }
        }
    }

    private static boolean handleUndefinedError(ArchieResponseBuilder rb, String out, Exception initial, String opName,
                                                boolean onPublish, MessageSender.Help h) {
        logger().warn(opName + " - error: ", initial);
        logger().warn(opName + " - next attempt to call ...");
        String fullMsg = ExceptionParser.buildMessage(initial);
        h.sendMessage(String.format("Uncontrolled errors: %s.\nOperation %s will be called again.", fullMsg, opName));
        try {
            String ret = onPublish ? notifyPublished(out, null, true) : notifyReceived(out, null, true);
            if (ret != null) {
                // cannot be here
                throw new Exception(ret);
            }
            return true;

        } catch (Exception e) {
            logger().error(opName + " second error: ", e);
            sendCriticalError(fullMsg, e, out, rb, opName);
        }
        return false;
    }

    public static boolean notifyPublished(ArchieResponseBuilder reb, IFlowLogger flowLogger) {
        if (reb == null || checkEmpty(reb)) {
            return true;
        }

        logger().debug("notify on CDSR WhenReady data published");
        boolean ret = false;
        boolean suspend = false;
        String out = reb.getPrettyBody();
        String err = null;

        boolean testMode = CochraneCMSPropertyNames.isArchieDownloadTestMode();
        if (testMode) {
            ret = !CochraneCMSPropertyNames.isArchieImitateError() || handleUndefinedError(reb, out, new Exception(
                    ERROR_INVALID_ZIP), OPERATION_PUBLISHED, true, ON_PUB);
        } else if (CochraneCMSPropertyNames.isArchieDownload() || CochraneCMSPropertyNames.isPublishAfterUpload()) {
            try {
                err = notifyPublished(out, ArchiePackage.ON_PUB, false);
                if (err != null) {
                    sendCriticalError(err, null, out, reb, OPERATION_PUBLISHED);
                } else {
                    ret = true;
                }

            } catch (javax.xml.ws.WebServiceException we) {
                // Uncontrolled error. Suspend sending till next downloading time.
                handleSOAPException(we, reb);
                reb.suspendWhenReady(we.getMessage());
                err = we.getMessage();
                suspend = true;

            } catch (Exception e) {
                // Uncontrolled and undefined error. At first, try again.
                ret = handleUndefinedError(reb, out, e, OPERATION_PUBLISHED, true, ON_PUB);
                err = e.getMessage();
            }
        } else {
            err = "notifying on When Ready publishing was disabled!";
            logger().warn(err);
            reb.setErrorMessage(err);
        }
        if (flowLogger != null) {
            flowLogger.onDashboardEventEnd(reb.getProcess(), reb.getPackageId(), ret, reb.sPD().is());
        }
        log(reb, new NotificationInfo(out, "published: [", NOTIFICATION_ON_PUBLISH_POSTFIX), err, !suspend && ret);
        return ret;
    }

    public static String createNotificationFile(ClDbVO dbVo, PublishedAbstractEntity pae, ArchieResponseBuilder reb) {
        PublishStorage ps = (PublishStorage) CochraneCMSBeans.getPublishStorage();

        DeliveryFileEntity deliveryFileEntity = ps.getManager()
                .find(DeliveryFileEntity.class, pae.getInitialDeliveryId());

        final String metadataJson = buildMetadata(pae, deliveryFileEntity);
        reb.setCochrnaneNotification(metadataJson);
        reb.setPackageName(deliveryFileEntity.getName());

        final String recordName = pae.getRecordName();
        final String xmlFilePath = getXmlFilePath(pae, deliveryFileEntity, recordName);
        IRepository rp = RepositoryFactory.getRepository();

        String filePath = null;

        try (InputStream xmlInputStream = rp.getFile(xmlFilePath);
             ByteArrayOutputStream zipByteArrayOutputStream = new ByteArrayOutputStream();) {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(zipByteArrayOutputStream)) {
                addToZip(new ByteArrayInputStream(metadataJson.getBytes()), zipOutputStream, "metadata.json");

                final String entryName = recordName + JATS_FINAL_EXTENSION;
                addToZip(xmlInputStream, zipOutputStream, entryName);

                final String srcPath = getSrcJATSDirFilePath(deliveryFileEntity, recordName);
                if (!pae.hasLanguage()) {
                    putAssetsFilesToArchive(rp, srcPath, recordName, zipOutputStream);
                }
            }
            final byte[] zipBytes = zipByteArrayOutputStream.toByteArray();
            final String zipFileName = String.format("notification_%s.zip", UUID.randomUUID());
            filePath = getRealFilePath(zipFileName, dbVo);
            putFileToRepository(rp, new ByteArrayInputStream(zipBytes), filePath, true);
        } catch (Exception e) {
            String errorMessage = getErrorMessage(pae) + "Error creating notification file.";
            reb.setErrorMessage(errorMessage);
            RevmanPackage.sendCochraneCriticalError(pae, reb, SEND_TO_COCHRANE_BY_SFTP);
        }
        return filePath;
    }

    public static boolean sendToCochraneBySftp(ClDbVO dbVo, String filePath,
                                               PublishedAbstractEntity pae, ArchieResponseBuilder reb) {
        CochraneSender cochraneSender = CochraneSender.createInstance(dbVo);
        boolean success = cochraneSender.sendBySftp(filePath);
        if (!success) {
            String errorMessage = getErrorMessage(pae) + "as Cochrane SFTP is unavailable.";
            reb.setErrorMessage(errorMessage);
            RevmanPackage.sendCochraneCriticalError(pae, reb, SEND_TO_COCHRANE_BY_SFTP);
        }
        return success;
    }

    private static String getErrorMessage(PublishedAbstractEntity pae) {
        return String.format("Operation 'on published' was failed for %s.pub%s%s ",
                pae.getRecordName(), pae.getPubNumber(), pae.hasLanguage() ? "." + pae.getLanguage() : "");
    }

    private static void putAssetsFilesToArchive(IRepository rp, String srcPath, String recordName,
                                                ZipOutputStream zipOutputStream) {
        final Path statsPath = createPath(srcPath, recordName, JATS_STATS_DIR_SUFFIX);
        final Path figuresPath = createPath(srcPath, recordName, JATS_FIG_DIR_SUFFIX);

        File[] statFilesFromDir = rp.getFilesFromDir(statsPath.toString());
        File[] figFilesFromDir = rp.getFilesFromDir(figuresPath.toString());

        addFilesToZip(statsPath, statFilesFromDir, zipOutputStream);
        addFilesToZip(figuresPath, figFilesFromDir, zipOutputStream);
    }

    private static Path createPath(String srcPath, String recordName, String suffix) {
        String fullPath = RepositoryFactory.getRepository().getRealFilePath(FilePathCreator.SEPARATOR + srcPath);
        return Paths.get(fullPath, recordName + suffix);
    }

    private static void addFilesToZip(Path folderPath, File[] files, ZipOutputStream zipOutputStream) {
        if (files != null) {
            Arrays.stream(files)
                    .filter(File::isFile)
                    .map(file -> folderPath.relativize(file.toPath()).toString())
                    .forEach(entryName -> {
                        try (InputStream inputStream = Files.newInputStream(folderPath.resolve(entryName))) {
                            addToZip(inputStream, zipOutputStream, entryName);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static String getSrcJATSDirFilePath(DeliveryFileEntity deliveryFileEntity, String recordName) {
        final Integer issueId = deliveryFileEntity.getIssue().getId();
        final int dfId = deliveryFileEntity.getId();
        return FilePathBuilder.JATS.getPathToSrcDir(issueId, dfId, recordName);
    }

    private static String getXmlFilePath(PublishedAbstractEntity pae,
                                         DeliveryFileEntity deliveryFileEntity, String recordName) {
        final Integer issueId = deliveryFileEntity.getIssue().getId();
        final int dfId = deliveryFileEntity.getId();
        if (pae.hasLanguage()){
            return FilePathBuilder.TR.getPathToJatsTADir(issueId, dfId, pae.getLanguage(), pae.getRecordName())
                    + FilePathCreator.SEPARATOR + pae.getRecordName() + Extensions.XML;
        } else {
            String fileName = String.format("%s%s%s%s", recordName, FilePathCreator.SEPARATOR,
                    recordName, XML_EXT);
            return  FilePathBuilder.JATS.getPathToSrcDir(issueId, dfId, fileName);
        }
    }

    public static String getRealFilePath(String fileName, ClDbVO db) {
        int issueId = db.getIssue().getId();
        boolean aut = issueId != DbEntity.NOT_EXIST_ID;
        String contentPath = FilePathCreator.getDirPathForPublish(db.getTitle(), db.getIssue().getId(),
                TYPE_COCHRANE_P, aut);
        String filePath = contentPath + fileName;

        return RepositoryFactory.getRepository().getRealFilePath(FilePathCreator.SEPARATOR + filePath);
    }

    private static String buildMetadata(PublishedAbstractEntity pae, DeliveryFileEntity deliveryFileEntity) {
        final String deliveryFile = deliveryFileEntity.getName();
        final String publicationDate = DATE_TIME_STANDARD_FORMAT.format(pae.getPublishedDate());
        return String.format("{\"sourcePackage\": \"%s\", \"publishedAt\": \"%s\"}", deliveryFile, publicationDate);
    }

    private static void addToZip(InputStream inputStream, ZipOutputStream zipOutputStream,
                                 String entryName) throws IOException {
        try {
            ZipEntry entry = new ZipEntry(entryName);
            zipOutputStream.putNextEntry(entry);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                zipOutputStream.write(buffer, 0, bytesRead);
            }
        } finally {
            zipOutputStream.closeEntry();
            IOUtils.closeQuietly(inputStream);
        }
    }

    private static void handleSOAPException(Exception se, ArchieResponseBuilder reb) {
        logger().warn("Publishing service hasn't responded, the notification will be re-sent next time. ", se);
        String archieCallMsg = buildArchieCallMsg(
                ExceptionParser.buildMessage(se, " The notification will be re-sent next time."));
        reb.setErrorMessage(archieCallMsg);
        ON_PUB.sendMessage(archieCallMsg);
    }

    private static void saveOutputNotification(ArchieResponseBuilder rb, String out, String prefix)  {
        CmsUtils.saveNotification(out, ArchieDownloader.RESPONSE_FOLDER + buildFileName(rb, prefix));
    }

    private static String buildFileName(ArchieResponseBuilder rb, String prefix) {
        return rb.getProcess() + "_" + prefix + Extensions.XML;
    }

    private boolean quickValidate(DeliveryPackageInfo packInfo, Set<String> failedNames, IRepository rp,
        IRecordCache cache) {

        Set<String> failedGroups = new HashSet<>();
        ErrorResults results = new ErrorResults(failedNames);
        boolean ret = true;

        Map<String, String> recs = packInfo.getRecordPaths();
        for (String group: recs.keySet()) {
            String revmanGroupDir = recs.get(group);
            try {
                if (!validateGroup(revmanGroupDir, group, results, failedGroups, packInfo, rp, cache) && ret) {
                    ret = false;
                }
            } catch (Exception e) {
                LOG.error(e.getMessage());
                prepareFailedRecord(group, e.getMessage(), null, results.err, results.externalResults, null, null);
                failedGroups.add(group);
            }
        }
        if (results.err.length() > 0) {
            results.err.delete(results.err.length() - 2, results.err.length());
            MessageSender.sendFailedLoadPackageMessage(packageFileName, results.err.toString());
        }
        for (String group: failedGroups) {
            packInfo.removeRecord(group);
        }
        return ret;
    }

    private boolean validateGroup(String groupDir, String group, ErrorResults results,
        Set<String> failedGroups, DeliveryPackageInfo dfInfo, IRepository rp, IRecordCache cache) throws Exception {

        File[] reviews = rp.getFilesFromDir(FilePathCreator.getDirForRevmanReviews(groupDir));
        boolean reviewExist = reviews != null && reviews.length > 0;

        String metadataPath = groupDir + FilePathCreator.SEPARATOR + METADATA_SOURCE;
        if (!rp.isFileExists(metadataPath)) {
            if (!reviewExist) {
                return true;
            }
            throw new CmsException("Revman metadata.xml is not exist.");
        }
        List<ErrorInfo> records = helper.checkRevmanElements(CmsUtils.getIssueNumber(getYear(), getIssueNumber()),
                dfInfo, group, rp.getFile(metadataPath), cache, reviewNames, isAut());

        boolean groupFailed = setWhenReadyRecords(records, results, reviewNames.keySet(), null, group, reb, cache);
        if (groupFailed) {
            failedGroups.add(group);
        }

        boolean ret = results.curr.isEmpty();
        results.curr.clear();
        return ret;
    }

    public static boolean setWhenReadyRecords(List<ErrorInfo> errors, ErrorResults results, Set<String> includes,
        Set<String> existed, String groupName, ArchieResponseBuilder reb, IRecordCache cache) {

        boolean allFail = false;

        for (ErrorInfo ri : errors) {

            Object entity = ri.getErrorEntity();
            ArchieEntry rel = (entity instanceof ArchieEntry) ? (ArchieEntry) entity : null;
            String name = rel != null ? RevmanMetadataHelper.buildPubName(rel.getName(), rel.getPubNumber())
                    : entity.toString();
            String errMsg = ri.getErrorDetail();

            if (ri.isError()) {

                if (!allFail && name.equals(groupName)) {

                    allFail = true;
                    prepareFailedRecord(name, errMsg, ri, results.err, results.externalResults, null, null);
                } else {
                    prepareFailedRecord(name, errMsg, ri, results.err, results.externalResults, results.curr, includes);
                }

                if (reb != null) {
                    reb.addContent(rel != null ? rel.asErrorElement(reb, ri, null) : reb.asErrorElement(name, ri));
                }

                updateCache(name, ri, existed, cache);

            } else if (reb != null && rel != null && !rel.isDeleted()) {
                reb.addContent(rel.asSuccessfulElement(reb, null));

            } else if (reb != null) {
                reb.addContent(rel != null ? rel.asSuccessfulElement(reb, null) : reb.asSuccessfulElement(name));
            }
        }

        return allFail;
    }

    private static void updateCache(String recName, ErrorInfo ri, Set<String> existed, IRecordCache cache) {
        if (!ri.isSystemError() && (existed == null || !existed.contains(recName))) {
            cache.removeRecord(recName, false);
        }
    }

    private static void prepareFailedRecord(String recName, String errMsg, ErrorInfo ei, StringBuilder errBuffer,
        Set<String> results, Map<String, ErrorInfo> noMoves, Set<String> includes) {

        MessageSender.addMessage(errBuffer, recName, errMsg);
        if (noMoves != null) {
            noMoves.put(recName, ei);
        }
        if (includes != null) {
            includes.remove(recName);
        }
        if (results != null) {
            results.add(recName);
        }
    }

    private DeliveryPackageInfo processTranslations(DeliveryPackageInfo info, IActivityLog logger,
        IRecordCache cache, Set<String> failedNames) throws DeliveryPackageException {
        try {
            DeliveryPackageInfo packageInfoTa = new DeliveryPackageInfo(info.getIssueId(), getLibName(),
                    info.getDbId(), getPackageId(), getPackageFileName());
            taPackage.extractData(packageInfoTa, reviewNames, failedNames, logger, cache);
            packageInfoTa.moveTranslations(info);
            taPackage.clear(false);

            return packageInfoTa;

        } catch (DeliveryPackageException de) {

            cache.removeRecords(reviewNames.keySet(), false);
            taPackage.clear(true);
            throw de;

        } catch (Exception e) {

            cache.removeRecords(reviewNames.keySet(), false);
            taPackage.clear(true);
            throw new DeliveryPackageException("Cannot parse package: " + e.getMessage(),
                IDeliveryFileStatus.STATUS_PICKUP_FAILED);
        }
    }

    private static String[] parseNamePath(String fullname) {
        String tail = REVMAN_FOLDER + FilePathCreator.SEPARATOR + PackageChecker.replaceBackslash2Forward(fullname);

        String[] names = tail.split("/");
        if (names.length < 2) {
            return null;
        }

        return names;
    }

    public static String getRevmanFolder(String pckIdentifier, int issueId, String libName) {
        return FilePathCreator.getFilePathForEnclosure(pckIdentifier, issueId, libName, new String[] {REVMAN_FOLDER});
    }

    private static void addReviewName(String name, int pubNum, Map<String, Integer[]> names) {
        Integer[] ids = {pubNum, null};
        names.put(name, ids);
    }

    @Override
    protected Collection<String> getArticleNames() {
        return reviewNames.keySet();
    }

    @Override
    public int getArticleCount() {
        return reviewNames.size();
    }

    @Override
    protected boolean parseZipEntry(Integer issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo, ZipEntry ze) {
        String reviewName =  null;
        boolean ret = true;
        try {
            String[] names = parseNamePath(ze.getName());
            if (names == null) {
                return true;
            }

            String path = FilePathCreator.getFilePathForEnclosure(pckTimestamp, issueId, libName, names);
            String groupDirName = names[POSITION_ZIP_GROUP];

            if (names.length > POSITION_ZIP_DATA + 1) {

                String nextDirName = names[POSITION_ZIP_DATA];
                if (REVIEW_FOLDER.equalsIgnoreCase(nextDirName)) {

                    String[] attrs = RecordHelper.getRecordAttrsByFileName(names[names.length - 1]);
                    reviewName = attrs[0];
                    addReviewName(reviewName, attrs.length > 1 ? RevmanMetadataHelper.parsePubNumber(attrs[1])
                            : Constants.FIRST_PUB, reviewNames);

                } else if (!hasTranslatedPackage() && TRANSLATION_FOLDER.equalsIgnoreCase(nextDirName)) {
                    taPackage = new TranslatedAbstractsPackage(this, issueId);
                }
            } else if (names.length > POSITION_ZIP_GROUP + 1 && !packageInfo.hasRecordPath(groupDirName)) {
                // store group
                String groupDirPath = FilePathCreator.getFilePathForEnclosure(
                    pckTimestamp, String.valueOf(issueId), libName, REVMAN_FOLDER, groupDirName);
                packageInfo.addRecordPath(groupDirName, groupDirPath);
            }

            putFileToRepository(rps, zis, path);

        } catch (Exception e) {
            if (reviewName != null) {
                if (isAut()) {
                    reb.addContent(ArchieResponseBuilder.asErrorElement(reviewName, null,
                            "package parsing error", false));
                }
                reviewNames.remove(reviewName);
                ret = false;
            }
            LOG.error(e.getMessage());
        }
        return ret;
    }

    private static class NotificationInfo {
        String out;
        final String prefix;
        final String postfix;

        NotificationInfo(String out, String prefix, String postfix) {
            this.out = out;
            this.prefix = prefix;
            this.postfix = postfix;
        }
    }
}
