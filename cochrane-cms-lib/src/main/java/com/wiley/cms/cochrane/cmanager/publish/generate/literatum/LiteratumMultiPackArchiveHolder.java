package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.Date;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.util.GenerationErrorCollector;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.repository.IRepository;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/18/2018
 */
public class LiteratumMultiPackArchiveHolder extends LiteratumArchiveHolder {

    private final GenerationErrorCollector errCollector;
    private int maxRecordCountInArchive = 0;

    public LiteratumMultiPackArchiveHolder(GenerationErrorCollector errCollector) {
        this.errCollector = errCollector;
    }

    @Override
    protected LiteratumArchiveHolder newArchiveHolder() {
        LiteratumMultiPackArchiveHolder ret = new LiteratumMultiPackArchiveHolder(errCollector);
        ret.maxRecordCountInArchive = maxRecordCountInArchive;
        return ret;
    }

    @Override
    protected void init(PublishWrapper publish, PublishEntity entity, String path) {
        super.init(publish, entity, path);

        maxRecordCountInArchive = BaseType.find(entity.getDb().getTitle()).get().getPubInfo(
                PubType.TYPE_LITERATUM).getOutBatch();
    }

    @Override
    protected LiteratumArchiveHolder checkNext(String sbnName, IRepository rps, IResultsStorage rs) throws Exception {

        if (maxRecordCountInArchive > 0 && maxRecordCountInArchive == curRecordCount) {

            closeArchive41(sbnName, errCollector, rps, rs);

            curRecordCount = 0;

            addNewExport(PublishWrapper.getLiteratumPackageName(export.getDb().getTitle(), !wasDelivered(), new Date(),
                    archiveTemplate), export, getFolder(), archiveTemplate, rps);
        }

        return super.checkNext(sbnName, rps, rs);
    }
}
