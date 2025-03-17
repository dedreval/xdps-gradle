package com.wiley.cms.cochrane.cmanager.publish.send.aries;

import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/15/2021
 */
public class AriesAcknowledgementPublishSender extends AriesAcknowledgementSender {

    public AriesAcknowledgementPublishSender(ClDbVO db) {
        super(db, "ARIES:ACK_PUBLISH:" + db.getTitle(), PubType.TYPE_ARIES_ACK_P);
    }

    @Override
    protected void setDeliveryFileStatusOnSent() {
        if (byDeliveryPacket()) {
            rs.setDeliveryFileStatus(getDeliveryFileId(), IDeliveryFileStatus.STATUS_PUBLISHING_FINISHED_SUCCESS, null,
                    IDeliveryFileStatus.OP_ARIES_ACK_SENT);
        }
    }
}
