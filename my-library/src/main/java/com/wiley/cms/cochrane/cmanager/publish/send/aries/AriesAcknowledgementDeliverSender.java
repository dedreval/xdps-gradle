package com.wiley.cms.cochrane.cmanager.publish.send.aries;

import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/15/2021
 */
public class AriesAcknowledgementDeliverSender extends AriesAcknowledgementSender {
    public AriesAcknowledgementDeliverSender(ClDbVO db) {
        super(db, "ARIES:ACK_DELIVER:" + db.getTitle(), PubType.TYPE_ARIES_ACK_D);
    }

    @Override
    protected void setDeliveryFileStatusOnSent() {
        rs.setDeliveryFileStatus(getDeliveryFileId(), null, null, IDeliveryFileStatus.OP_ARIES_ACK_SENT);
    }
}
