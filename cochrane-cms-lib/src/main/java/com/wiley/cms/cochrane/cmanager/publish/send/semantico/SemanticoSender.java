package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishService;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishServiceFactory;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseChecker;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSender;
import com.wiley.cms.cochrane.cmanager.publish.send.ArchiveSender;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.notification.SuspendNotificationSender;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.tes.util.CollectionCommitter;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.InputUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/25/2016
 */
public class SemanticoSender extends AbstractSender {

    private boolean staticContentDisabled;
    private String comment = "";
    private HWClient client;
    private boolean replicateMode;
    private String hwFreq = HWFreq.BULK.getValue();

    public SemanticoSender(ClDbVO db) {
        super(db, "SEMANTICO:ML3G:" + db.getTitle(), PubType.TYPE_SEMANTICO);
    }

    protected SemanticoSender(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);

        staticContentDisabled = publish.isStaticContentDisabled();
        sendTestMode = CochraneCMSPropertyNames.isSemanticoPublishTestMode();

        hwFreq = publish.getHWFrequency();
        if (hwFreq == null) {
            if (export.noPrioritySet()) {
                BaseType bt = BaseType.find(getDbName()).get();
                hwFreq = (isWhenReady() ? bt.getWhenReadyHWFrequency() : (byRecords()
                        ? bt.getSelectiveHWFrequency() : bt.getIssueHWFrequency())).getValue();
            } else {
                hwFreq = HWFreq.getHWFreq(export.getHwFrequency()).getValue();
            }
        }
    }

    @Override
    protected void initProfileProperties(PublishProfile.PubLocationPath path) {
        super.initProfileProperties(path);
        client = HWClient.Factory.getFactory().getHWClient(path);
        replicateMode = path.hasReplicateLocationRef();
    }

    @Override
    public HWClient getHWClient() {
        return client;
    }

    @Override
    protected String getComment() {
        return buildCommentStr(comment);
    }

    protected boolean isDelete() {
        return false;
    }

    @Override
    protected void send() throws Exception {

        String fileName = getPackageFileName();
        String fullName = getServerPath() + FilePathCreator.SEPARATOR + fileName;

        if (!sendTestMode && !isDelete()) {
            if (isLocalHost()) {
                sendSemantico(fullName, rps.getRealFilePath(getPackagePath()), getExportId(),
                        isWhenReady() && !replicateMode);
            } else {
                sendSemanticoSftp(fullName, getPackagePath(), server, getExportId(), isWhenReady() && !replicateMode);
            }
        }

        List<String> deletedDois = isDelete() ? Arrays.asList(InputUtils.readStreamToString(
                new FileInputStream(rps.getRealFilePath(getPackagePath()))).trim().split("\n")) : null;
        comment = notifySemantico(this, fullName, hwFreq, isStaticContentDisabled(),  deletedDois,
                isWhenReady() && !replicateMode, CochraneCMSPropertyNames.isSemanticoApiCallTestMode());

        if (CochraneCMSPropertyNames.isLiteratumEventPublishTestMode()) {
            tryImitateLiteratumEvent(fileName, getDb().getTitle(), export.getId(), getDb().getId(), deletedDois, ps);
        }
    }

    @Override
    public String getDestinationPlace() {
        return super.getDestinationPlace() + getHWEndpoint(isDelete(), getHWClient());
    }

    static void sendSemanticoSftp(String fullName, String packPath, ServerType server, int exportId, boolean resend)
            throws Exception {

        PublishProfile pp = PublishProfile.PUB_PROFILE.get();
        sendBySftp(fullName, packPath, server, exportId, resend ? SuspendNotificationSender.SUSPEND_HW_SENDING : null,
                pp.getHWProcessAttemptCount(), pp.getHWProcessAttemptDelay());
    }

    static void sendSemantico(String fullName, String realPath, int exportId, boolean resend) throws Exception {
        try {
            int count = PublishProfile.PUB_PROFILE.get().getHWProcessAttemptCount();
            RepeatableOperation rc = new RepeatableOperation(count) {
                @Override
                protected void perform() throws Exception {
                    FileUtils.copyFileByCommand(realPath, fullName);
                }

                @Override
                protected int getDelay() {
                    return PublishProfile.PUB_PROFILE.get().getHWProcessAttemptDelay();
                }
            };
            rc.performOperationThrowingException();

        } catch (Exception e) {
            if (resend) {
                SuspendNotificationSender.suspendNotification(SuspendNotificationSender.SUSPEND_HW_SENDING,
                        fullName, String.format("%d", exportId), "", e);
            }
            throw e;
        }
    }

    static String getHWEndpoint(boolean onDelete, HWClient client) {
        return "; \nHW endpoint: " + (onDelete ? client.getDeletePath() : client.getPath());
    }

    static void tryImitateLiteratumEvent(String fileName, String dbName, int exportId, Integer dbId,
                                         Collection<String> deletedDois, IPublishStorage ps) {
        BaseType bt = BaseType.find(dbName).get();
        if (bt.isCDSR() || bt.isEditorial()) {
            LiteratumResponseChecker.Responder.instance().imitateCDSRAndEditorialResponseForHW(
                    fileName, ps.findPublishCdAndPubNumbers(exportId, dbId));

        } else if (bt.isCCA()) {
            LiteratumResponseChecker.Responder.instance().imitateCcaResponseForHW(
                    fileName, ps.findPublishCdAndPubNumbers(exportId, dbId));

        } else if (bt.isCentral()) {
            Collection<String> cdNumbers = CochraneCMSPropertyNames.getHWLiteratumEventsImitateError() > 0
                    ? ps.findPublishCdNumbers(exportId) : null;
            LiteratumResponseChecker.Responder.instance().imitateCentralResponseForHW(
                    fileName, cdNumbers, deletedDois, false);
        }
    }

    static String notifySemantico(ArchiveSender publisher, String fullName, String freq, boolean staticContentDisabled,
                Collection<String> deletedCdNumbers, boolean resend, boolean testMode) throws CmsException {
        int exportId = publisher.getExportId();
        String dbName = publisher.getDbName();
        IPublishService publishService = PublishServiceFactory.getFactory().getInstance();
        SuspendNotificationSender sender = SuspendNotificationSender.SUSPEND_HW_SENDING;
        try {
            String ret = deletedCdNumbers != null
                ? withdrawSemantico(publisher, fullName, freq, deletedCdNumbers, testMode, publishService)
                : publishService.notifySemantico(exportId, fullName, dbName, freq, staticContentDisabled, testMode,
                    publisher.getHWClient());
            return ret == null ? "" : ret;

        } catch (CmsException ce) {

            Object obj = ce.getErrorInfo() == null ? null : ce.getErrorInfo().getErrorEntity();
            if (obj == null) {
                throw sender.errMsg(ce.getMessage());
            }
            if (resend) {
                SuspendNotificationSender.suspendNotification(sender, CochraneCMSBeans.getHWClient().toString(),
                        String.format("%d", exportId), obj.toString(), ce);
            }
            throw ce;

        } catch (Exception e) {
            throw sender.errMsg(e.getMessage());
        }
    }

    private static String withdrawSemantico(ArchiveSender publisher, String fullName, String freq,
            Collection<String> deletedCdNumbers, boolean testMode, IPublishService publishService) throws Exception {

        int batch = LiteratumResponseChecker.MAX_DOI_COUNT_PER_EVENT.get().asInteger();
        int exportId = publisher.getExportId();
        String dbName = publisher.getDbName();

        if (deletedCdNumbers.size() <= batch) {
            return publishService.notifySemantico(exportId, dbName, freq, deletedCdNumbers, testMode,
                    publisher.getHWClient());
        }

        StringBuilder sb = new StringBuilder();
        Exception[] exception = new Exception[1];
        CollectionCommitter<String> committer = new CollectionCommitter<String>(batch, new ArrayList<>()) {
            @Override
            public void commit(Collection<String> list) {
                try {
                    if (exception[0] == null) {
                        String ret = publishService.notifySemantico(exportId, dbName, freq, list, testMode,
                                publisher.getHWClient());
                        sb.append(ret).append("\n");
                    }
                } catch (Exception e) {
                    exception[0] = e;
                }
            }
        };
        deletedCdNumbers.forEach(committer::commit);
        committer.commitLast();
        if (exception[0] != null) {
            throw exception[0];
        }
        return sb.toString();
    }

    private boolean isStaticContentDisabled() {
        return staticContentDisabled;
    }
}