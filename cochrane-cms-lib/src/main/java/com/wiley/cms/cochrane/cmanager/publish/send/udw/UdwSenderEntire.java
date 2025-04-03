package com.wiley.cms.cochrane.cmanager.publish.send.udw;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSenderEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;

import static com.wiley.cms.cochrane.cmanager.publish.send.AbstractSender.sendByOrdinalOrSecureFtp;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 05.02.2019
 */
public class UdwSenderEntire extends AbstractSenderEntire {
    public UdwSenderEntire(EntireDbWrapper db) {
        super(db, "UDW:ENTIRE:", PubType.TYPE_UDW);
    }

    @Override
    protected void send() throws Exception {
        sendByOrdinalOrSecureFtp(getPackagePath(),
                getServerPath() + "/" + getPackageFileName(),
                getServerName(),
                getServerPort(),
                getServerLogin(),
                getServerPassword(),
                getServerTimeout());
    }
}
