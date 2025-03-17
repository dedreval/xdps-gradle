package com.wiley.cms.cochrane.cmanager.publish.send;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWClient;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.tes.util.KibanaUtil;
import com.wiley.tes.util.ftp.FTPConnection;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.AbstractPublisher;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.util.SSHOperations;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/22/2018
 */
public abstract class ArchiveSender extends AbstractPublisher {
    static final Logger LOG = Logger.getLogger(ArchiveSender.class);

    static final String TEMPLATE_SENDING_PACKAGES = "sending.packages";

    protected String packagePath;
    protected PublishEntity export;
    protected ServerType server;
    protected boolean sendTestMode;

    private String serverPath;
    private boolean localHost;

    protected ArchiveSender(String dbName, String sendName, String exportTypeName) {
        super(buildTag(dbName, sendName, exportTypeName), exportTypeName);
    }

    protected abstract void send() throws Exception;

    protected final void send(PublishWrapper publish) throws Exception {
        if (publish.hasPublishToAwait()) {
            ps.createPublishWait(publish.getId(), publish.getPublishToAwait().isStaticContentDisabled());
        }
        send();
    }

    protected void init(PublishWrapper publish) throws Exception {
    }

    protected void initProfileProperties(PublishProfile.PubLocationPath path) {

        server = path.getServerType();

        serverPath = path.getFolder();
        if (serverPath != null && !serverPath.isEmpty()) {
            serverPath = (serverPath.lastIndexOf("/") != -1) ? serverPath : serverPath + "/";
        } else {
            serverPath = "./";
        }

        localHost = server.isLocalHost();
    }

    protected final ServerType getServer() {
        return server;
    }

    protected String getServerPath() {
        return serverPath;
    }

    protected boolean isLocalHost() {
        return localHost;
    }

    public String getServerName() {
        return server.getHost();
    }

    public int getServerPort() {
        return server.getPort();
    }

    public String getServerLogin() {
        return server.getUser();
    }

    public String getServerPassword() {
        return server.getPassword();
    }

    public int getServerTimeout() {
        return server.getTimeout();
    }

    public String getDestination() {
        return getServerName();
    }

    public String getDestinationPlace() {
        return ":" + getServerPath();
    }

    void setExport(PublishWrapper publish, PublishEntity pe) {
        export = pe;
        publish.setPublishEntity(pe);
    }

    protected void createTarGz(String path) throws Exception {
        Date date = new Date();
        String packageName = StringUtils.substringBeforeLast(path, "/") + "/" + getDbName() + "_"
            + Now.SHORT_DATE_FORMATTER.format(date) + "_" + date.getTime() + Extensions.TAR;
        SSHOperations.createTarGz(path, packageName, getServerName(), getServerLogin(), getServerPassword());
    }

    protected final String buildCommentStr(String comment) {
        if (sendTestMode) {
            return String.format("%s \n\nIt's running in test mode. No packages actually deposited!", comment);
        }
        return comment;
    }

    public int getExportId() {
        return export.getId();
    }

    public final String getPackageFileName() {
        return export.getFileName();
    }

    public final String getPackagePath() {
        return getPackageFolder() + getPackageFileName();
    }

    public final String getPackageFolder() {
        return packagePath;
    }

    public HWClient getHWClient() {
        return CochraneCMSBeans.getHWClient();
    }

    protected void sendBySSH(String filePath, String fileName) throws Exception {
        sendBySSH(filePath, fileName, false);
    }

    protected void sendBySSH(String filePath, String fileName, boolean touch) throws Exception {
        if (isLocalHost()) {
            return;
        }
        String localPath = rps.getRealFilePath(filePath);
        String remotePath = getServerLogin() + "@" + getServerName() + ":" + fileName;
        SSHOperations.send(localPath, remotePath);

        if (touch) {
            touch(fileName + ".done");
        }
    }

