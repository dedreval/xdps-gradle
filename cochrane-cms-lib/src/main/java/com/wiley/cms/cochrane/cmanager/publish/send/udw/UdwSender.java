package com.wiley.cms.cochrane.cmanager.publish.send.udw;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.publish.send.AbstractSender;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 05.02.2019
 */
public class UdwSender extends AbstractSender {
    public UdwSender(ClDbVO db) {
        super(db, "UDW:", PubType.TYPE_UDW);
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
