package com.wiley.cms.cochrane.cmanager.publish.generate.ds;

import java.util.List;
import java.util.function.Supplier;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.cch.CCHCentralGenerator;
import com.wiley.cms.cochrane.cmanager.res.PubType;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 03.07.2019
 */

public class DSCentralGenerator extends CCHCentralGenerator {

    private boolean onlyPublishedHW;

    public DSCentralGenerator(ClDbVO db) {
        super(db, "DS:MAIN:" + db.getTitle(), PubType.TYPE_DS);
    }

    @Override
    protected void init(PublishWrapper publish) throws Exception {
        super.init(publish);
        onlyPublishedHW = publish.isOnlyPublishedContent();
    }

    @Override
    protected List<RecordWrapper> getRecordList(int startIndex, int count) {
        return byDeliveryPacket() ? RecordWrapper.getDbRecordWrapperList(getDb().getId(), getDeliveryFileId(),
                startIndex, count) : super.getRecordList(startIndex, count);
    }

    @Override
    protected boolean isRecordNotIncluded(RecordWrapper record, Supplier<String> pathSupplier) {
        return super.isRecordNotIncluded(record, pathSupplier)
                || ((byIssue() || byDeliveryPacket()) && isRecordHWError(record))
                || (onlyPublishedHW && isRecordNotPublished(record));
    }
}
