package com.wiley.cms.cochrane.converter.ml3g;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.tes.util.Extensions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 07.04.2014
 */
public class Wml3gConverterPrevious extends Wml3gConverter {

    private List<String> recNames;
    private final int version;

    private Map<String, ? extends IRecord> records;

    public Wml3gConverterPrevious(List<String> recNames, int version) {
        this.recNames = recNames;
        this.version = version;
        setDbType(CochraneCMSPropertyNames.getCDSRDbName());
    }

    public Wml3gConverterPrevious(Integer issueId, int fullIssueNumber, List<String> recNames, int version) {
        this(recNames, version);
        issueVO = new IssueVO(issueId, CmsUtils.getYearByIssueNumber(fullIssueNumber),
                CmsUtils.getIssueByIssueNumber(fullIssueNumber), null);
    }

    public Wml3gConverterPrevious(int dbId, Wml3gConversionProcessPartParameters procPartParams) {
        super(dbId, procPartParams);
        version = procPartParams.getVersion();
    }

    public void setRecords(Map<String, ? extends IRecord> records) {
        this.records = records;
    }

    protected String getDbName(int dbId) {
        return CochraneCMSPropertyNames.getCDSRDbName();
    }

    protected void parseProcessedEntities(String[] procEntities) {
        recNames = Arrays.asList(procEntities);
    }

    protected ContentLocation getContentLocation() {
        return ContentLocation.PREVIOUS;
    }

    @Override
    protected Collection<? extends IRecord> before() {
        LOG.debug(getConversionStateMessage("started", getDbName(), issueVO, recNames.size()));

        return getRecordVOs(recNames);
    }

    @Override
    protected Set<Integer> after(List<RecordPublishVO> rpVOs) {
        convertedCnt = recNames.size();
        failedCnt = getFailedRecordsCount(rpVOs);

        LOG.debug(getConversionStateMessage("completed", getDbName(), issueVO, recNames.size()));

        Map<IRecord, String> conversionErrors = processingErrors.getRecordConversionErrors();
        if (conversionErrors != null && !conversionErrors.isEmpty() && records != null) {
            Set<Integer> ret = new HashSet<>(conversionErrors.size());
            conversionErrors.entrySet().forEach(r -> ret.add(records.get(r.getKey().getName()).getId()));
            return ret;
        }
        return Collections.emptySet();
    }

    @Override
    protected void printStarted(String dbName, BaseType.PubInfo pi) {
        LOG.debug(getConversionStateMessage("started for special options: " + pi.toString(), dbName,
            issueVO, recNames.size()));
    }

    protected String getConversionStateMessage(String state, String dbName, IssueVO issueVO, int size) {
        return String.format("Conversion to WML3G %s for {%s} record(s), version {%s}; database name [%s]",
                state,
                size,
                version,
                dbName);
    }

    private List<RecordVO> getRecordVOs(List<String> recNames) {
        List<RecordVO> recVOs = new ArrayList<>(recNames.size());
        boolean cdsr = dbType.getId().equals(CochraneCMSPropertyNames.getCDSRDbName());

        for (String recName : recNames) {
            ICDSRMeta meta = cdsr
                    ? ResultStorageFactory.getFactory().getInstance().findPreviousMetadata(recName, version) : null;
            // CPP5-687
            if (meta != null && meta.isJats()) {
                excludedRecNames.add(recName);
                failedCnt++;
                continue;
            }
            RecordVO recVO = new RecordVO();
            recVO.setName(recName);

            recVO.setRecordPath(FilePathCreator.getPreviousSrcPath(recName, version));
            recVOs.add(recVO);
        }

        return recVOs;
    }

    @Override
    protected IRecord getRecord4TAInserter(RecordPublishVO rpVO) {
        return records != null ? records.get(rpVO.getName()) : rpVO;
    }

    @Override
    protected Collection<? extends IRecord> getRecordVOs(Collection<Integer> recIds) {
        return records != null ? records.values() : null;
    }

    @Override
    protected List<RecordPublishVO> getRecordPublishVOs(Collection <? extends IRecord> recVOs) {
        List<RecordPublishVO> rpVOs = new ArrayList<>(recVOs.size());
        for (IRecord recVO : recVOs) {

            RecordPublishVO rpVO = new RecordPublishVO();
            rpVO.setName(recVO.getName());
            rpVO.setSubTitle(recVO.getSubTitle());
            rpVO.setUnitStatusId(recVO.getUnitStatusId());
            rpVOs.add(rpVO);
        }

        return rpVOs;
    }

    protected List<String> getAssetsUris(IRecord recVO, String dbName, StringBuilder errs) {
        return Ml3gAssetsManager.collectAssets(dbName, recVO, ContentLocation.PREVIOUS, version, errs);
    }

    @Override
    protected void upPathsWithFopTranslations(Ml3gXmlAssets assets, IRecord record, String lang, boolean retracted) {
        String fopTaPathPrevious = rps.getRealFilePath(FilePathBuilder.PDF.getPathToPreviousPdfFopTAWithSuffix(
                version, record.getName(), lang, Constants.PDF_ABSTRACT_SUFFIX));
        List<String> assetsUris = assets.getAssetsUris();
        if (retracted) {
            assetsUris.remove(fopTaPathPrevious);

        } else if (!assetsUris.contains(fopTaPathPrevious)) {
            assetsUris.add(fopTaPathPrevious);
        }
    }

    @Override
    protected String getPath2Wml3gXml(IssueVO issueVO, String dbName, String recName) {
        return tmpFolder != null ? tmpFolder + recName + Extensions.XML
                : (isSpecConversion() ? FilePathBuilder.ML3G.getPathToPreviousMl3gRecordByPostfix(version, recName,
                special.getSourcePostfix()) : FilePathCreator.getPreviousMl3gXmlPath(recName, version));
    }

    @Override
    protected void logConversionErrors(Map<String, RecordPublishVO> rpVOsBySrcPath, Map<String, String> errsBySrcPath) {
        logConversionErrors(rpVOsBySrcPath, errsBySrcPath, ActivityLogEntity.EntityLevel.HISTORY);
    }

    @Override
    protected List<Integer> excludeRecordOnProcessed(List<RecordPublishVO> rpVOs, String dbName) {
        return null;
    }

    @Override
    protected String getPath2Wml3gAssets(IssueVO issueVO, String dbName, String recName) {
        return tmpFolder != null ? tmpFolder + recName + Extensions.ASSETS
                : FilePathCreator.getPreviousMl3gAssetsPath(recName, version);
    }

    protected void save2Db(List<RecordPublishVO> rpVOs) {
    }

    protected String getConversionIdMessage(IssueVO issueVO, String dbName) {
        return dbName + "; version " + version;
    }

    @Override
    protected void sendErrorsToErrorLogService() {
    }

    @Override
    protected int getVersion() {
        return version;
    }

    private int getFailedRecordsCount(List<RecordPublishVO> rpVOs) {
        int cnt = 0;
        for (RecordPublishVO rpVO : rpVOs) {
            if (rpVO.getState() == RecordPublishEntity.CONVERSION_FAILED) {
                cnt++;
            }
        }

        return cnt;
    }
}
