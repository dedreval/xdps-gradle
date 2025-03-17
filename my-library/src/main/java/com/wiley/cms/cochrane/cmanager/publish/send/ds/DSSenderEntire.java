package com.wiley.cms.cochrane.cmanager.publish.send.ds;

import java.io.File;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSenderEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSSenderEntire extends AbstractSenderEntire {

    public DSSenderEntire(EntireDbWrapper db) {
        super(db, "DS:ENTIRE:", PubType.TYPE_DS);
    }

    @Override
    protected void send() throws Exception {
        if (!isLocalHost()) {
            sendByFtp(getPackageFileName(), new File(rps.getRealFilePath(getPackageFolder()), getPackageFileName()));
        }
    }
}