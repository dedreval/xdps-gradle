package com.wiley.cms.cochrane.converter.ml3g;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishVO;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 27.11.2013
 */
public class Wml3gConverterEntire extends Wml3gConverter {

    private final IEntireDBStorage edbs = AbstractManager.getEntireDBStorage();

    public Wml3gConverterEntire(int dbId, Wml3gConversionProcessPartParameters procPartParams) {
        super(dbId, procPartParams);
        setUseExistingAssets(CochraneCMSProperties.getBoolProperty(
                "cms.cochrane.wml3g_conversion.entire.use_existing_assets", false));
    }

    @Override
    protected void logConversionErrors(Map<String, RecordPublishVO> rpVOsBySrcPath, Map<String, String> errsBySrcPath) {
        logConversionErrors(rpVOsBySrcPath, errsBySrcPath, ActivityLogEntity.EntityLevel.ENTIRE);
    }

    protected String getDbName(int dbId) {
        return BaseType.getDbName(dbId);
    }

    protected ContentLocation getContentLocation() {
        return ContentLocation.ENTIRE;
    }

    protected String getConversionStateMessage(String state, String dbName, IssueVO issueVO, int size) {
        return String.format("Conversion to WML3G %s for {%s} record(s); database name [%s]",
                state,
                size,
                dbName);
    }

    @Override
    protected Collection<? extends IRecord> getRecordVOs(Collection<Integer> recIds) {
        List<EntireDBEntity> entities = edbs.getRecordsByIds(recIds);
        List<RecordVO> recVOs = new ArrayList<RecordVO>(entities.size());

        for (EntireDBEntity entity : entities) {
            recVOs.add(new RecordVO(entity));
        }

        return recVOs;
    }

    protected List<String> getAssetsUris(IRecord recVO, String dbName, StringBuilder errs) {
        List<String> assetsUris = null;
        if (useExistingAssets) {
            assetsUris = Ml3gAssetsManager.getAssetsUris(dbName, Constants.UNDEF, recVO.getName(), Constants.UNDEF,
                    ContentLocation.ENTIRE, new StringBuilder());
        }
        return assetsUris != null ? assetsUris
                : Ml3gAssetsManager.collectAssets(dbName, recVO, ContentLocation.ENTIRE, Constants.UNDEF, errs);
    }

    @Override
    protected void upPathsWithFopTranslations(Ml3gXmlAssets assets, IRecord record, String lang, boolean retracted) {
        String fopTaPathEntire = rps.getRealFilePath(FilePathBuilder.PDF.getPathToEntirePdfFopTAWithSuffix(
                dbType.getId(), record.getName(), lang, Constants.PDF_ABSTRACT_SUFFIX));
        List<String> assetsUris = assets.getAssetsUris();
        if (retracted) {
            assetsUris.remove(fopTaPathEntire);

        } else if (!assetsUris.contains(fopTaPathEntire)) {
            assetsUris.add(fopTaPathEntire);
        }
    }

    protected List<Integer> excludeRecordOnProcessed(List<RecordPublishVO> rpVOs, String dbName) {
        //Date date = new Date();
        if (isCdsrDb(dbName)) {
            Iterator<RecordPublishVO> it = rpVOs.iterator();
            while (it.hasNext()) {
                RecordPublishVO rpVO = it.next();
                String cdNumber = rpVO.getName();
                /*File file = new File(rps.getRealFilePath(getPath2Wml3gXml(null, dbName, cdNumber)));
                IRecordCache recCache = getRecordCocge();
                if (recCache != null && recCache.containsRecord(cdNumber)
                        || (file.exists() && file.lastModified() > date.getTime())) {
                    excludedRecNames.add(cdNumber);
                    failedCnt++;
                    it.remove();
                    continue;
                }*/
                // CPP5-687
                ICDSRMeta meta = ResultStorageFactory.getFactory().getInstance().findLatestMetadata(cdNumber, false);
                if (meta != null && meta.isJats()) {
                    excludedRecNames.add(cdNumber);
                    failedCnt++;
                    it.remove();
                }
            }
        }
        return null;
    }

    private boolean isCdsrDb(String dbName) {
        return dbName.equals(CochraneCMSPropertyNames.getCDSRDbName());
    }

    protected String getPath2Wml3gXml(IssueVO issueVO, String dbName, String recName) {
        return isSpecConversion() ? FilePathBuilder.ML3G.getPathToEntireMl3gRecordByPostfix(dbName, recName,
                special.getSourcePostfix()) : FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName);
        //return isInsertTaMode() ? FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName)
        //        : FilePathBuilder.ML3G.getPathToEntireMl3gNoTARecord(dbName, recName);
    }

    protected String getPath2Wml3gAssets(IssueVO issueVO, String dbName, String recName) {
        return FilePathCreator.getFilePathForEntireMl3gAssets(dbName, recName);
    }

    protected void save2Db(List<RecordPublishVO> rpVOs) {
        if (!rpVOs.isEmpty()) {
            Map<Integer, RecordPublishVO> recIds = getRecordIds(rpVOs);

            edbs.deleteRecordPublish(recIds.keySet());
            edbs.persistRecordPublish(recIds);
        }
    }

    protected String getConversionIdMessage(IssueVO issueVO, String dbName) {
        return dbName + " entire";
    }

    @Override
    protected int getVersion() {
        return RecordEntity.VERSION_LAST;
    }
}
