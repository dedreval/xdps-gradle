package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 6/6/2016
 */
public class SemanticoCDSRTopicGeneratorEntire extends SemanticoCDSRGeneratorEntire {

    public SemanticoCDSRTopicGeneratorEntire(EntireDbWrapper db) {
        super(db, "SEMANTICO:ENTIRE:TOPICS:" + db.getDbName(), PubType.TYPE_SEMANTICO_TOPICS);
    }

    protected boolean isOnlyTopic() {
        return true;
    }
}
