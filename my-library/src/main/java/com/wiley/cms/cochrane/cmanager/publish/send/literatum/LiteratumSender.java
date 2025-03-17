package com.wiley.cms.cochrane.cmanager.publish.send.literatum;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumResponseChecker;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSender;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.notification.SuspendNotificationSender;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class LiteratumSender extends AbstractSender {
    private AtyponClient client;
    private boolean replicateMode = false;

    public LiteratumSender(ClDbVO db) {
        super(db, "LIT:ML3G:" + db.getTitle(), PubType.TYPE_LITERATUM);
    }

    protected LiteratumSender(ClDbVO db, String sendName, String exportTypeName) {
        super(db, sendName + db.getTitle(), exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        sendTestMode = CochraneCMSPropertyNames.isWollitPublishTestMode();
    }

    @Override
    protected void initProfileProperties(PublishProfile.PubLocationPath path) {
        super.initProfileProperties(path);
        if (path.getConnectionType().exists()) {
            client = new AtyponClient(path, getServerLogin(), getServerPassword());
        }
        replicateMode = path.hasReplicateLocationRef();
    }

    @Override
    protected void send() throws Exception {
        String fileName = getPackageFileName();
        if (!isLocalHost() && !sendTestMode) {
            sendLiteratum(fileName, packagePath, getServerPath(), client, getServer(), getDeliveryFileId(),
                    byDeliveryPacket() && !replicateMode);
        }
        if (CochraneCMSPropertyNames.isLiteratumEventPublishTestMode()) {
            tryImitateLiteratumEvent(fileName, getDb().getTitle(), export.getId(), ps);
        }
    }

    @Override
    public String getDestination() {
        return client == null ? getServerName() : client.toString();
    }

    @Override
    public String getDestinationPlace() {
        return "";
    }

    @Override
    public String getComment() {
        return buildCommentStr("");
    }

    static void tryImitateLiteratumEvent(String fileName, String dbName, int exportId, IPublishStorage ps)
            throws Exception {
        BaseType bt = BaseType.find(dbName).get();
        if (bt.isCDSR()) {
            LiteratumResponseChecker.Responder.instance().imitateCDSRResponseForWOL(
                    fileName, ps.findPublishCdAndPubNumbers(exportId));

        } else if (bt.isEditorial()) {
            LiteratumResponseChecker.Responder.instance().imitateEditorialResponseForWOL(
                    fileName, ps.findPublishCdNumbers(exportId));

        } else if (bt.isCCA()) {
            LiteratumResponseChecker.Responder.instance().imitateCcaResponseForWOL(
                    fileName, ps.findPublishCdNumbers(exportId));

        } else if (bt.isCentral()) {
            LiteratumResponseChecker.Responder.instance().imitateCentralResponseForWOL(
                    fileName, ps.findPublishCdNumbers(exportId));
        }
    }

    static void sendLiteratum(String fileName, String packagePath, String serverPath, AtyponClient sender,
                              ServerType server, int dfId, boolean resend) throws Exception {
        if (sender != null) {
            sender.sendPackage(
                RepositoryFactory.getRepository().getRealFilePath(packagePath) + FilePathCreator.SEPARATOR, fileName);
        } else {
            PublishProfile pp = PublishProfile.PUB_PROFILE.get();
            sendBySftp(serverPath + FilePathCreator.SEPARATOR + fileName, packagePath + fileName, server, dfId,
                resend ? SuspendNotificationSender.SUSPEND_WR_SENDING : null, pp.getLitProcessAttemptCount(),
                    pp.getLitProcessAttemptDelay());
        }
    }
}
