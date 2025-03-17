package com.wiley.cms.cochrane.cmanager.publish.send.aries;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSender;
import com.wiley.cms.cochrane.cmanager.res.ServerType;
import com.wiley.cms.notification.SuspendNotificationSender;
import com.wiley.cms.process.RepeatableOperation;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/15/2021
 */
public class AriesAcknowledgementSender extends AbstractSender {

    protected AriesAcknowledgementSender(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
    }

    @Override
    protected void send() throws Exception {
        String fileName = getPackageFileName();
        String fullName = getServerPath() + FilePathCreator.SEPARATOR + fileName;

        if (isLocalHost() || (sendAriesSftp(fullName, getPackagePath(), server, getExportId(), true)
                && sendAriesSftp(AriesHelper.buildGOFileName(fullName), AriesHelper.buildGOFileName(getPackagePath()),
                    server, getExportId(), true))) {

            setDeliveryFileStatusOnSent();
        }
    }

    protected void setDeliveryFileStatusOnSent() {
    }

    static boolean sendAriesSftp(String fullName, String packagePath, ServerType server, int exportId, boolean resend)
            throws Exception {
        boolean[] ret = {false};
        RepeatableOperation rc = new RepeatableOperation() {
            @Override
            protected void perform() throws Exception {
                sendBySftp(packagePath, fullName, server.getHost(), server.getPort(), server.getUser(),
                        server.getPassword(), server.getTimeout());
                ret[0] = true;
            }
        };
        try {
            rc.performOperationThrowingException();

        } catch (Exception e) {
            if (resend) {
                SuspendNotificationSender.suspendNotification(SuspendNotificationSender.SUSPEND_ARIES_SENDING,
                        fullName, "" + exportId, "", e);
            }
            throw e;
        }
        return ret[0];
    }
}
