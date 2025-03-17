package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 6/6/2016
 */
public class SemanticoCDSRTopicGenerator extends SemanticoCDSRGenerator {

    public SemanticoCDSRTopicGenerator(ClDbVO db) {
        super(db, "SEMANTICO:TOPICS:" + db.getTitle(), PubType.TYPE_SEMANTICO_TOPICS);
    }

    @Override
    protected boolean isOnlyTopic() {
        return true;
    }

    @Override
    protected boolean isWhenReady() {
        return byDeliveryPacket();
    }
}
