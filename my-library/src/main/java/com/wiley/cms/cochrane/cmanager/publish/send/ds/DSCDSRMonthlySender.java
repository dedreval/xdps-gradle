package com.wiley.cms.cochrane.cmanager.publish.send.ds;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSCDSRMonthlySender extends DSSender {

    public DSCDSRMonthlySender(ClDbVO db) {
        super(db, "DS:ML3G:" + db.getTitle(), PubType.TYPE_DS_MONTHLY);
    }
}