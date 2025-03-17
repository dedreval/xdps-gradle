package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.cch.CCHCentralGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSCentralGeneratorEntire extends CCHCentralGeneratorEntire {

    public DSCentralGeneratorEntire(EntireDbWrapper db) {
        super(db, "DS:ENTIRE:MAIN:" + db.getDbName(), PubType.TYPE_DS);
    }
}