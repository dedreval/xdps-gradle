package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.IssueDate;
import com.wiley.cms.cochrane.utils.SSLChecker;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.cochrane.archie.service.Publishing;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageException;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/28/2019
 */
public abstract class ArchiePackage extends DeliveryPackage {

    public static final String SUCCESS_LOG = "success.log";
    public static final String OUTPUT_LOG = "output.log";
    public static final String ERROR_LOG = "error.log";

    public static final String REVIEW_FOLDER = "reviews";
    public static final String TRANSLATION_FOLDER = "translations";
    public static final String AS_A_RESULT_NOT_APPROVED =
            "\n\nAs a result, the following notification could not be approved:\n";

    static final String ERROR_INVALID_ZIP = "Archie Web service returns an invalid zip archive";
    static final String ERROR_EMPTY_ZIP   = "Archie Web service returns an empty zip archive";

    static final String OPERATION_RECEIVED  = "contentReceived()";
    static final String OPERATION_PUBLISHED = "contentPublished()";
    static final String OPERATION_DOWNLOAD  = "getContentForPublication()";

    static final String NOTIFICATION_ON_RECEIVE_POSTFIX = "rec";
    static final String NOTIFICATION_ON_PUBLISH_POSTFIX = "pub";

    static final MessageSender.Help ON_RECEIVE = createNotificationMessageCallback(
            MessageSender.MSG_TITLE_WR_SEND_NOTIFICATION_WARN, WebServiceUtils.PUBLISHING_SERVICE, OPERATION_RECEIVED);

    static final MessageSender.Help ON_PUB = createNotificationMessageCallback(
            MessageSender.MSG_TITLE_WR_SEND_NOTIFICATION_WARN, WebServiceUtils.PUBLISHING_SERVICE, OPERATION_PUBLISHED);

    static final MessageSender.Help ON_GET = createNotificationMessageCallback(
            MessageSender.MSG_TITLE_WR_SEND_NOTIFICATION_WARN, WebServiceUtils.PUBLISHING_SERVICE, OPERATION_DOWNLOAD);

    static final String NOTIFICATION_REC_ERROR_MSG = "notification error for received data";

    private static final Logger LOG = Logger.getLogger(ArchiePackage.class);
    private static final Logger LOG_AN = Logger.getLogger("ArchieNotification");

    private static final int PACKAGE_NAME_SIZE = 40;

    ArchieResponseBuilder reb = null;

    ArchiePackage(URI packageUri) throws DeliveryPackageException {
        super(packageUri);
    }

    ArchiePackage(String packageFileName) {
        super(packageFileName);
    }

    ArchiePackage(URI packageUri, String dbName, int deliveryId, int fullIssueNumber) {
        super(packageUri, dbName, deliveryId, fullIssueNumber);

        setAut(fullIssueNumber);
        if (isAut()) {
            reb.sPD(CmsUtils.isScheduledIssueNumber(fullIssueNumber), false);
        }
    }

    protected static Logger logger() {
        return LOG;
    }

    @Override
    protected ArchieResponseBuilder getResponseBuilder() {
        return reb;
    }

    public static File downloadArchiePackage(int year, int month, int issueY, int issueM, IContentManager cm)
            throws Exception {

        File packet = null;
        if (CochraneCMSPropertyNames.isArchieDownloadTestMode()) {

            File dir = RepositoryUtils.getRealFile(ArchieDownloader.TEMP_FOLDER);
            if (!dir.exists())  {
                LOG.error(ArchieDownloader.TEMP_FOLDER + " folder with test packets doesn't exist");

            } else {
                downloadCochranePackageFromTestFolder(issueY, issueM, dir, cm);
            }

        } else {
            packet = downloadArchiePackage(year, month, issueY, issueM);
            if (packet == null) {
                LOG.debug(String.format("Archie packet is null. See %s folder", ArchieDownloader.TEMP_FOLDER));

            } else {
                cm.newPackageReceived(packet.toURI());
            }
        }
        return packet;
    }

