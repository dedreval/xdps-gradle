package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/25/2016
 */
public class SemanticoTopicSenderEntire extends SemanticoSenderEntire {
    public SemanticoTopicSenderEntire(EntireDbWrapper db) {
        super(db, "SEMANTICO:ENTIRE:TOPICS:" + db.getDbName(), PubType.TYPE_SEMANTICO_TOPICS);
    }
}