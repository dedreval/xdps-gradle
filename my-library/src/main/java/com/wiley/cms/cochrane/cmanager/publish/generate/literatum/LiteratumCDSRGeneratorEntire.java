package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishChecker;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.converter.services.RevmanMetadataHelper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class LiteratumCDSRGeneratorEntire extends LiteratumGeneratorEntire {

    public LiteratumCDSRGeneratorEntire(EntireDbWrapper db) {
        super(db);
    }

    @Override
    protected void processRecord(String dbName, RecordWrapper record, IPublishChecker checker) throws Exception {
        super.processRecord(dbName, record, record.getPubNumber(), RecordEntity.VERSION_LAST, record.getId(),
                checker);
        List<PrevVO> prevList = record.getVersions().getPreviousVersionsVO();
        for (PrevVO prev : prevList) {
            super.processRecord(dbName, record, prev.pub, prev.version, null, checker);
        }
    }

    @Override
    protected String getPubName(String cdNumber, int pubNumber) {
        return RevmanMetadataHelper.buildPubName(cdNumber, pubNumber);
    }

    @Override
    protected IPublishChecker createPublishChecker(List<? extends RecordWrapper> recordList) {
        return PublishChecker.getLiteratumDelivered(recordList, archive.getExport().getId(),
            true, true, isTrackByRecord() ? null : false, ps);
    }

    @Override
    protected Ml3gXmlAssets createAssets(String dbName, String cdNumber, int version) throws Exception {
        if (version == RecordEntity.VERSION_LAST) {
            return super.createAssets(dbName, cdNumber, version);
        }
        return LiteratumCDSRGenerator.createPreviousAssets(cdNumber, version, this, rps);
    }
}
