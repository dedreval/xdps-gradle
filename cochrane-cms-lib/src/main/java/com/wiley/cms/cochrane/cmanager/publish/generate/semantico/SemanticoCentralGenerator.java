package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCentralGenerator;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/2/2016
 */
public class SemanticoCentralGenerator extends ML3GCentralGenerator {

    public SemanticoCentralGenerator(ClDbVO db) {
        super(db, "SEMANTICO:ML3G:" + db.getTitle(), PubType.TYPE_SEMANTICO);
    }
}
