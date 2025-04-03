package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.res.PubType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSEditorialGeneratorEntire extends ML3GCDSRGeneratorEntire {

    public DSEditorialGeneratorEntire(EntireDbWrapper db) {
        super(db, "DS:ENTIRE:ML3G:" + db.getDbName(), PubType.TYPE_DS);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        setCheckRecordWithNoOnlineDate(CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4DS());
    }

    @Override
    protected List<ArchiveEntry> createParticularFiles() throws Exception {
        List<ArchiveEntry> ret = new ArrayList<>();
        addDoiXml(ret, archiveRootDir, null, RecordHelper::buildDoiCDSRAndEditorial);
        return ret;
    }

    //@Override
    //protected boolean checkRecordWithNoOnlineDate() {
    //    return true;
    //}

    @Override
    protected boolean addPreviousVersion() {
        return false;
    }

    @Override
    protected boolean checkEmpty() {
        return true;
    }
}