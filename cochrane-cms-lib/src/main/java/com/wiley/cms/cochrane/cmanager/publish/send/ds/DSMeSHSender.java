package com.wiley.cms.cochrane.cmanager.publish.send.ds;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSMeSHSender extends DSSender {

    public DSMeSHSender(ClDbVO db) {
        super(db, "DS:MESH:" + db.getTitle(), PubType.TYPE_DS_MESH);
    }
}