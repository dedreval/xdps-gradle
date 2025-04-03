package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGenerator;
import com.wiley.cms.cochrane.cmanager.res.PubType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSEditorialGenerator extends ML3GCDSRGenerator {

    public DSEditorialGenerator(ClDbVO db) {
        super(db, "DS:ML3G:" + db.getTitle(), PubType.TYPE_DS);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        setCheckRecordWithNoOnlineDate(CochraneCMSPropertyNames.checkEmptyPublicationDateInWML3G4DS());
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return (hasIncludedNames() ? getRecordListFromIncludedNames(count, false)
                : super.getRecordList(startIndex, count));
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
