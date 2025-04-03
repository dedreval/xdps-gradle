package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSCDSRMonthlyGenerator extends DSCDSRGenerator {

    public DSCDSRMonthlyGenerator(ClDbVO db) {
        super(db, "DS:ML3G:" + db.getTitle(), PubType.TYPE_DS_MONTHLY);
    }
}