    protected void sendByFtp(String fileName, File sent) throws Exception {
        FTPClient ftp = null;
        InputStream is = null;
        try {
            ftp = FTPConnection.connectByFTPClient(getServerName(), getServerLogin(), getServerPassword(),
                    getServerPort(), getServerTimeout());
            ftp.enterLocalPassiveMode();
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

    protected void touch(String fullFileName) throws Exception {
        SSHOperations.touch(fullFileName, getServerName(), getServerLogin(), getServerPassword());
    }

    protected void deleteFolders(String[] names, String path) throws Exception {
        SSHOperations.deleteFolders(names, path, getServerName(), getServerLogin(), getServerPassword());
    }

    protected void mkdir(String path) throws Exception {
        SSHOperations.mkdir(path, getServerName(), getServerLogin(), getServerPassword());
    }

    protected void unpackPackage(String path) throws Exception {
        SSHOperations.unpackPackage(path, getPackageFileName(), getServerPath(), getServerName(), getServerLogin(),
            getServerPassword());
    }

    protected String[] listFiles(String path) throws Exception {
        return SSHOperations.listFiles(path, getServerName(), getServerLogin(), getServerPassword());
    }

    protected String send(PublishWrapper publish, PublishOperation op, Boolean relatedFiles) {
        try {
            String msg = op.getNotReady(export);
            if (msg != null) {
                op.onFail(publish, export.getId(), rs);
                return msg;
            }
            replicate(publish, relatedFiles);
            send(publish);
            LOG.debug("SENT > " + export.getFileName());
            op.afterOperation(publish, export.getId(), rs);
            logSendingSuccessful(publish, relatedFiles);
            BaseType bt = BaseType.find(export.getDb().getTitle()).get();
            if (bt.isCentral() && !export.getDb().isEntire()) {
                addCentralFlowLogging(bt, export.getPublishType(), publish.getDeliveryFileId(),
                        publish.getTransactionId());

            } else {
                addFlowLogging(bt, export, publish.getTransactionId(), null, ps, flowLogger);
            }
        } catch (Exception e) {
            handleError(publish, op, e);
        }

        return null;
    }

    public static void addFlowLoggingOnError(BaseType bt, PublishEntity pe, String majorType, String transactionId,
                                             String errMsg, IPublishStorage ps, IFlowLogger fl) {
        addFlowLogging(bt, pe, transactionId, buildDashboardErrorMessage(majorType, errMsg), ps, fl);
    }

    public static void addFlowLogging(BaseType bt, PublishEntity pe, String transactionId, String errMsg,
                                      IPublishStorage ps, IFlowLogger fl) {
        if (KibanaUtil.addKibanaLogging(bt, pe.getPublishType())) {
            List<PublishRecordEntity> list = ps.getPublishRecordsForFlow(bt, Collections.singletonList(pe.getId()));
            list.forEach(r -> fl.onProductsPublishingSent(pe.getFileName(), pe.getPublishType(),
                r.getDeliveryId(), RecordHelper.buildCdNumber(r.getNumber()), transactionId, errMsg,
                    CmsUtils.isScheduledDb(pe.getDb().getId())));
        }
    }

    static String buildDashboardErrorMessage(String majorType, String errMsg) {
        return String.format("'%s' happened during sending for %s", errMsg, majorType);
    }

    private void addCentralFlowLogging(BaseType bt, Integer publishTypeId, Integer dfId, String transactionId) {
        if (PubType.LITERATUM_DB_TYPES.contains(publishTypeId)) {
            addCentralFlowLogging(ILogEvent.PRODUCT_SENT_WOLLIT, bt, PubType.LITERATUM_DB_TYPES, transactionId);
        } else if (PubType.SEMANTICO_DB_TYPES.contains(publishTypeId)) {
            addCentralFlowLogging(ILogEvent.PRODUCT_SENT_HW, bt, PubType.SEMANTICO_DB_TYPES, transactionId);
        }  else if (PubType.SEMANTICO_DEL_TYPES.contains(publishTypeId)) {
            addCentralFlowLoggingOnWithdraw(ILogEvent.PRODUCT_SENT_HW_TO_WITHDRAW, bt, dfId, transactionId);
        } else if (PubType.DS_DB_TYPES.contains(publishTypeId)) {
            addCentralFlowLogging(ILogEvent.PRODUCT_SENT_DS, bt, PubType.DS_DB_TYPES, transactionId);
        }
    }

    private void addCentralFlowLoggingOnWithdraw(int eventId, BaseType bt, Integer dfId, String transactionId) {
        int count = rs.getDeletedRecordsCount(bt, dfId);
        flowLogger.onPackageFlowEvent(eventId, bt, null, dfId, PackageChecker.METAXIS, transactionId, count, count);
    }

    private void addCentralFlowLogging(int eventId, BaseType bt, Collection<Integer> publishTypeIds,
                                       String transactionId) {
        OpStats stats = rs.getPublishStatsOnSent(export.getId(), export.getDb().getId(), publishTypeIds);
        stats.getMultiCounters().forEach((dfId, st) -> flowLogger.onPackageFlowEvent(eventId, bt, export.getFileName(),
                dfId, PackageChecker.METAXIS, transactionId, st.getTotalCompleted(), st.getTotal()));
    }

    private void replicate(PublishWrapper publish, Boolean relatedFiles) {
        PublishProfile.PubLocationPath path = publish.getPath();
        PublishProfile.PubLocationPath replicatePath = path.getReplication();
        if (replicatePath == null) {
            return;
        }
        try {
            initProfileProperties(replicatePath);
            publish.setPubLocationPath(replicatePath);

            send(publish);
            LOG.debug("REPLICATE > " + export.getFileName());
            logSendingSuccessful(publish, relatedFiles);

        } catch (Throwable e) {
            logSendingFailed(publish, e.getMessage());

        } finally {
            initProfileProperties(path);
            publish.setPubLocationPath(path);
        }
    }

    String getMessageMap(String message, Map<String, String> map) {
        String packageFileName = getPackageFileName();
        map.put("name", packageFileName == null ? "" : getPackageFileName());
        map.put("server", getDestination());
        map.put("place", getDestinationPlace());
        map.put("comment", getComment());
        return CochraneCMSProperties.getProperty(message, map);
    }

    protected String getComment() {
        return "";
    }

    protected void logSendingSuccessful(PublishWrapper publish, Boolean relatedFiles) {
    }

    protected void logSendingFailed(PublishWrapper publish, String message) {
    }

    protected void handleError(PublishWrapper publish, PublishOperation op, Exception e) {
    }
}
