package com.wiley.cms.cochrane.cmanager.publish.send;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.11.11
 */
public abstract class AbstractSenderEntire extends ArchiveSender {

    private final EntireDbWrapper db;
    private boolean byRecords;

    protected AbstractSenderEntire(EntireDbWrapper db, String sendName, String exportTypeName) {
        super(db.getDbName(), sendName, exportTypeName);
        this.db = db;
    }

    protected void init(PublishWrapper publish, PublishOperation op) throws Exception {

        super.init(publish);

        byRecords = publish.byRecords();
        initPackagePath();

        export = checkExportEntityEntire(publish, db.getDbName(), PublishProfile.buildExportDbName(
                publish.getPath(), false, publish.byRecords()), packagePath, false);

        initServerProperties(publish);
    }

    private void initPackagePath() {
        packagePath = FilePathCreator.getDirPathForPublish(db.getDbName(), DbEntity.NOT_EXIST_ID,
                getExportTypeName(), false);
    }

    private void initServerProperties(PublishWrapper pw) {

        PublishProfile.PubLocationPath path = pw.getPath();
        if (path != null) {
            //todo define on upper level
            if (pw.byRecords()) {
                path = PublishProfile.getProfile().get().getIssuePubLocation(path.getPubType().getMajorType(),
                    getExportTypeName(), db.getDbName());
            }
            initProfileProperties(path);
        }
    }

    public void start(PublishWrapper publish, PublishOperation op) {
        LOG.debug(String.format("> Start %s [%s]", op.getName(), tag));

        String notReadyMsg;
        try {
            init(publish, op);
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
            if (publish.byRecords() && (!publish.isDelete() || !publish.isSend())) {
                EntireRecordWrapper.getProcessStorage().deleteProcess(publish.getRecordsProcessId());
            }
        }

        if (notReadyMsg != null)  {
            LOG.error(String.format("< Finish %s [%s] %s", op.getName(), tag, notReadyMsg));
            logSendingFailed(publish,  notReadyMsg);

        } else {
            LOG.debug(String.format("< Finish %s [%s]", op.getName(), tag));
        }
    }

    @Override
    protected void handleError(PublishWrapper publish, PublishOperation op, Exception e) {
        op.onFail(publish, export.getId(), rs);
        LOG.error(String.format("< Finish %s [%s] Failed with Exception: %s", op.getName(), tag,
                            e.getMessage()), e);
        logSendingFailed(publish, e.getMessage() == null ? "null" : e.getMessage());
    }

    @Override
    protected void logSendingSuccessful(PublishWrapper publish, Boolean relatedFiles) {
        if (relatedFiles != null) {
            logSendingSuccessful(publish, "sending.db_name_entire_packages", getPackageFileName(), relatedFiles);
        } else {
            logSendingSuccessful(publish, "sending.db_name_entire", "sending.package", null);
        }
    }

    protected void logSendingSuccessful(PublishWrapper publish, String mainMsgPattern, String addMsgPattern,
                                        Boolean relatedFiles) {
        if (!publish.hasMessage()) {
            publish.addMessage(MessageSender.MSG_TITLE_SENDING_SUCCESSFUL);
            if (relatedFiles != null) {
                publish.addSubMessages(fillReport(mainMsgPattern), addMsgPattern);

            } else {
                publish.addMessage(fillReport(mainMsgPattern));
            }
            return;
        }

        if (relatedFiles != null) {
            if (relatedFiles) {
                publish.addSubMessages(fillReport(TEMPLATE_SENDING_PACKAGES), addMsgPattern);
            } else {
                publish.addSubMessage(addMsgPattern);
            }
        } else {
            publish.addMessage(getMessageMap(addMsgPattern, new HashMap<>()));
        }
    }

    @Override
    protected void logSendingFailed(PublishWrapper publish, String message) {

        Map<String, String> map2 = new HashMap<>();
        map2.put("issue_and_db_name", fillReport("sending.db_name_entire_failed"));
        map2.put("details", message);
        String report = CochraneCMSProperties.getProperty("sending.failed", map2);

        MessageSender.sendReport(MessageSender.MSG_TITLE_SENDING_FAILED, db.getDbName(), report);
    }

    private String fillReport(String message) {
        Map<String, String> map = new HashMap<>();
        map.put("db_name", db.getDbName());
        return getMessageMap(message, map);
    }

    @Override
    public String getDbName() {
        return db.getDbName();
    }

    protected boolean byRecords() {
        return byRecords;
    }
}