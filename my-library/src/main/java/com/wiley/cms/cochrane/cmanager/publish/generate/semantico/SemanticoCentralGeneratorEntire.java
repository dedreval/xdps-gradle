package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCentralGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/2/2016
 */
public class SemanticoCentralGeneratorEntire extends ML3GCentralGeneratorEntire {

    public SemanticoCentralGeneratorEntire(EntireDbWrapper db) {
        super(db, "SEMANTICO:ENTIRE:ML3G:" + db.getDbName(), PubType.TYPE_SEMANTICO);
    }
}
