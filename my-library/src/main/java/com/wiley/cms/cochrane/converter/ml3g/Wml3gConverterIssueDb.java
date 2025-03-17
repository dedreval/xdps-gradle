package com.wiley.cms.cochrane.converter.ml3g;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.RepeatableOperation;
import com.wiley.tes.util.Extensions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 27.11.2013
 */
public class Wml3gConverterIssueDb extends Wml3gConverter {

    protected final IRecordStorage recs = AbstractManager.getRecordStorage();

    private Map<Integer, ? extends IRecord> records;

    public Wml3gConverterIssueDb(IssueVO issueVO, int dbId, String dbName, Collection<Integer> recIds) {
        this(issueVO, dbId, dbName);
        this.recIds = recIds;
    }

    public Wml3gConverterIssueDb(Integer issueId, int fullIssueNumber, int dbId, String dbName) {
        this(new IssueVO(issueId, CmsUtils.getYearByIssueNumber(fullIssueNumber), CmsUtils.getIssueByIssueNumber(
                fullIssueNumber), null), dbId, dbName);
    }

    public Wml3gConverterIssueDb(int dbId, Wml3gConversionProcessPartParameters procPartParams) {
        super(dbId, procPartParams);
    }

    public Wml3gConverterIssueDb(IssueVO issueVO, int dbId, String dbName) {
        this.issueVO = issueVO;
        this.dbId = dbId;
        setDbType(dbName);
    }

    public void setRecords(Map<Integer, ? extends IRecord> records) {
        this.records = records;
        this.recIds = records.keySet();
    }

    protected String getDbName(int dbId) {
        return new DbWrapper(dbId).getTitle();
    }

    protected ContentLocation getContentLocation() {
        return ContentLocation.ISSUE;
    }

    protected String getConversionStateMessage(String state, String dbName, IssueVO issueVO, int size) {
        return String.format("Conversion to WML3G %s for {%s} record(s); issue [%s]; database name [%s]",
                state,
                size,
                issueVO,
                dbName);
    }

    @Override
    protected Collection<? extends IRecord> getRecordVOs(Collection<Integer> recIds) {
        return records != null ? records.values() : recs.getRecordVOsByIds(recIds, dbType.hasTranslationMl3g());
    }

    @Override
    protected IRecord getRecord4TAInserter(RecordPublishVO rpVO) {
        return records != null ? records.get(rpVO.getRecordId()) : rpVO;
    }

    @Override
    protected List<String> getAssetsUris(IRecord recVO, String dbName, StringBuilder errs) {
        List<String> assets = null;
        if (useExistingAssets) {
            assets = Ml3gAssetsManager.getAssetsUris(dbName, Constants.UNDEF, recVO.getName(), Constants.UNDEF,
                    ContentLocation.ISSUE, new StringBuilder());
        }
        if (assets == null) {
            assets = Ml3gAssetsManager.collectIssueAssets(issueVO.getId(), dbName, recVO, errs);
            if (dbType.canPdfFopConvert()) {
                checkCommonPDFLink(recVO.getName(), assets);
            }
        }
        return assets;
    }

    private void checkCommonPDFLink(String cdNumber, List<String> assets) {
        for (String asset: assets) {
            if (asset.endsWith(Extensions.PDF)) {
                return;
            }
        }  // add a link to the common PDF
        assets.add(FilePathBuilder.PDF.getPathToEntirePdfFopRecord(dbType.getId(), cdNumber));
    }

    @Override
    protected void upPathsWithFopTranslations(Ml3gXmlAssets assets, IRecord record, String lang, boolean retracted) {
        String fopTaPath = FilePathBuilder.PDF.getPathToIssuePdfFopTAWithSuffix(
                issueVO.getId(), dbType.getId(), record.getName(), lang, Constants.PDF_ABSTRACT_SUFFIX);
        String fopTaPathEntire = FilePathBuilder.PDF.getPathToEntirePdfFopTAWithSuffix(
                dbType.getId(), record.getName(), lang, Constants.PDF_ABSTRACT_SUFFIX);
        List<String> assetsUris = assets.getAssetsUris();
        if (retracted) {
            assetsUris.remove(fopTaPath);
            assetsUris.remove(fopTaPathEntire);

        } else if (!assetsUris.contains(fopTaPath) && !assetsUris.contains(fopTaPathEntire)) {
            assets.getAssetsUris().add(fopTaPath);
        }
    }

    @Override
    protected List<Integer> excludeRecordOnProcessed(List<RecordPublishVO> rpVOs, String dbName) {
        // CPP5-687
        boolean cdsr = dbName.equals(CochraneCMSPropertyNames.getCDSRDbName());
        List<Integer> excludedIds = null;
        if (cdsr) {
            Iterator<RecordPublishVO> it = rpVOs.iterator();
            while (it.hasNext()) {
                RecordPublishVO rpVO = it.next();
                String cdNumber = rpVO.getName();
                ICDSRMeta meta = CmsUtils.isScheduledIssue(issueVO.getId())
                        ? ResultStorageFactory.getFactory().getInstance().findMetadataToIssue(
                                Constants.SPD_ISSUE_NUMBER, cdNumber, false)
                        : ResultStorageFactory.getFactory().getInstance().findLatestMetadata(cdNumber, false);
                if (meta == null || !meta.isJats()) {
                    continue;
                }
                excludedRecNames.add(cdNumber);
                failedCnt++;
                it.remove();
                excludedIds = excludedIds == null ? new ArrayList<>() : excludedIds;
                excludedIds.add(rpVO.getRecordId());
            }
        }
        return excludedIds;
    }

    @Override
    protected String getPath2Wml3gXml(IssueVO issueVO, String dbName, String recName) {
        return tmpFolder != null ? tmpFolder + recName + Extensions.XML
            : (isSpecConversion() ? FilePathBuilder.ML3G.getPathToMl3gRecordByPostfix(issueVO.getId(), dbName, recName,
                special.getSourcePostfix()) : FilePathCreator.getFilePathForMl3gXml(issueVO.getId(), dbName, recName));
    }

    @Override
    protected String getPath2Wml3gAssets(IssueVO issueVO, String dbName, String recName) {
        return tmpFolder != null ? tmpFolder + recName + Extensions.ASSETS
                : FilePathCreator.getFilePathForMl3gAssets(issueVO.getId(), dbName, recName);
    }

    @Override
    protected void save2Db(final List<RecordPublishVO> rpVOs) {
        if (rpVOs.isEmpty())  {
            return;
        }
        final Map<Integer, RecordPublishVO> recIds = getRecordIds(rpVOs);
        RepeatableOperation ro = new RepeatableOperation() {
            public void perform() {
                recs.deleteRecordPublishByRecordIds(recIds.keySet());
                recs.persistRecordPublish(recIds);
            }
        };
        ro.performOperation();
    }

    protected String getConversionIdMessage(IssueVO issueVO, String dbName) {
        return issueVO + "; " + dbName;
    }
}
