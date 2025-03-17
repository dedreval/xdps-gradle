package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.PublishOperation;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSenderEntire;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/25/2016
 */
public class SemanticoSenderEntire extends AbstractSenderEntire {

    private boolean staticContentDisabled;
    private String comment = "";
    private HWClient client;
    private String hwFreq = HWFreq.BULK.getValue();

    public SemanticoSenderEntire(EntireDbWrapper db) {
        super(db, "SEMANTICO:ENTIRE:ML3G:" + db.getDbName(), PubType.TYPE_SEMANTICO);
    }

    protected SemanticoSenderEntire(EntireDbWrapper db, String generateName, String exportTypeName) {
       super(db, generateName, exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish, PublishOperation op) throws Exception {
        super.init(publish, op);
        staticContentDisabled = publish.isStaticContentDisabled();
        sendTestMode = CochraneCMSPropertyNames.isSemanticoPublishTestMode();

        hwFreq = publish.getHWFrequency();
        if (hwFreq == null) {
            if (export.noPrioritySet()) {
                BaseType bt = BaseType.find(getDbName()).get();
                hwFreq = (byRecords() ? bt.getSelectiveHWFrequency() : bt.getEntireHWFrequency()).getValue();
            } else {
                hwFreq = HWFreq.getHWFreq(export.getHwFrequency()).getValue();
            }
        }
    }

    @Override
    protected void initProfileProperties(PublishProfile.PubLocationPath path) {
        super.initProfileProperties(path);
        client = HWClient.Factory.getFactory().getHWClient(path);
    }

    @Override
    public HWClient getHWClient() {
        return client;
    }

    @Override
    protected String getComment() {
        return buildCommentStr(comment);
    }

    @Override
    public String getDestinationPlace() {
        return super.getDestinationPlace() + SemanticoSender.getHWEndpoint(false, getHWClient());
    }

    @Override
    protected void send() throws Exception {

        String fileName = getPackageFileName();
        String fullName = getServerPath() + FilePathCreator.SEPARATOR + fileName;

        if (!sendTestMode) {
            if (isLocalHost()) {
                SemanticoSender.sendSemantico(fullName, rps.getRealFilePath(getPackagePath()), getExportId(), false);

            } else {
                SemanticoSender.sendSemanticoSftp(fullName, getPackagePath(), server, getExportId(), false);
            }
        }

        comment = SemanticoSender.notifySemantico(this, fullName, hwFreq,
                isStaticContentDisabled(), null, false, CochraneCMSPropertyNames.isSemanticoApiCallTestMode());

        if (CochraneCMSPropertyNames.isLiteratumEventPublishTestMode()) {
            SemanticoSender.tryImitateLiteratumEvent(fileName, getDbName(), export.getId(), null, null, ps);
        }
    }

    private boolean isStaticContentDisabled() {
        return staticContentDisabled;
    }
}