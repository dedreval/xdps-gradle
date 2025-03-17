package com.wiley.cms.cochrane.cmanager.publish.send.literatum;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSenderEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 03.11.11
 */
public class LiteratumSenderEntire extends AbstractSenderEntire {
    private AtyponClient client;

    public LiteratumSenderEntire(EntireDbWrapper db) {
        super(db, "LIT:ENTIRE:ML3G:" + db.getDbName(), PubType.TYPE_LITERATUM);
    }

    protected LiteratumSenderEntire(EntireDbWrapper db, String sendName, String exportTypeName) {
        super(db, sendName, exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish, PublishOperation op) throws Exception {
        super.init(publish, op);
        sendTestMode = CochraneCMSPropertyNames.isWollitPublishTestMode();
    }

    @Override
    protected void initProfileProperties(PublishProfile.PubLocationPath path) {
        super.initProfileProperties(path);
        if (path.getConnectionType().exists()) {
            client = new AtyponClient(path, getServerLogin(), getServerPassword());
        }
    }

    @Override
    public String getDestination() {
        return client == null ? getServerName() : client.toString();
    }

    @Override
    public String getComment() {
        return buildCommentStr("");
    }

    @Override
    public String getDestinationPlace() {
        return "";
    }

    @Override
    protected void send() throws Exception {
        String fileName = getPackageFileName();
        if (!isLocalHost() && !sendTestMode) {
            LiteratumSender.sendLiteratum(fileName, packagePath, getServerPath(), client, getServer(),
                    DbEntity.NOT_EXIST_ID, false);
        }
        if (CochraneCMSPropertyNames.isLiteratumEventPublishTestMode()) {
            LiteratumSender.tryImitateLiteratumEvent(fileName, getDbName(), export.getId(), ps);
        }
    }
}
