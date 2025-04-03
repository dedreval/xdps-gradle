package com.wiley.cms.cochrane.cmanager.publish.unpack;

import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import org.apache.commons.lang.exception.ExceptionUtils;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.IPublish;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.PublishStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.publish.util.FileNameMatcher;
import com.wiley.cms.cochrane.cmanager.publish.util.SSHOperations;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 08.11.11
 */
public abstract class AbstractUnpacker implements IPublish {
    private static final Logger LOG = Logger.getLogger(AbstractUnpacker.class);

    private static final MessageFormat MSG_UNPACK_FAIL =
            new MessageFormat("Package {0} failed to unpack for \"{1}\" DB.<br><b>Details</b>: {2}");
    private static final MessageFormat MSG_UNPACK_SUCCESS =
            new MessageFormat("Package(s) {0} has been successfully unpacked for \"{1}\" DB.");
    private static final MessageFormat MSG_DETAILS =
            new MessageFormat("<br><b>Details</b>:<ul><li>{0} records have been updated. {1}</ul>");

    private static final String REPORT_NOTIF_PARAM = "report";
    private static final String DATABASE_NOTIF_PARAM = "database";
    private static final String DETAILS_NOTIF_PARAM = "details";
    private static final String ISSUE_AND_DB_NAME_NOTIF_PARAM = "issue_and_db_name";
    private static final String PUBLISH_UNPACKER = "Publish Unpacker";
    private static final String FINISH_UNPACK = "< Finish Unpack [";

    protected IResultsStorage rs;
    protected IRecordStorage recs;
    protected IActivityLogService als;

    private ClDbVO db;
    private String unpackName;
    private String exportTypeName;
    private String unpackingPath;
    private String serverName;
    private String serverLogin;
    private String serverPassword;
    private String serverPath;
    private String packageFileName;
    private int exportId;
    private int unpackedRecordsCount;
    private int deletedRecordsCount;

    //private List<RecordWrapper> records;
    private int recordsProcessId;

    protected AbstractUnpacker(ClDbVO db, String unpackName, String exportTypeName) {

        this.db = db;
        this.unpackName = unpackName;
        this.exportTypeName = exportTypeName;
    }

    private void init(PublishWrapper publish) throws Exception {
        rs = ResultStorageFactory.getFactory().getInstance();
        recs = RecordStorageFactory.getFactory().getInstance();
        als = AbstractManager.getActivityLogService();
        IPublishStorage ps = PublishStorageFactory.getFactory().getInstance();
        String exportName = PubType.find(exportTypeName).get().getExportName();

        if (!publish.isNewPublish()) {
            exportId = publish.getId();
        } else {
            PublishEntity export = ps.createPublish(
                    db.getId(), publish.getPublishEntity().getPublishType(), null, HWFreq.NONE.ordinal(), false);
            publish.setPublishEntity(export);
            exportId = export.getId();
        }

        packageFileName = rs.findPublish(exportId).getFileName();
        recordsProcessId = publish.getRecordsProcessId();

        PublishProfile.PubLocationPath path = publish.getPath();
        if (path != null) {
            initProfileProperties(path);
        }
    }

    private void initProfileProperties(PublishProfile.PubLocationPath path) {

        ServerType server = path.getServerType();
        serverName = server.getHost();
        serverLogin = server.getUser();
        serverPassword = server.getPassword();
        //serverPath = PublishProfile.buildServerPath(path, db.getTitle());
        serverPath = path.getFolder();
        unpackingPath = path.getUnpackFolder();
    }

    public void start(PublishWrapper publish, PublishOperation op) {
        LOG.debug("> Start Unpack [" + unpackName + "]");
        try {
            init(publish);
            if (!checkPublishReadiness()) {
                publish.setPublishEntity(rs.setUnpacking(exportId, false, true));
                String message = "Unpacking has been stopped: package is sending or was not sent or does not exists";
                LOG.error(FINISH_UNPACK + unpackName + "] " + message);
                activityLogOnFailed(message);
                sendNotificationOnFailed(message);
                return;
            }
            unpack();
        } catch (Exception e) {
            publish.setPublishEntity(rs.setUnpacking(exportId, false, true));
            String message = e.getMessage() == null ? ExceptionUtils.getStackTrace(e) : e.getMessage();
            LOG.error(FINISH_UNPACK + unpackName + "] Failed with exception: " + message, e);
            activityLogOnFailed(message);
            sendNotificationOnFailed(message);
            return;
        }
        publish.setPublishEntity(rs.updatePublishUnpacking(exportId, false, new Date(), true));
        LOG.debug(FINISH_UNPACK + unpackName + "]");
        addActivityLogOnSuccess();
        sendNotificationOnSuccess(publish);
    }

    private void sendNotificationOnSuccess(PublishWrapper publish) {

        if (publish.hasMessage()) {
            publish.addMessage(getReport(false));
        } else {
            publish.addMessage(MessageSender.MSG_TITLE_UNPACK_SUCCESS);
            publish.addMessage(getReport(true));
        }
    }

    protected String getReport(boolean includeIssue) {
        Map<String, String> map = new HashMap<String, String>();

        if (includeIssue) {
            map.put(ISSUE_AND_DB_NAME_NOTIF_PARAM, getIssueAndDb("unpacking.issue_and_db_name"));
        } else {
            map.put(ISSUE_AND_DB_NAME_NOTIF_PARAM, getMessageMap("unpacking.package", new HashMap<String, String>()));
        }
        map.put(DETAILS_NOTIF_PARAM, getUnpackedRecordsCount() + " records have been updated."
                            + addDeletedProtocolsMessage());
        return CochraneCMSProperties.getProperty("unpacking.success_details", map);
    }

