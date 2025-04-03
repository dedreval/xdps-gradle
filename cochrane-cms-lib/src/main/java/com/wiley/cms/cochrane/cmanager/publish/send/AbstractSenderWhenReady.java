package com.wiley.cms.cochrane.cmanager.publish.send;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.AbstractPublisher;
import com.wiley.cms.cochrane.cmanager.publish.IPublish;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.PublishStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorWhenReady;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.ftp.FTPConnection;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.wiley.cms.cochrane.cmanager.MessageSender.MSG_PARAM_RECORD_ID;
import static com.wiley.cms.cochrane.cmanager.MessageSender.getCDnumbersFromMessageByPattern;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 12.12.11
 */
public abstract class AbstractSenderWhenReady implements IPublish {
    public static final String SENDING_SUCCESSFUL_MESSAGE_KEY = "sending.when_ready_db_name";
    public static final String SENDING_FAILED_MESSAGE_KEY = "sending.when_ready_db_name_failed";
    public static final String SENDING_SUCCESSFUL_NOTIFICATION_KEY = "sending_successful";
    public static final String SENDING_FAILED_NOTIFICATION_KEY = "sending_failed";

    private static final Logger LOG = Logger.getLogger(AbstractSenderWhenReady.class);
    private static final String FINISH_SEND = "< Finish Send [";

    protected final IRepository rps;
    protected PublishWrapper publish;
    private final IResultsStorage rs;
    private final DbWrapper db;
    private final boolean fromEntire;
    private final String exportTypeName;
    private final String sendName;
    private String packagePath;
    private String packageFolder;
    private String serverName;
    private String serverLogin;
    private String serverPassword;
    private String serverPath;
    private String sendingErr;
    private int serverPort;
    private int timeout;
    private boolean localHost;

    protected AbstractSenderWhenReady(ClDbVO dbVO, String exportTypeName) {
        this(dbVO.getId(), dbVO.getIssue().getId() == DbEntity.NOT_EXIST_ID, exportTypeName);
    }

    protected AbstractSenderWhenReady(EntireDbWrapper db, String exportTypeName) {
        this(
                ResultStorageFactory.getFactory().getInstance().getDatabaseEntity(db.getDbName()).getId(),
                true,
                exportTypeName);
    }

    protected AbstractSenderWhenReady(int dbId, boolean fromEntire, String exportTypeName) {
        this.rps = RepositoryFactory.getRepository();
        this.rs = ResultStorageFactory.getFactory().getInstance();
        this.db = new DbWrapper(dbId);
        this.fromEntire = fromEntire;
        this.exportTypeName = exportTypeName;
        this.sendName = this.exportTypeName.toUpperCase() + ":WHENREADY:cca";
    }

    protected void init(PublishWrapper publish) {

        this.publish = publish;
       
        packageFolder = definePackageDir(db.getTitle(), fromEntire ? DbEntity.NOT_EXIST_ID
                : db.getIssue().getId(), exportTypeName);
        packagePath = packageFolder + publish.getFileName();

        AbstractPublisher.checkExportEntity(publish, db.getId(), packageFolder, false, rs,
                PublishStorageFactory.getFactory().getInstance(), CochraneCMSBeans.getFlowLogger());

        PublishProfile.PubLocationPath locPath = PublishProfile.getProfile().get().getPubLocation(
                exportTypeName, getDbName(), false, false);

        serverPath = locPath.getFolder();
        ServerType server = locPath.getServerType();
        serverName = server.getHost();
        serverPassword = server.getPassword();
        serverLogin = server.getUser();
        serverPort = server.getPort();
        localHost = server.isLocalHost();
        timeout = server.getTimeout();
    }

    protected boolean isLocalHost() {
        return localHost;
    }

    private static String definePackageDir(String dbName, int issueId, String exportTypeName) {
        return FilePathCreator.getDirPathForPublish(dbName, issueId, exportTypeName, false);
    }

    public String getExportTypeName() {
        return exportTypeName;
    }

    protected String getPackageFolder() {
        return packageFolder;
    }

    private void logSendingSuccessful() {
        Map<String, String> additional = new HashMap<>();
        additional.put(MessageSender.MSG_PARAM_RECORD_NAMES, getSentRecords(publish, getPackagePath(), rps));

        Map<String, String> map = new HashMap<>();
        map.put(MessageSender.MSG_PARAM_REPORT, getIssueAndDb(getSuccessfulMessageKey(), additional));
        map.put(MessageSender.MSG_PARAM_DATABASE, getDbName());

        MessageSender.sendMessage(getSuccessfulNotificationKey(), map);
    }

    private void logSendingFailed(String message) {
        Map<String, String> additional = new HashMap<>();
        additional.put(MessageSender.MSG_PARAM_RECORD_NAMES, getSentRecords(publish, getPackagePath(), rps));

        Map<String, String> map = new HashMap<>();
        map.put("issue_and_db_name", getIssueAndDb(getFailedMessageKey(), additional));
        map.put("details", message);
        String report = CochraneCMSProperties.getProperty("sending.failed", map);

        map.clear();
        map.put(MessageSender.MSG_PARAM_REPORT, report);
        map.put(MessageSender.MSG_PARAM_DATABASE, getDbName());

        String identifiers = getCDnumbersFromMessageByPattern(report, map);
        map.put(MSG_PARAM_RECORD_ID, identifiers);

        MessageSender.sendMessage(getFailedNotificationKey(), map);
    }

