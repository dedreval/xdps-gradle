package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.IContentRoom;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishChecker;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gXmlAssets;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.converter.services.RevmanMetadataHelper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class LiteratumCDSRGenerator extends LiteratumWRGenerator {

    public LiteratumCDSRGenerator(ClDbVO db) {
        super(db);
    }


    @Override
    protected String getPubName(String cdNumber, int pubNumber) {
        return RevmanMetadataHelper.buildPubName(cdNumber, pubNumber);
    }

    @Override
    protected IPublishChecker createPublishChecker(List<RecordWrapper> recordList) {
        return PublishChecker.getLiteratumDelivered(recordList, archive.getExport().getId(),
            true, true, isTrackByRecord() ? null : false, ps);
    }

    @Override
    protected Ml3gXmlAssets createAssets(int issueId, String dbName, String cdNumber, int version,
                                         boolean outdated) throws Exception {
        if (version == RecordEntity.VERSION_LAST) {
            return super.createAssets(issueId, dbName, cdNumber, RecordEntity.VERSION_LAST, outdated);
        }
        return createPreviousAssets(cdNumber, version, this, rps);
    }

    static Ml3gXmlAssets createPreviousAssets(String cdNumber, int version, IContentRoom room,
                                              IRepository rep) throws Exception {
        Ml3gXmlAssets  assets = new Ml3gXmlAssets();
        StringBuilder errs = new StringBuilder();

        setBaseAssetsUri(FilePathBuilder.ML3G.getPathToPreviousMl3gRecord(version, cdNumber), assets, rep);
        assets.setAssetsUris(PublishHelper.getPublishContentUris(room, version, cdNumber, false, false,
                ContentLocation.PREVIOUS, errs));

        if (assets.getAssetsUris() == null) {
            throw new Exception(errs.toString());
        }

        return assets;
    }
}
