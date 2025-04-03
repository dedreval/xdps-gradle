package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/25/2016
 */
public class SemanticoTopicSender extends SemanticoSender {
    public SemanticoTopicSender(ClDbVO db) {
        super(db, "SEMANTICO:TOPICS:" + db.getTitle(), PubType.TYPE_SEMANTICO_TOPICS);
    }
}