    private void addActivityLogOnSuccess() {
        Object[] args = new Object[]{getPackageFileName(), db.getTitle()};
        String msg = MSG_UNPACK_SUCCESS.format(args);

        msg = msg.concat(getDetailsMessage());

        als.info(ActivityLogEntity.EntityLevel.DB, ILogEvent.PUBLISH_UNPACKING_SUCCESS,
                db.getId(), db.getTitle(), PUBLISH_UNPACKER, msg);
    }

    protected String getDetailsMessage() {
        String dpm = addDeletedProtocolsMessage();
        dpm = dpm.equals("") ? dpm : "<li>" + addDeletedProtocolsMessage();

        Object[] args = new Object[]{getUnpackedRecordsCount(), dpm};
        return MSG_DETAILS.format(args);
    }

    protected String addDeletedProtocolsMessage() {
        return "";
    }

    private void activityLogOnFailed(String message) {
        Object[] args = new Object[]{getPackageFileName(), db.getTitle(), message};
        String msg = MSG_UNPACK_FAIL.format(args);
        als.error(ActivityLogEntity.EntityLevel.DB, ILogEvent.PUBLISH_UNPACKING_FAILED, db.getId(), db.getTitle(),
                PUBLISH_UNPACKER, msg);
    }

    private void sendNotificationOnFailed(String message) {
        Map<String, String> map = new HashMap<String, String>();

        Map<String, String> map2 = new HashMap<String, String>();
        map2.put(ISSUE_AND_DB_NAME_NOTIF_PARAM, getIssueAndDb("unpacking.issue_and_db_name_failed"));
        map2.put(DETAILS_NOTIF_PARAM, message);
        String report = CochraneCMSProperties.getProperty("unpacking.failed", map2);

        map.put(REPORT_NOTIF_PARAM, report);
        map.put(DATABASE_NOTIF_PARAM, db.getTitle());

        MessageSender.sendMessage("unpacking_failed_notification", map);
    }

    protected String getMessageMap(String message, Map<String, String> map) {
        map.put("name", getPackageFileName());
        map.put("server", getServerName());
        map.put("place", (!"".equals(getUnpackingPath()) ? getUnpackingPath() : getServerPath()));
        return CochraneCMSProperties.getProperty(message, map);
    }

    protected String getIssueAndDb(String message) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("issue_num", Integer.toString(db.getIssue().getNumber()));
        map.put("issue_year", Integer.toString(db.getIssue().getYear()));
        map.put("db_name", db.getTitle());
        return getMessageMap(message, map);
    }

    private boolean checkPublishReadiness() {
        PublishEntity pe = rs.findPublish(exportId);
        return pe.sent() && !pe.isSending() && isPackageExists(getServerPath() + "/" + getPackageFileName());
    }

    protected abstract void unpack() throws Exception;

    protected boolean isPackageExists(String packagePath) {
        return SSHOperations.isFileExists(packagePath, getServerName(), getServerLogin(), getServerPassword());
    }

    protected void rename(String oldPath, String newPath) throws Exception {
        SSHOperations.rename(oldPath, newPath, getServerName(), getServerLogin(), getServerPassword());
    }

    protected Integer countUnpackedRecords() {
        Object[] args = new Object[]{FileNameMatcher.valueOf(db.getTitle().toUpperCase()).getCommand(),
            serverPath + "/" + packageFileName};
        String params = getFindParams().format(args);
        return SSHOperations.countRecords(unpackingPath + "/.", params, getServerName(), getServerLogin(),
                getServerPassword());
    }

    protected void unpackPackage(String path) throws Exception {
        SSHOperations.unpackPackage(path, getPackageFileName(), getServerPath(), getServerName(), getServerLogin(),
                getServerPassword());
    }

    protected void deleteFolders(String[] names, String path) throws Exception {
        SSHOperations.deleteFolders(names, path, getServerName(), getServerLogin(), getServerPassword());
    }

    protected void mkdir(String path) throws Exception {
        SSHOperations.mkdir(path, getServerName(), getServerLogin(), getServerPassword());
    }

    protected MessageFormat getFindParams() {
        return new MessageFormat(" -name \"*\" -prune -type d {0} -newer {1} | wc -l");
    }

    protected ClDbVO getDb() {
        return db;
    }

    protected boolean byRecords() {
        return recordsProcessId > 0;
    }

    //protected List<RecordWrapper> getRecords() {
    //    return records;
    //}

    public String getUnpackingPath() {
        return unpackingPath;
    }

    public String getServerName() {
        return serverName;
    }

    public String getServerLogin() {
        return serverLogin;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    public String getServerPath() {
        return serverPath;
    }

    public String getPackageFileName() {
        return packageFileName;
    }

    public int getUnpackedRecordsCount() {
        return unpackedRecordsCount;
    }

    public void setUnpackedRecordsCount(int unpackedRecordsCount) {
        this.unpackedRecordsCount = unpackedRecordsCount;
    }

    public int getDeletedRecordsCount() {
        return deletedRecordsCount;
    }

    public void setDeletedRecordsCount(int deletedRecordsCount) {
        this.deletedRecordsCount = deletedRecordsCount;
    }
}
