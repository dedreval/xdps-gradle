package com.wiley.cms.cochrane.cmanager.publish.send;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.util.SSHOperations;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.notification.SuspendNotificationSender;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.ftp.FtpConnectionWrapper;
import com.wiley.tes.util.ftp.FtpInteraction;
import com.wiley.tes.util.ftp.SftpConnection;

import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 03.11.11
 */
public abstract class AbstractSender extends ArchiveSender {
    protected static final Logger LOG = Logger.getLogger(AbstractSender.class);

    private static final String FINISH_SEND = "< Finish Send [";

    private int recordsProcessId;
    private final ClDbVO db;
    private int deliveryFileId;

    protected AbstractSender(ClDbVO db, String sendName, String exportTypeName) {
        super(db.getTitle(), sendName, exportTypeName);
        this.db = db;
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {

        super.init(publish);

        deliveryFileId = publish.getDeliveryFileId();
        recordsProcessId = publish.getRecordsProcessId();

        initPackagePath();

        export = checkExportEntity(publish, db.getId(), packagePath, false, rs, ps, flowLogger);

        initServerProperties(publish);
    }

    private void initPackagePath() {
        packagePath = FilePathCreator.getDirPathForPublish(db.getTitle(), db.getIssue().getId(), getExportTypeName(),
                !byIssue());
    }

    private void initServerProperties(PublishWrapper publish) {

        PublishProfile.PubLocationPath path = publish.getPath();
        if (path != null) {
            initProfileProperties(path);
        }
    }

    public void start(PublishWrapper publish, PublishOperation op) {
        LOG.debug("> Start Send [" + tag + "]");

        String notReadyMsg;
        try {
            init(publish);
            PublishEntity initialExport = publish.getPublishEntity();
            op.beforeOperation(initialExport.getId(), rs);
            boolean hasRelatedFiles = publish.hasRelatedPackages();
            boolean first = true;

            if (hasRelatedFiles) {
                List<PublishEntity> related = publish.getRelatedPackages();
                for (PublishEntity pe: related) {

                    setExport(publish, pe);
                    op.beforeOperation(pe.getId(), rs);
                    String msg = send(publish, op, first);
                    first = first && msg != null;
                }
                setExport(publish, initialExport);
            }

            notReadyMsg = send(publish, op, hasRelatedFiles ? first : null);

        } catch (Exception e) {
            handleError(publish, op, e);
            return;

        } finally {
            if (publish.byRecords()) {
                RecordWrapper.getProcessStorage().deleteProcess(publish.getRecordsProcessId());
            }
        }

        if (notReadyMsg != null) {

            LOG.error(FINISH_SEND + tag + "] " + notReadyMsg);
            logSendingFailed(publish, notReadyMsg);

        } else {
            LOG.debug(FINISH_SEND + tag + "]");
        }
    }

    @Override
    protected void handleError(PublishWrapper pw, PublishOperation op, Exception e) {
        BaseType bt = BaseType.find(getDbName()).get();
        op.onFail(pw, export.getId(), rs);

        if (bt.isCentral() && !export.getDb().isEntire()) {
            addCentralLoggingOnError(bt, export.getPublishType(), pw, e.getMessage());

        } else if (isWhenReady()) {
            boolean ds = PubType.TYPE_DS.equals(pw.getType());
            if (PubType.TYPE_LITERATUM.equals(pw.getType())) {
                rs.setDeliveryFileStatus(deliveryFileId, IDeliveryFileStatus.STATUS_PUBLISHING_FAILED, true);

            } else if (PubType.TYPE_SEMANTICO.equals(pw.getType())) {
                CochraneCMSBeans.getRecordManager().setRecordState(RecordEntity.STATE_WAIT_WR_PUBLISHED_NOTIFICATION,
                    RecordEntity.STATE_WR_ERROR, pw.getPublishEntity().getDb().getId(), pw.getCdNumbers());
                
            } else if (ds || PubType.TYPE_DS_MONTHLY.equals(pw.getType())) {
                CochraneCMSBeans.getRecordManager().setRecordState(RecordEntity.STATE_DS_PUBLISHED,
                    RecordEntity.STATE_DS_PUBLISHING, pw.getPublishEntity().getDb().getId(), pw.getCdNumbers());
            }
            addFlowLoggingOnError(bt, export, pw.getMajorType(), pw.getTransactionId(), e.getMessage(), ps, flowLogger);
        }
        //pw.setPublishEntity(rs.setSending(export.getId(), false, true));
        LOG.error(FINISH_SEND + tag + "] Failed with Exception: " + e.getMessage(), e);
        logSendingFailed(pw, e.getMessage() == null ? "Null" : e.getMessage());
    }

    @Override
    protected void logSendingFailed(PublishWrapper publish, String message) {

        Map<String, String> map = new HashMap<>();
        map.put("issue_and_db_name", getIssueAndDb("sending.issue_and_db_name_failed", null));
        map.put("details", message);
        String report = CochraneCMSProperties.getProperty("sending.failed", map);

        map.put(MessageSender.MSG_PARAM_DATABASE, CmsUtils.getOrDefault(getDbName()));
        String identifiers =  MessageSender.getCDnumbersFromMessageByPattern(message, map);
        map.put(MessageSender.MSG_PARAM_RECORD_ID, identifiers);
        MessageSender.sendReport(MessageSender.MSG_TITLE_SENDING_FAILED, db.getTitle(), report);
    }

    @Override
    protected void logSendingSuccessful(PublishWrapper publish, Boolean relatedFiles) {
        if (relatedFiles != null) {
            logSendingSuccessful(publish, TEMPLATE_SENDING_PACKAGES, getPackageFileName(), relatedFiles);
        } else {
            logSendingSuccessful(publish, "sending.issue_and_db_name", "sending.package", null);
        }
    }

    protected void logSendingSuccessful(PublishWrapper publish, String mainMsgPattern, String addMsgPattern,
                                        Boolean relatedFiles) {
        if (!publish.hasMessage()) {
            publish.addMessage(MessageSender.MSG_TITLE_SENDING_SUCCESSFUL);
            if (relatedFiles != null) {
                publish.addSubMessages(getIssueAndDb("sending.issue_and_db_name_packages", null), addMsgPattern);
            } else {
                publish.addMessage(getIssueAndDb(mainMsgPattern, null));
            }
            return;
        }

        if (relatedFiles != null) {
            if (relatedFiles) {
                publish.addSubMessages(getIssueAndDb(mainMsgPattern, null), addMsgPattern);
            } else {
                publish.addSubMessage(addMsgPattern);
            }
        } else {
            publish.addMessage(getMessageMap(addMsgPattern, new HashMap<>()));
        }
    }

    protected String getIssueAndDb(String message, Map<String, String> additional) {
        Map<String, String> map = new HashMap<>();
        boolean spd = CmsUtils.isScheduledIssue(db.getIssue().getId());
        map.put("issue_num",  CmsUtils.buildIssueNumber(db.getIssue(), spd));
        map.put("issue_year", CmsUtils.buildIssueYear(db.getIssue(), spd));
        map.put("db_name", db.getTitle());
        if (additional != null) {
            map.putAll(additional);
        }
        return getMessageMap(message, map);
    }

    protected void sendLocalBySSH(String filePath, String fileName) throws Exception {
        SSHOperations.send(rps.getRealFilePath(filePath), fileName);
    }

    public static void sendByOrdinalOrSecureFtp(String localPath,
                                                String serverPath,
                                                String host,
                                                int port,
                                                String login,
                                                String password,
                                                int timeout) throws Exception {
        URI hostUri = new URI(host);
        String hostname = StringUtils.isEmpty(hostUri.getHost()) ? host : hostUri.getHost();
        FtpInteraction ftpInteraction = StringUtils.equals(hostUri.getScheme(), "ftp")
                ? new FtpConnectionWrapper()
                : new SftpConnection();
        try {
            ftpInteraction.connect(hostname, port, login, password, timeout);
            ftpInteraction.putFile(serverPath, RepositoryFactory.getRepository().getRealFilePath(localPath));
        } finally {
            ftpInteraction.disconnect();
        }
    }

    public static void sendBySftp(String localPath, String serverPath, String host, int port, String login,
                                  String password, int timeout) throws Exception {
        String realLocalPath = getRealFilePath(localPath);
        LOG.debug(String.format("%s -> %s", realLocalPath, serverPath));
        FtpInteraction sftp = null;
        try {
            sftp = new SftpConnection();
            sftp.connect(host, port, login, password, timeout);
            sftp.putFile(serverPath, realLocalPath);

        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }

    private void addCentralLoggingOnError(BaseType bt, Integer publishTypeId, PublishWrapper pw, String errorMsg) {
        if (PubType.LITERATUM_DB_TYPES.contains(publishTypeId)) {
            addCentralLoggingOnError(ILogEvent.PRODUCT_SENT_WOLLIT, bt, pw, PubType.LITERATUM_DB_TYPES, false,
                    errorMsg);
        } else if (PubType.SEMANTICO_DB_TYPES.contains(publishTypeId)) {
            addCentralLoggingOnError(ILogEvent.PRODUCT_SENT_HW, bt, pw, PubType.SEMANTICO_DB_TYPES, false, errorMsg);
        } else if (PubType.SEMANTICO_DEL_TYPES.contains(publishTypeId)) {
            addCentralLoggingOnError(ILogEvent.PRODUCT_SENT_HW_TO_WITHDRAW, bt, pw, PubType.SEMANTICO_DEL_TYPES, true,
                    errorMsg);
        } else if (PubType.DS_DB_TYPES.contains(publishTypeId)) {
            addCentralLoggingOnError(ILogEvent.PRODUCT_SENT_DS, bt, pw, PubType.DS_DB_TYPES, false, errorMsg);
        }
    }

    private void addCentralLoggingOnError(int eventId, BaseType bt, PublishWrapper pw,
                                          Collection<Integer> publishTypeIds, boolean withdraw, String errorMsg) {
        String msg =  buildDashboardErrorMessage(pw.getMajorType(), errorMsg);
        if (withdraw) {
            int count = rs.getDeletedRecordsCount(bt, pw.getDeliveryFileId());
            if (count > 0) {
                flowLogger.onPackageFlowEventError(eventId, bt, null, pw.getDeliveryFileId(),
                        PackageChecker.METAXIS, pw.getTransactionId(), msg, count, count);
            }
            return;
        }
        OpStats stats = rs.getPublishStatsOnSent(export.getId(), export.getDb().getId(), publishTypeIds);
        if (stats.getTotal() > 0) {
            stats.getMultiCounters().forEach((dfId, st) -> flowLogger.onPackageFlowEventError(
                eventId, bt, export.getFileName(), dfId, PackageChecker.METAXIS, pw.getTransactionId(), msg,
                    st.getTotalCompleted(), st.getTotal()));
        }
    }

    private static void sendBySftp(String localPath, String serverPath, String host, int port, String login,
                                  int timeout, String identity) throws Exception {
        String realLocalPath = getRealFilePath(localPath);
        LOG.debug(String.format("%s -> %s by identity", realLocalPath, serverPath));
        FtpInteraction sftp = null;
        try {
            sftp = new SftpConnection();
            sftp.connect(host, port, login, timeout, identity);
            sftp.putFile(serverPath, realLocalPath);

        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }

    private static String getRealFilePath(String localPath) {
        String fileName = RepositoryUtils.getLastNameByPath(localPath);
        return RepositoryFactory.getRepository().getRealFilePath(
                localPath.substring(0, localPath.length() - fileName.length())) + FilePathCreator.SEPARATOR + fileName;
    }

    protected static void sendBySftp(String fullName, String localPath, ServerType server, int exportId,
            SuspendNotificationSender reSender, int quickRepeatCount, int quickRepeatDelay) throws Exception {

        RepeatableOperation rc = new RepeatableOperation(quickRepeatCount) {
            @Override
            protected void perform() throws Exception {
                if (server.hasIdentity()) {
                    sendBySftp(localPath, fullName, server.getHost(), server.getPort(), server.getUser(),
                            server.getTimeout(), server.getIdentity());
                } else {
                    sendBySftp(localPath, fullName, server.getHost(), server.getPort(), server.getUser(),
                            server.getPassword(), server.getTimeout());
                }
            }
            @Override
            protected int getDelay() {
                return quickRepeatDelay;
            }
        };
        try {
            rc.performOperationThrowingException();

        } catch (Exception e) {
            if (reSender != null) {
                SuspendNotificationSender.suspendNotification(reSender, fullName, "" + exportId, "", e);
            }
            throw e;
        }
    }

    @Override
    public String getDbName() {
        return db.getTitle();
    }

    protected boolean byRecords() {
        return recordsProcessId > 0;
    }

    protected boolean isWhenReady() {
        return deliveryFileId == BY_WHEN_READY // it's correct for CCH/HW as they have no real package ID
                || byDeliveryPacket();         // currently any sending by package ID can be considered as automated
    }

    protected boolean byDeliveryPacket() {
        return deliveryFileId > BY_ISSUE;
    }

    protected boolean byIssue() {
        return deliveryFileId == BY_ISSUE;
    }

    protected ClDbVO getDb() {
        return db;
    }

    public int getDeliveryFileId() {
        return deliveryFileId;
    }
}