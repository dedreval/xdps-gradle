package com.wiley.cms.cochrane.cmanager.publish.send.ds;

import java.io.File;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSenderWhenReady;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSCCASender extends AbstractSenderWhenReady {

    private static final String SENDING_SUCCESSFUL_NOTIFICATION_KEY = "cca_ds_sending_successful";
    private static final String SENDING_FAILED_NOTIFICATION_KEY = "cca_ds_sending_failed";

    public DSCCASender(ClDbVO dbVO) {
        super(dbVO, PubType.TYPE_DS);
    }

    public DSCCASender(EntireDbWrapper db) {
        super(db, PubType.TYPE_DS);
    }

    public DSCCASender(int dbId) {
        super(dbId, false, PubType.TYPE_DS);
    }

    @Override
    protected void doSend() throws Exception {
        if (!isLocalHost()) {
            //sendByFtp(StringUtils.substringAfterLast(getPackagePath(), "/"), rps.getFile(getPackagePath()));
            sendByFtp(getPackageFileName(), new File(rps.getRealFilePath(getPackageFolder()), getPackageFileName()));
        }
    }

    protected String getSuccessfulNotificationKey() {
        return SENDING_SUCCESSFUL_NOTIFICATION_KEY;
    }

    protected String getFailedNotificationKey() {
        return SENDING_FAILED_NOTIFICATION_KEY;
    }
}