    public static File downloadArchiePackage(int year, int month, int issueY, int issueM) throws Exception {
        Publishing pub = null;
        byte[] ret;
        try {
            pub = WebServiceUtils.getPublishing();
            ret = pub.getContentForPublication(CochraneCMSPropertyNames.getArchieDownloadPassword());

        } catch (Exception e) {
            if (SSLChecker.checkCertificate(CochraneCMSPropertyNames.getArchieDownloadService())) {
                pub = WebServiceUtils.getPublishing();
                ret = pub.getContentForPublication(CochraneCMSPropertyNames.getArchieDownloadPassword());

            }  else {
                throw e;
            }
        } finally {
            WebServiceUtils.releaseServiceProxy(pub, Publishing.class);
        }

        boolean[] packageTypes = {false, false}; // {jats, aries}
        try {
            checkPackageOnDownload(ret, packageTypes);

        } catch (CmsException ce) {
            return null;
        }

        File packet = RepositoryUtils.createFile(ArchieDownloader.INPUT_FOLDER + CmsUtils.getIssueNumber(year, month)
                + FilePathCreator.SEPARATOR + buildAutoPackageName(issueY, issueM, packageTypes));
        OutputStream outputStream = new FileOutputStream(packet);
        outputStream.write(ret);
        outputStream.close();

        return packet;
    }

    static boolean checkPackageOnDownload(byte[] packet, boolean[] packageTypes) throws Exception {
        checkPackage(packet, ON_GET, packageTypes);
        return packageTypes[0];  // JATS
    }

