package com.wiley.cms.cochrane.cmanager.publish.send.ds;

import java.io.File;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSender;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSSender extends AbstractSender {

    public DSSender(ClDbVO db) {
        super(db, "DS:", PubType.TYPE_DS);
    }

    protected DSSender(ClDbVO db, String sendName, String exportTypeName) {
        super(db, sendName, exportTypeName);
    }

    @Override
    protected void send() throws Exception {
        if (!isLocalHost()) {
            sendByFtp(getPackageFileName(), new File(rps.getRealFilePath(getPackageFolder()), getPackageFileName()));
        }
    }
}