package com.wiley.cms.cochrane.cmanager.publish.generate.ml3g;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 01.11.11
 */
public class ML3GCentralGenerator extends ML3GGenerator {

    protected ML3GCentralGenerator(ClDbVO db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected String getArchivePrefix(String recName) {
        return recName.substring(recName.length() - THREE);
    }
}
