package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.cmanager.res.PubType;

import java.util.Collections;
import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSMeSHGenerator extends AbstractGenerator<ArchiveHolder> {

    public DSMeSHGenerator(ClDbVO db) {
        super(db, "DS:MESH:" + db.getTitle(), PubType.TYPE_DS_MESH);
    }

    @Override
    protected List<ArchiveEntry> processRecordList(List<RecordWrapper> recordList) throws Exception {
        return Collections.emptyList();
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return Collections.emptyList();
    }

    @Override
    protected void createArchive() throws Exception {
        createMeshArchive();
    }
}