    private static void checkPackage(byte[] packet, MessageSender.Help callback, boolean[] packageTypes)
                                    throws Exception {
        if (packet == null) {
            throw new Exception(ERROR_INVALID_ZIP);
        }
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packet));
        ZipEntry ze;
        String err = null;
        boolean success = false;
        boolean jats = false;
        boolean aries = false;
        boolean empty = true;
        try {
            while ((ze = zis.getNextEntry()) != null) {
                String name = ze.getName();

                if (name.equalsIgnoreCase(OUTPUT_LOG)) {
                    continue;
                }

                if (name.equalsIgnoreCase(ERROR_LOG)) {
                    err = InputUtils.readStreamToString(zis);
                    break;

                } else if (name.equalsIgnoreCase(SUCCESS_LOG)) {
                    success = true;
                    if (jats || aries) {
                        break;
                    }

                } else {
                    if (name.endsWith(Extensions.DAR)) {
                        jats = true;
                        packageTypes[0] = true;

                    } else if (name.endsWith(Extensions.ZIP)) {
                        aries = true;
                        packageTypes[1] = true;
                    }
                    empty = false;
                    if (success && (jats || aries)) {
                        break;
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(zis);
        }
        handleErr(err, success, empty, callback);
    }

    private static void handleErr(String err, boolean success, boolean empty, MessageSender.Help callback)
            throws Exception {
        String message = null;
        if (err != null) {
            message = ArchiePackage.buildArchieResponseMsg(ArchiePackage.ERROR_LOG + ". Details:\n"
                    + (err.length() > 0 ? err : "<empty>"));

        } else if (!success) {
            message = "Downloaded packet contains no success.log file";

        } else if (empty) {
            message = "Downloaded packet contains no workable entries";
            LOG.info(message);
            throw new CmsException(message);

        }
        if (message != null) {
            LOG.error(message);
            if (callback != null) {
                callback.sendMessage(message);
                throw new CmsException(message);

            } else {
                throw new Exception(message);
            }
        }
    }

    public static boolean canAut(String packageName) {
        return isArchieAut(packageName) && (CochraneCMSPropertyNames.canArchieAut() || isAutReprocess(packageName));
    }

    @Override
    public boolean isAut() {
        return reb != null;
    }

    @Override
    protected boolean forSPD() {
        return isAut() && reb.sPD().is();
    }

    protected void setAut(int fullIssueNumber) {
        if (canAut(packageFileName)) {
            reb = new ArchieResponseBuilder(false, false, packageFileName.replace(FilePathCreator.ZIP_EXT, "0"),
                    packageId);
            vendor = PackageChecker.ARCHIE;
            reb.sPD(CmsUtils.isScheduledIssueNumber(fullIssueNumber), false);
        }
        libName = CochraneCMSPropertyNames.getCDSRDbName();
    }

    @Override
    public void parsePackageName() throws DeliveryPackageException {
        int year;
        int issueNumber;
        try {
            if (packageFileName.startsWith(IssueDate.getTranslationPackagePrefix())) {
                IssueDate iDate = new IssueDate(CmsUtils.getCochraneDownloaderDateTime());
                year = iDate.year;
                issueNumber = Integer.parseInt(String.format(Constants.ISSUE_NUMBER_FORMAT, iDate.issueMonth));
            } else {
                String[] parts = packageFileName.split("[-_]");
                year = Integer.parseInt(parts[1]);
                issueNumber = Integer.parseInt(parts[2]);
            }
            fullIssueNumber = CmsUtils.getIssueNumber(year, issueNumber);
            vendor = parseVendor();

        } catch (Throwable e) {
            LOG.error(e.getMessage());
            throw new DeliveryPackageException("a package name must start with: crgs-issueYear-issueNumber-...",
                    IDeliveryFileStatus.STATUS_BAD_FILE_NAME);
        }
        setAut(fullIssueNumber);
    }

    private static StringBuilder buildAutoPackagePrefix(int year, int number) {
        return new StringBuilder(PACKAGE_NAME_SIZE).append(PackageChecker.CDSR_PREFIX).append("-")
                       .append(year).append("-").append(String.format(Constants.ISSUE_NUMBER_FORMAT, number))
                       .append(PackageChecker.AUT_SUFFIX);
    }

    public static String buildAutoPackageName(int year, int number, boolean[] packageTypes) {
        long time = System.currentTimeMillis();
        StringBuilder ret = buildAutoPackagePrefix(year, number).append(
                String.format("_%tF-%TH-%TM-%TS", time, time, time, time));
        if (packageTypes[0])  {
            ret.append(PackageChecker.JATS_POSTFIX);
        } else if (packageTypes[1]) {
            ret.append(PackageChecker.ARCHIE_ARIES_POSTFIX);
            ret.append(PackageChecker.JATS_POSTFIX);
        }
        return ret.append(Extensions.ZIP).toString();
    }

    public static String buildPackageName(int year, int number, String postfix, String ext) {
        return PackageChecker.buildCRGSPrefix(year, CmsUtils.getIssueMonth(number)) + postfix + ext;
    }

    static String buildArchieResponseMsg(String message) {
        return "the Archie service responded with " + message;
    }

    static String buildArchieCallMsg(String message) {
        return "a call to Archie service failed with " + message;
    }

    static void sendCriticalErrorOnDownload(String cause) {
        MessageSender.Help sender = createNotificationMessageCallback(
                MessageSender.MSG_TITLE_WR_DOWNLOAD_FAIL, OPERATION_DOWNLOAD, OPERATION_DOWNLOAD);
        StringBuilder msg = buildErrCause(OPERATION_DOWNLOAD, cause, null);
        sender.sendMessage(msg.toString());
    }

    public static void sendCriticalError(String cause, Exception ex, String affectedContentStr,
                                  ArchieResponseBuilder affectedContent, String opName) {
        MessageSender.Help sender = createNotificationMessageCallback(
                MessageSender.MSG_TITLE_WR_SEND_NOTIFICATION_FAIL, opName, opName);
        String out = getAffectedContent(affectedContentStr, affectedContent);
        StringBuilder msg = buildErrCause(opName, cause, ex);
        msg.append(AS_A_RESULT_NOT_APPROVED).append(out);
        sender.sendMessage(msg.toString());
        if (affectedContent != null) {
            affectedContent.setErrorMessage(msg.toString());
        }
    }

    public static void sendCochraneCriticalError(PublishedAbstractEntity pae,
                                                 ArchieResponseBuilder reb, String opName) {
        MessageSender.Help sender = createNotificationMessageCallback(
                MessageSender.MSG_TITLE_WR_SEND_NOTIFICATION_FAIL, opName, opName);
        String out = reb.getCochrnaneNotification();
        String errorMsg = reb.getErrorMessage();
        LOG.error(errorMsg);
        String msg = errorMsg + (StringUtils.isNotBlank(out) ? AS_A_RESULT_NOT_APPROVED + out : "");
        sender.sendMessage(reb.getPackageName(), pae, msg);
    }

    static void printNotification4Debug(String out, String prefix, boolean err) {
        if (CochraneCMSPropertyNames.isArchieDownloadDebugMode()) {
            if (err) {
                LOG_AN.error(String.format("%s:\n%s\n[ArchieNotificationResponse]: failure", prefix, out));
            } else {
                LOG_AN.info(String.format("%s:\n%s\n[ArchieNotificationResponse]: success", prefix, out));
            }
        }
    }

    @Override
    protected boolean parseZip(Integer issueId, ZipInputStream zis, DeliveryPackageInfo packageInfo,
                               String realDirToZipStore) throws DeliveryPackageException {
        boolean all = true;
        ZipEntry ze;
        ZipEntry zeLast = null;
        int limit = CochraneCMSPropertyNames.getArchieDownloaderLimit();
        try {
            while ((ze = zis.getNextEntry()) != null) {
                zeLast = ze;
                if (ze.isDirectory() || !parseZipEntry(issueId, zis, packageInfo, ze)) {
                    continue;
                }
                if (isAut() && limit > 0 && getArticleCount() == limit) {
                    LOG.info(String.format(
                        "%d CDSR articles were picked up while a total limit is %d - next parsing is suspended",
                            getArticleCount(), limit));
                    all = false;
                    break;
                }
            }
            checkFirstZipEntry(packageFileName, zeLast);
            putPackageToRepository(realDirToZipStore);

        } catch (Throwable tr) {
            String msg = "Package parsing: " + tr.getMessage();
            if (flLogger != null) {
                flLogger.onPackageError(ILogEvent.PRODUCT_UNPACKED, getPackageId(), packageFileName, getArticleNames(),
                        msg, isAut(), forSPD());
            } else {
                recordCache.removeRecords(getArticleNames(), forSPD());
            }
            throw new DeliveryPackageException(msg, IDeliveryFileStatus.STATUS_CORRUPT_ZIP);

        } finally {
            IOUtils.closeQuietly(zis);
        }
        return all;
    }

    private static void downloadCochranePackageFromTestFolder(int issueY, int issueM, File dir, IContentManager cm) {
        File[] packages = dir.listFiles(RepositoryUtils.ZIP_FF);
        if (packages == null) {
            return;
        }
        String prefix = buildAutoPackagePrefix(issueY, issueM).toString();
        List<File> archiePackages = Arrays.stream(packages)
                .filter(p -> p.getName().startsWith(prefix))
                .collect(Collectors.toList());

        if (archiePackages.isEmpty()) {
            LOG.debug(String.format("No Archie packages found. See %s folder. Archie test package file name should "
                    + "start with %s", ArchieDownloader.TEMP_FOLDER, prefix));
        } else {
            sendNewPackageReceived(cm, archiePackages);
        }
    }

    public static synchronized void sendNewPackageReceived(IContentManager cm, List<File> archiePackages) {
        archiePackages.forEach(p -> cm.newPackageReceived(p.toURI()));
    }

    private static StringBuilder buildErrCause(String opName, String cause, Throwable ex) {
        StringBuilder msg = new StringBuilder().append("A call of  ").append(opName);
        if (ex != null) {
            msg.append(" was failed with an uncontrolled error: ").append(cause).append(".").append(
                "\n\nThe next attempt to call it was also unsuccessful because of ").append(ex.getMessage());
        } else {
            msg.append(" was failed because of ").append(cause).append(".");
        }
        return msg;
    }

    private static String getAffectedContent(String out, ArchieResponseBuilder rb) {
        XMLOutputter xout = new XMLOutputter(Format.getPrettyFormat());
        return rb != null ? xout.outputString(rb.getResponse()) : out;
    }

    private static MessageSender.Help createNotificationMessageCallback(String title, final String service,
                                                                        final String operation) {
        return new MessageSender.Help(title) {
            @Override
            public void sendMessage(String... params) {
                super.sendMessage(
                        MessageSender.MSG_PARAM_SERVICE, service,
                        MessageSender.MSG_PARAM_LINK, WebServiceUtils.getLinkByServiceName(
                                WebServiceUtils.PUBLISHING_SERVICE, operation),
                        MessageSender.MSG_PARAM_OPERATION, operation,
                        MessageSender.MSG_PARAM_REPORT, params[0]);
            }

            @Override
            public void sendMessage(String packageName, PublishedAbstractEntity pae, String... params) {
                super.sendMessage(packageName, pae, MessageSender.MSG_PARAM_SERVICE, service,
                        MessageSender.MSG_PARAM_LINK, WebServiceUtils.getLinkByServiceName(
                                WebServiceUtils.PUBLISHING_SERVICE, operation),
                        MessageSender.MSG_PARAM_OPERATION, operation,
                        MessageSender.MSG_PARAM_REPORT, params[0]);
            }
        };
    }
}