    public static String getSentRecords(PublishWrapper publish, String packagePath, IRepository rps) {
        String archiveDir = new File(packagePath).getParent();
        String recordsListFile = AbstractGeneratorWhenReady.getRecordListFileName(publish.getFileName());
        String recordNames;
        try (InputStream is = new FileInputStream(new File(rps.getRealFilePath(archiveDir), recordsListFile))) {
            recordNames = IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            recordNames = String.format("N/A. Unable to get records from %s. %s", publish.getFileName(), e);
        }
        return recordNames;
    }

    private String getIssueAndDb(String message, Map<String, String> additional) {
        Map<String, String> map = new HashMap<>();
        map.put("db_name", getDbName());
        map.put("name", getPackageFileName());
        map.put("server", getServerName());
        map.put("place", getServerPath());
        if (additional != null) {
            map.putAll(additional);
        }
        return CochraneCMSProperties.getProperty(message, map);
    }

    protected abstract void doSend() throws Exception;

    protected void sendByFtp(String fileName, File sent) throws Exception {
        FTPClient ftp = null;
        InputStream is = null;
        try {
            ftp = FTPConnection.connectByFTPClient(getServerName(), getServerLogin(), getServerPassword(),
                    getServerPort(), timeout);
            boolean ret = ftp.changeWorkingDirectory(getServerPath());
            if (!ret) {
                throw new Exception(FTPConnection.getServerReplyStr(ftp));
            }
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            is = new BufferedInputStream(new FileInputStream(sent));
            ret = ftp.storeFile(fileName, is);
            if (!ret) {
                throw new Exception(FTPConnection.getServerReplyStr(ftp));
            }
            LOG.debug(String.format("%s has been successfully sent to: %s:%s", fileName, getServerName(),
                    getServerPath()));
        } finally {
            IOUtils.closeQuietly(is);
            try {
                if (ftp != null) {
                    ftp.logout();
                    ftp.disconnect();
                }
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }
        }
    }

    @Override
    public void start(PublishWrapper publish, PublishOperation operation) {
        LOG.debug("> Start Send [" + sendName + "]");
        try {
            init(publish);
            operation.beforeOperation(publish.getId(), rs);
            if (isPackageExist()) {
                doSend();
            } else {
                throw new Exception("Sending has been stopped: package is generating or was not generated");
            }
            LOG.debug("SENT > " + publish.getFileName());
            operation.afterOperation(publish, publish.getId(), rs);
            LOG.debug(FINISH_SEND + sendName + "]");
            logSendingSuccessful();
            PublishEntity export = publish.getPublishEntity();
            BaseType bt = BaseType.find(export.getDb().getTitle()).get();
            ArchiveSender.addFlowLogging(bt, export, publish.getTransactionId(), null,
                    CochraneCMSBeans.getPublishStorage(), CochraneCMSBeans.getFlowLogger());

        } catch (Exception e) {
            PublishEntity export = publish.getPublishEntity();
            operation.onFail(publish, publish.getId(), rs);
            sendingErr = e.getMessage();
            if (export != null) {
                BaseType bt = BaseType.find(export.getDb().getTitle()).get();
                ArchiveSender.addFlowLoggingOnError(bt, export, publish.getMajorType(), publish.getTransactionId(),
                        sendingErr, CochraneCMSBeans.getPublishStorage(), CochraneCMSBeans.getFlowLogger());
            }

            LOG.error(FINISH_SEND + sendName + "] Failed with Exception: " + sendingErr);
            logSendingFailed(sendingErr == null ? "NullPointerException" : sendingErr);
        }
        publish.setPublishEntity(rs.findPublish(publish.getId()));
    }

    private boolean isPackageExist() {
        PublishEntity publishEntity = publish.getPublishEntity();
        return !publishEntity.isGenerating() && publishEntity.isGenerated();
    }

    protected String getSuccessfulMessageKey() {
        return SENDING_SUCCESSFUL_MESSAGE_KEY;
    }

    protected String getFailedMessageKey() {
        return SENDING_FAILED_MESSAGE_KEY;
    }

    protected String getSuccessfulNotificationKey() {
        return SENDING_SUCCESSFUL_NOTIFICATION_KEY;
    }

    protected String getFailedNotificationKey() {
        return SENDING_FAILED_NOTIFICATION_KEY;
    }

    protected String getPackageFileName() {
        return publish.getFileName();
    }

    protected String getDbName() {
        return db.getTitle();
    }

    protected String getPackagePath() {
        return packagePath;
    }

    protected String getServerPath() {
        if (serverPath != null && serverPath.length() > 0) {
            return (serverPath.lastIndexOf("/") != -1) ? serverPath : serverPath + "/";
        } else {
            return "./";
        }
    }

    protected String getServerName() {
        return serverName;
    }

    protected String getServerLogin() {
        return serverLogin;
    }

    protected String getServerPassword() {
        return serverPassword;
    }

    protected int getServerPort() {
        return serverPort;
    }

    public String getSendingError() {
        return sendingErr;
    }
}
