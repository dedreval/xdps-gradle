package com.wiley.cms.cochrane.cmanager.publish.send.cochrane;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSender;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.ftp.FtpInteraction;
import com.wiley.tes.util.ftp.SftpConnection;

public class CochraneSender extends AbstractSender {

    public static final String RETRY_FOLDER = "tmp/notifications/retry-queue/";
    private static final String CLSYSREV = "clsysrev";
    private static final Logger LOG = Logger.getLogger(CochraneSender.class);
    private static final int QUICK_REPEAT_COUNT = 3;
    private static final int QUICK_REPEAT_DELAY = 3 * 60 * 1000;

    public static CochraneSender createInstance(ClDbVO db) {
        return new CochraneSender(db, "COCHRANE:PUBLISH:", PubType.TYPE_COCHRANE_P);
    }

    public static CochraneSender createEntireInstance() {
        EntireDbWrapper dbWrapper = new EntireDbWrapper(CochraneCMSPropertyNames.getCDSRDbName());
        IResultsStorage rs = ResultStorageFactory.getFactory().getInstance();
        ClDbVO dbVo = new ClDbVO(rs.getDb(dbWrapper.getDbId()));

        return new CochraneSender(dbVo, "COCHRANE:PUBLISH:", PubType.TYPE_COCHRANE_P);
    }

    protected CochraneSender(ClDbVO db, String sendName, String exportTypeName) {
        super(db, sendName, exportTypeName);
    }

    public boolean sendBySftp(String localPath) {
        initProfileProperties();

        String packageName = CmsUtils.getPackageNameFromPath(localPath);
        String serverPath = getServerPath(packageName);
        ServerType server = getServer();

        RepeatableOperation rc = new RepeatableOperation(QUICK_REPEAT_COUNT) {
            @Override
            protected void perform() throws Exception {
                sendBySftp(localPath, serverPath, server.getHost(), server.getPort(), server.getUser(),
                        server.getPassword(), server.getTimeout());
            }

            @Override
            protected int getDelay() {
                return QUICK_REPEAT_DELAY;
            }
        };

        boolean success = false;
        try {
            success = rc.performOperation();
            if (success) {
                MessageSender.sendReport("cochrane_publish", this.getDb().getTitle(),
                        buildBodyNotification(server, packageName));
            } else {
                logSendError(packageName);
            }
        } catch (Exception e) {
            logSendError(packageName);
        }
        return success;
    }

    public static void sendBySftp(String localPath, String serverPath, String host, int port, String login,
                                  String password, int timeout) throws Exception {
        if (CochraneCMSPropertyNames.isArchieDownloadTestMode()) {
            LOG.debug(String.format("%s -> %s. (TEST MODE)", localPath, serverPath));
            return;
        }
        LOG.debug(String.format("%s -> %s", localPath, serverPath));
        FtpInteraction sftp = null;
        try {
            sftp = new SftpConnection();
            sftp.connect(host, port, login, password, timeout);
            sftp.putFile(serverPath, localPath);

        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }

    public static void saveNotificationForResend(PublishedAbstractEntity pae, String localPath) {
        boolean send = CmsUtils.saveSFTPNotification(localPath, RETRY_FOLDER);
        if (!send) {
            String errorMessage = buildMoveNotificationFileError(localPath);
            MessageSender.sendErrorMovingNotificationFile(pae, errorMessage);
        }
    }

    public boolean sendUndeliveredNotificationsBySftp(String localPath) {
        initProfileProperties();

        String packageName = CmsUtils.getPackageNameFromPath(localPath);
        String serverPath = getServerPath(packageName);
        ServerType server = getServer();

        boolean success = false;
        try {
            sendBySftp(localPath, serverPath, server.getHost(), server.getPort(), server.getUser(),
                    server.getPassword(), server.getTimeout());
            MessageSender.sendReport("cochrane_publish", this.getDb().getTitle(),
                    buildBodyNotification(server, packageName));
            success = CmsUtils.deleteFileByPath(localPath);
            if (!success) {
                logDeleteError(packageName);
            }
        } catch (Exception e) {
            String resendError = buildResendError(packageName) + ". Cause: " + e;
            LOG.error(resendError);
            MessageSender.sendCochraneCriticalError(resendError, packageName);
        }
        return success;
    }

    @Override
    protected void send() throws Exception {
    }

    private void initProfileProperties() {
        PublishProfile.PubLocationPath path = PublishProfile.getProfile().get().getPubLocation(
                PubType.TYPE_COCHRANE_P, CLSYSREV, false, true);

        initProfileProperties(path);
    }

    private String getServerPath(String packageName) {
        String serverPathToFolder = getServerPath();
        return serverPathToFolder + FilePathCreator.SEPARATOR + packageName;
    }

    private String buildBodyNotification(ServerType server, String packageName) {
        IssueVO issue = this.getDb().getIssue();
        String header;
        if (issue != null) {
            header = String.format("issue: %s %s; Database: %s\n\n", issue.getNumber(), issue.getYear(), this.getDb().getTitle());
        } else {
            header = String.format("Database: %s\n\n", this.getDb().getTitle());
        }
        String content = String.format("The %s has been deposited in %s\n" +
                "Cochrane endpoint: %s;", packageName, getServerPath(), server.getHost());
        String body = header + content;
        body = buildTestCommentStr(body);
        return body;
    }

    private String buildTestCommentStr(String body) {
        if (CochraneCMSPropertyNames.isArchieDownloadTestMode()) {
            return String.format("%s  [TEST MODE]\n\nIt's running in test mode. No packages actually deposited!", body);
        }
        return body;
    }

    private static String buildMoveNotificationFileError(String localPath) {
        return String.format("Failed to move notification file from %s to %s", localPath, RETRY_FOLDER);
    }

    private String buildResendError(String packageName) {
        return String.format("Failed to resend undelivered notification %s from "
                + "DLQ to SFTP. Operation: %s", packageName, PubType.TYPE_COCHRANE_P);
    }

    private void logSendError(String packageName) {
        LOG.error(String.format("After %s attempts, failed to send the file %s via sftp. Operation: %s",
                QUICK_REPEAT_COUNT, packageName, PubType.TYPE_COCHRANE_P));
    }

    private void logDeleteError(String packageName) {
        LOG.error(String.format("Failed to delete undelivered notification %s from "
                + "DLQ. Operation: %s", packageName, PubType.TYPE_COCHRANE_P));
    }
}
