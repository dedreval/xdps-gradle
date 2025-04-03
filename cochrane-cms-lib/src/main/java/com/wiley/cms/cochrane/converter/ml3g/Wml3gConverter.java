package com.wiley.cms.cochrane.converter.ml3g;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractsHelper;
import com.wiley.cms.cochrane.converter.ConverterException;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.tes.util.Logger;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import javax.naming.NamingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 26.11.2013
 */
public abstract class Wml3gConverter {

    protected static final Logger LOG = Logger.getLogger(Wml3gConverter.class);
    private static final String SAVING_CONTENT_FAILE_MSG_TEMPLATE = "Failed to save WML3G content for record [%s]. %s";

    protected IRepository rps = RepositoryFactory.getRepository();
    protected IssueVO issueVO;
    protected BaseType dbType;
    protected Collection<Integer> recIds;
    protected ProcessingErrors processingErrors;
    protected int convertedCnt;
    protected int failedCnt;
    protected List<String> excludedRecNames = new ArrayList<>();
    protected int dbId;
    protected BaseType.PubInfo special = null;
    protected List<String> prevResults = null;
    protected String tmpFolder = null;

    protected boolean useExistingAssets = false;

    protected Wml3gConverter() {
    }

    public Wml3gConverter(int dbId, Wml3gConversionProcessPartParameters procPartParams) {
        issueVO = procPartParams.getIssueVO();
        this.dbId = dbId;
        setDbType(getDbName(dbId));
        parseProcessedEntities(procPartParams.getProcEntities());
    }

    protected abstract String getDbName(int dbId);

    protected final String getDbName() {
        return dbType.getId();
    }

    protected void setDbType(String dbName) {
        dbType = BaseType.find(dbName).get();
    }

    protected void parseProcessedEntities(String[] procEntities) {
        recIds = new ArrayList<>(procEntities.length);
        for (String procEntity : procEntities) {
            recIds.add(Integer.parseInt(procEntity));
        }
    }

    protected Collection<? extends IRecord> before() {
        LOG.debug(getConversionStateMessage("started", getDbName(), issueVO, recIds.size()));
        return getRecordVOs(recIds);
    }

    protected Set<Integer> after(List<RecordPublishVO> rpVOs) {

        Set<Integer> failedRecIds = getFailedRecordIds(rpVOs);
        failedCnt += failedRecIds.size();
        convertedCnt = recIds.size();
        LOG.debug(getConversionStateMessage("completed", getDbName(), issueVO, recIds.size()));

        return failedRecIds;
    }

    protected void printStarted(String dbName, BaseType.PubInfo pi) {
        LOG.debug(getConversionStateMessage("started for special options: " + pi.toString(), dbName,
                issueVO, recIds.size()));
    }

    public Set<Integer> execute() {
        Collection<? extends IRecord> recVOs = before();

        String dbName = getDbName();

        int insertTa = dbType.hasTranslationMl3g() ? dbType.getTranslationModeMl3g()
                : ITranslatedAbstractsInserter.MODE_NO_TA;
        boolean hasStandaloneWml3g = dbType.hasStandaloneWml3g();

        processingErrors = new ProcessingErrors(recVOs);

        List<RecordPublishVO> rpVOs = getRecordPublishVOs(recVOs);

        List<Integer> excludedIds = excludeRecordOnProcessed(rpVOs, dbName);

        Map<String, Ml3gXmlAssets> assetsByRecName = new HashMap<>(rpVOs.size());
        setAssets(assetsByRecName, recVOs, rpVOs, hasStandaloneWml3g);

        if (insertTa > ITranslatedAbstractsInserter.MODE_NO_TA) {
            insertTranslations(assetsByRecName, rpVOs, insertTa, true);
        }

        convert(assetsByRecName, rpVOs, hasStandaloneWml3g, null);

        save2Repository(issueVO, dbName, rpVOs, assetsByRecName);
        save2Db(rpVOs);
        sendErrors(getConversionIdMessage(issueVO, dbName));

        List<BaseType.PubInfo> list = getSpecMl3gOptions();
        if (list != null) {
            execute(list, recVOs, assetsByRecName, rpVOs, insertTa, hasStandaloneWml3g);
        }
        Set<Integer> failedIds = after(rpVOs);
        if (excludedIds != null) {
            failedIds.addAll(excludedIds);
        }
        return failedIds;
    }

    public final String getProcessingErrors() {
        return processingErrors == null ? null : processingErrors.getAllErrors();
    }

    private void execute(List<BaseType.PubInfo> list, Collection<? extends IRecord> recVOs,
        Map<String, Ml3gXmlAssets> assetsByRecName, List<RecordPublishVO> rpVOs, int insertTa,
        boolean hasStandaloneWml3g) {

        String dbName = getDbName();
        for (BaseType.PubInfo pi: list) {

            printStarted(dbName, pi);

            special = pi;

            assetsByRecName.clear();
            processingErrors.clearErrors();
            setAssets(assetsByRecName, recVOs, rpVOs, hasStandaloneWml3g);

            if (pi.getTranslationMode() != insertTa) {
                // need of full reconversion as translation's mode is different
                if (pi.hasTranslation()) {
                    insertTranslations(assetsByRecName, rpVOs, pi.getTranslationMode(), false);
                }
                convert(assetsByRecName, rpVOs, hasStandaloneWml3g, null);
            } else {
                convert(assetsByRecName, rpVOs, hasStandaloneWml3g, prevResults);
            }
            save2Repository(issueVO, dbName, rpVOs, assetsByRecName);
            sendErrors(getConversionIdMessage(issueVO, dbName));
        }
    }

    protected abstract String getConversionStateMessage(String state,
                                                        String dbName,
                                                        IssueVO issueVO,
                                                        int size);

    protected abstract Collection<? extends IRecord> getRecordVOs(Collection<Integer> recIds);

    protected List<RecordPublishVO> getRecordPublishVOs(Collection<? extends IRecord> recVOs) {
        List<RecordPublishVO> rpVOs = new ArrayList<>(recVOs.size());
        for (IRecord recVO : recVOs) {

            if (recVO.isWml3g() || !rps.isFileExistsQuiet(recVO.getRecordPath())) {
                excludedRecNames.add(recVO.getName());
                failedCnt++;
                continue;
            }
            RecordPublishVO rpVO = new RecordPublishVO();
            rpVO.setName(recVO.getName());
            rpVO.setRecordId(recVO.getId());
            rpVO.setSubTitle(recVO.getSubTitle());
            rpVO.setUnitStatusId(recVO.getUnitStatusId());
            rpVOs.add(rpVO);

            if (!recVO.insertTaFromEntire()) {
                rpVO.setDeliveryFileId(recVO.getDeliveryFileId());
                rpVO.setUnchanged(recVO.isUnchanged());
            }
        }
        return rpVOs;
    }

    private void setAssets(Map<String, Ml3gXmlAssets> assetsByRecName,
                             Collection<? extends IRecord> recVOs,
                             List<RecordPublishVO> rpVOs,
                             boolean hasStandaloneWml3g) {
        boolean missing = false;
        String dbName = getDbName();

        for (IRecord recVO : recVOs) {
            Ml3gXmlAssets assets = new Ml3gXmlAssets();
            assets.setXmlUri(recVO.getRecordPath());
            if (hasStandaloneWml3g) {
                StringBuilder errsContainer = processingErrors.getErrorsContainer(recVO.getName(), true);
                assets.setAssetsUris(getAssetsUris(recVO, dbName, errsContainer));
                missing = (missing || assets.getAssetsUris() == null);
            }
            assetsByRecName.put(recVO.getName(), assets);
        }
        if (missing) {
            for (RecordPublishVO rpVO : rpVOs) {
                Ml3gXmlAssets assets = assetsByRecName.get(rpVO.getName());
                if (assets == null || assets.getAssetsUris() == null) {
                    rpVO.setState(RecordPublishEntity.CONVERSION_FAILED);
                }
            }
        }
    }

    protected abstract List<String> getAssetsUris(IRecord recVO, String dbName, StringBuilder errs);


    private void insertTranslations(Map<String, Ml3gXmlAssets> assetsByName, List<RecordPublishVO> rpVOs,
                                    int mode, boolean hw) {
        ITranslatedAbstractsInserter taInserter;
        try {
            taInserter = CochraneCMSPropertyNames.lookup("TranslatedAbstractsInserter",
                    ITranslatedAbstractsInserter.class);
        } catch (NamingException e) {
            String err = String.format("Translated Abstracts Inserter initialization failed, %s", e);
            processingErrors.addCommonError(err);
            taInserter = null;
        }
        if (taInserter != null) {
            for (RecordPublishVO rpVO : rpVOs) {
                if (rpVO.getState() == RecordPublishEntity.CONVERSION_FAILED) {
                    continue;
                }

                Ml3gXmlAssets assets = assetsByName.get(rpVO.getName());
                try {
                    IRecord record = getRecord4TAInserter(rpVO);
                    String path2Ta = taInserter.getSourceForRecordWithInsertedAbstracts(record,
                            assets.getXmlUri(), issueVO.getId(), rpVO.getDeliveryFileId(), mode, hw);
                    assets.setXmlUri(path2Ta);
                    setJatsTranslations(rpVO.getDeliveryFileId(), record, assets);
                    addPathsWithFopTranslations(assets, record);

                } catch (Exception e) {
                    String err = String.format("Failed to insert translation to WML21 xml [%s], %s.",
                            assets.getXmlUri(), e);
                    processingErrors.addConversionError(rpVO.getName(), err);
                    rpVO.setState(RecordPublishEntity.CONVERSION_FAILED);
                }
            }
        } else {
            updateRecordPublishStata(rpVOs, RecordPublishEntity.CONVERSION_FAILED);
        }
    }

    private void setJatsTranslations(Integer dfId, IRecord record, Ml3gXmlAssets assets) throws Exception {
        Map<String, String> taResults = TranslatedAbstractsHelper.getJatsTranslationsExisted(
                issueVO.getId(), dfId, record, getVersion(), rps);
        if (!taResults.isEmpty()) {
            record.addLanguages(taResults.keySet());
            assets.setJatsTranslations(taResults);
        }
    }

    protected abstract void upPathsWithFopTranslations(Ml3gXmlAssets assets, IRecord record, String language,
                                                       boolean retracted);

    protected IRecord getRecord4TAInserter(RecordPublishVO rpVO) {
        return rpVO;
    }

    private void addPathsWithFopTranslations(Ml3gXmlAssets assets, IRecord record) {
        Collection<String> retractedLanguages = record.getRetractedLanguages();
        if (retractedLanguages != null) {
            for (String lang: retractedLanguages) {
                if (TranslatedAbstractVO.isMappedLanguage4Fop(lang)) {
                    upPathsWithFopTranslations(assets, record, lang, true);
                }
            }
        }
        Collection<String> languages = record.getLanguages();
        if (languages != null) {
            for (String lang : languages) {
                if (TranslatedAbstractVO.isMappedLanguage4Fop(lang)) {
                    upPathsWithFopTranslations(assets, record, lang, false);
                }
            }
        }
    }

    protected final IConverterAdapter convert(Map<String, Ml3gXmlAssets> assetsByRecName,
                                              List<RecordPublishVO> rpVOs,
                                              boolean hasStandaloneWml3g,
                                              List<String> results) {
        IConverterAdapter adapter;
        try {
            adapter = CochraneCMSPropertyNames.lookup("ConverterAdapter", IConverterAdapter.class);
        } catch (NamingException e) {
            String err = String.format("Converter initialization failed, %s", e);
            processingErrors.addCommonError(err);
            adapter = null;
        }

        if (adapter != null) {

            List<String> relativeAssetsUris = hasStandaloneWml3g ? getRelativeAssetsUris(rpVOs, assetsByRecName) : null;
            Map<String, String> errsBySrcPath = new HashMap<>();
            Map<String, RecordPublishVO> rpVOsBySrcPath = new HashMap<>(assetsByRecName.size());
            List<String> srcPaths = new ArrayList<>(assetsByRecName.size());

            for (RecordPublishVO rpVO : rpVOs) {
                if (rpVO.getState() != RecordPublishEntity.CONVERSION_FAILED) {
                    Ml3gXmlAssets assets = assetsByRecName.get(rpVO.getName());
                    rpVOsBySrcPath.put(assets.getXmlUri(), rpVO);
                    srcPaths.add(assets.getXmlUri());
                }
            }

            List<String> convResults = results != null ? results
                    : convertBatch(adapter, srcPaths, relativeAssetsUris, hasStandaloneWml3g, errsBySrcPath);

            if (!isSpecConversion()) {
                prevResults = convResults;
            }
            parseConversionResults(assetsByRecName, rpVOsBySrcPath, srcPaths, convResults, errsBySrcPath, adapter);

            logConversionErrors(rpVOsBySrcPath, errsBySrcPath);
        } else {
            updateRecordPublishStata(rpVOs, RecordPublishEntity.CONVERSION_FAILED);
        }
        return adapter;
    }

    private List<String> convertBatch(IConverterAdapter adapter,
                                      List<String> srcPaths,
                                      List<String> relativeAssetsUris,
                                      boolean hasStandaloneWml3g,
                                      Map<String, String> errsBySrcPath) {
        List<String> convResults;
        String dbName = getDbName();
        String srcFmt = getSourceFormat(dbName);
        String resFmt = getResultFormat(hasStandaloneWml3g);
        try {
            if (isCmrDb(dbName) || isCentralDb(dbName)) {
                int issueYear = CmsUtils.convertYearByDbName(issueVO.getYear(), issueVO.getNumber(), dbName);
                int issueNumb = CmsUtils.convertIssueByDbName(issueVO.getNumber(), dbName);

                convResults = adapter.convertBatch(srcPaths, srcFmt, resFmt, dbName, issueYear, issueNumb,
                        errsBySrcPath);
            } else {
                Map<String, String> params = adapter.getWml21ToWml3gConversionSpecParam(isSpecConversion());

                convResults = adapter.convertBatch(srcPaths, srcFmt, resFmt, dbName, params,
                        relativeAssetsUris, errsBySrcPath);
            }
        } catch (ConverterException e) {
            processingErrors.addCommonError(String.format("Conversion to WML3G failed, %s", e));
            convResults = Collections.emptyList();
        }
        return convResults;
    }

    private List<String> getRelativeAssetsUris(List<RecordPublishVO> rpVOs,
                                               Map<String, Ml3gXmlAssets> assetsByRecName) {
        List<String> uris = new ArrayList<>();
        for (RecordPublishVO rpVO : rpVOs) {
            if (rpVO.getState() != RecordPublishEntity.CONVERSION_FAILED) {
                List<String> assetsUris = assetsByRecName.get(rpVO.getName()).getAssetsUris();
                uris.addAll(Ml3gAssetsManager.getAssetsRelativeUris(assetsUris, rpVO.getName()));
            }
        }
        return uris;
    }

    private String getSourceFormat(String dbName) {
        if (isCentralDb(dbName) || isCmrDb(dbName)) {
            return IConverterAdapter.USW;
        } else {
            return IConverterAdapter.WILEY_ML21;
        }
    }

    private String getResultFormat(boolean hasStandaloneWml3g) {
        if (hasStandaloneWml3g) {
            return IConverterAdapter.WILEY_ML3G_SA;
        } else {
            return IConverterAdapter.WILEY_ML3G2;
        }
    }

    private void parseErrorResults(Map<String, RecordPublishVO> rpVOBySrcPath, String result) {
        processingErrors.addCommonError(result);
        updateRecordPublishStata(rpVOBySrcPath.values(), RecordPublishEntity.CONVERSION_FAILED);
    }

    private void parseConversionResults(Map<String, Ml3gXmlAssets> assetsByRecName,
                                        Map<String, RecordPublishVO> rpVOBySrcPath,
                                        List<String> srcPaths,
                                        List<String> convResults,
                                        Map<String, String> errsBySrcPath,
                                        IConverterAdapter adapter) {
        if (convResults.isEmpty() || convResults.size() != srcPaths.size()) {
            parseErrorResults(rpVOBySrcPath, "Conversion results are empty or have invalid values");

        } else {
            updateJatsTa(assetsByRecName, rpVOBySrcPath, srcPaths, convResults, errsBySrcPath, adapter);
        }
        setConversionErrors(rpVOBySrcPath, errsBySrcPath);
    }

    private void setConversionErrors(Map<String, RecordPublishVO> rpVOBySrcPath, Map<String, String> errsBySrcPath) {
        for (String path : errsBySrcPath.keySet()) {
            RecordPublishVO record = rpVOBySrcPath.get(path);
            if (record != null) {
                processingErrors.addConversionError(record.getName(), errsBySrcPath.get(path));
            }
        }
    }

    private void updateJatsTa(Map<String, Ml3gXmlAssets> assetsByRecName, Map<String, RecordPublishVO> rpVOBySrcPath,
                              List<String> srcPaths, List<String> convResults, Map<String, String> errsBySrcPath,
                              IConverterAdapter adapter) {
        for (int i = 0; i < srcPaths.size(); i++) {

            String srcPath = srcPaths.get(i);
            RecordPublishVO rpVO = rpVOBySrcPath.get(srcPath);
            Ml3gXmlAssets assets = assetsByRecName.get(rpVO.getName());

            if (errsBySrcPath.containsKey(srcPath)) {
                rpVO.setState(RecordPublishEntity.CONVERSION_FAILED);

            } else {
                assets.setXmlContent(convResults.get(i));
                rpVO.setState(RecordPublishEntity.CONVERTED);
                insertJatsTranslations(srcPath, rpVO, assets, errsBySrcPath, adapter);
            }
        }
    }

    private void insertJatsTranslations(String srcPath, RecordPublishVO rpVO, Ml3gXmlAssets assets,
            Map<String, String> errsBySrcPath, IConverterAdapter adapter) {
        Map<String, String> jatsTa = assets.getJatsTranslations();
        if (jatsTa != null) {
            try {
                assets.setXmlContent(adapter.convertSource(assets.getXmlContent(), IConverterAdapter.WILEY_ML3G_SEQ,
                        IConverterAdapter.WILEY_ML3G_JOINED, null, jatsTa, true));

            } catch (Throwable tr) {
                LOG.error(tr.getMessage());
                errsBySrcPath.put(srcPath, tr.getMessage());
                rpVO.setState(RecordPublishEntity.CONVERSION_FAILED);
            }
        }
    }

    protected void logConversionErrors(Map<String, RecordPublishVO> rpVOsBySrcPath, Map<String, String> errsBySrcPath) {
        logConversionErrors(rpVOsBySrcPath, errsBySrcPath, ActivityLogEntity.EntityLevel.RECORD);
    }

    protected final void logConversionErrors(Map<String, RecordPublishVO> rpVOsBySrcPath,
        Map<String, String> errsBySrcPath, ActivityLogEntity.EntityLevel entityLevel) {

        IActivityLog activityLog = AbstractManager.getActivityLogService();
        String userName = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);
        for (String srcPath : errsBySrcPath.keySet()) {
            RecordPublishVO record = rpVOsBySrcPath.get(srcPath);
            if (record != null) {
                activityLog.error(entityLevel, ILogEvent.CONVERSION_TO_3G_FAILED,
                        record.getRecordId(), record.getName(), userName, errsBySrcPath.get(srcPath));
            }
        }
    }

    protected abstract List<Integer> excludeRecordOnProcessed(List<RecordPublishVO> rpVOs, String dbName);

    private void save2Repository(IssueVO issueVO,
                                   String dbName,
                                   List<RecordPublishVO> rpVOs,
                                   Map<String, Ml3gXmlAssets> assetsByRecName) {
        for (RecordPublishVO rpVO : rpVOs) {
            if (rpVO.getState() != RecordPublishEntity.CONVERSION_FAILED) {
                Ml3gXmlAssets assets = assetsByRecName.get(rpVO.getName());
                boolean success = saveWml3gContent(issueVO, dbName, rpVO.getName(), assets.getXmlContent());
                if (success && !isSpecConversion() && CollectionUtils.isNotEmpty(assets.getAssetsUris())) {
                    success = saveWml3gAssets(issueVO, dbName, rpVO.getName(), assets.getAssetsUris());
                }

                if (!success) {
                    rpVO.setState(RecordPublishEntity.CONVERSION_FAILED);
                }
            }
        }
    }

    private boolean saveWml3gContent(IssueVO issueVO, String dbName, String recName, String content) {
        String path = getPath2Wml3gXml(issueVO, dbName, recName);
        String savingErrs = put2Repository(content, path);
        boolean success = (savingErrs == null);
        if (!success) {
            String err = String.format(SAVING_CONTENT_FAILE_MSG_TEMPLATE, recName, savingErrs);
            processingErrors.addRuntimeError(recName, err);
        }
        return success;
    }

    protected String put2Repository(String content, String path) {
        try {
            InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            rps.putFile(path, is);
            return null;

        } catch (IOException e) {
            return "Failed to save content by path [" + path + "], " + e;
        }
    }

    protected abstract String getPath2Wml3gXml(IssueVO issueVO, String dbName, String recName);

    protected boolean saveWml3gAssets(IssueVO issueVO, String dbName, String recName, List<String> assetsUris) {
        String path = getPath2Wml3gAssets(issueVO, dbName, recName);
        String savingErrs = put2Repository(Ml3gAssetsManager.getAssetsFileContent(assetsUris), path);

        boolean success = (savingErrs == null);
        if (!success) {
            String err = String.format(SAVING_CONTENT_FAILE_MSG_TEMPLATE, recName, savingErrs);
            processingErrors.addRuntimeError(recName, err);
        }

        return success;
    }

    protected abstract String getPath2Wml3gAssets(IssueVO issueVO, String dbName, String recName);

    protected abstract void save2Db(List<RecordPublishVO> rpVOs);

    protected abstract String getConversionIdMessage(IssueVO issueVO, String dbName);

    private void sendErrors(String qualifier) {
        sendErrorsToNotificationService(qualifier);
        sendErrorsToErrorLogService();
    }

    private void sendErrorsToNotificationService(String qualifier) {
        String errs = processingErrors.getAllErrors();
        if (errs.length() > 0) {
            MessageSender.sendWml3gConversion(qualifier, errs);
        }
    }

    protected void sendErrorsToErrorLogService() {
        IActivityLog logService = AbstractManager.getActivityLogService();
        int dbTypeId = dbType.getDbId();
        int issueNumb = (issueVO == null ? Constants.NO_ISSUE : issueVO.getFullNumber());
        Map<IRecord, String>  conversionErrors = processingErrors.getRecordConversionErrors();
        for (IRecord record : conversionErrors.keySet()) {
            logService.logRecordError(ILogEvent.CONVERSION_TO_3G_FAILED, record.getId(), record.getName(), dbTypeId,
                    issueNumb, conversionErrors.get(record));
        }
    }

    private Set<Integer> getFailedRecordIds(List<RecordPublishVO> rpVOs) {
        Set<Integer> ids = new HashSet<Integer>(rpVOs.size());
        for (RecordPublishVO rpVO : rpVOs) {
            if (rpVO.getState() == RecordPublishEntity.CONVERSION_FAILED) {
                ids.add(rpVO.getRecordId());
            }
        }

        return ids;
    }

    private boolean isCmrDb(String dbName) {
        return dbName.equals(CochraneCMSPropertyNames.getCmrDbName());
    }

    protected boolean isCentralDb(String dbName) {
        return dbName.equals(CochraneCMSPropertyNames.getCentralDbName());
    }

    protected Map<Integer, RecordPublishVO> getRecordIds(List<RecordPublishVO> rpVOs) {
        Map<Integer, RecordPublishVO> recordIds = new HashMap<>(rpVOs.size());
        rpVOs.forEach(rpVO -> recordIds.put(rpVO.getRecordId(), rpVO));
        return recordIds;
    }

    private void updateRecordPublishStata(Collection<RecordPublishVO> rpVOs, int state) {
        for (RecordPublishVO rpVO : rpVOs) {
            rpVO.setState(state);
        }
    }

    public int getConvertedCnt() {
        return convertedCnt;
    }

    public int getFailedCnt() {
        return failedCnt;
    }

    public static Wml3gConverter getConverter(int dbId, Wml3gConversionProcessPartParameters procPartParams) {
        Wml3gConverter converter;
        if (procPartParams.getContentLocation() == ContentLocation.ISSUE) {
            converter = new Wml3gConverterIssueDb(dbId, procPartParams);
        } else if (procPartParams.getContentLocation() == ContentLocation.ENTIRE) {
            converter = new Wml3gConverterEntire(dbId, procPartParams);
        } else {
            converter = new Wml3gConverterPrevious(dbId, procPartParams);
        }
        return converter;
    }

    public List<String> getExcludedRecordNames() {
        return excludedRecNames;
    }

    public void setTmpFolder(String path) {
        tmpFolder = path;
    }

    protected boolean isSpecConversion() {
        return special != null;
    }

    private List<BaseType.PubInfo> getSpecMl3gOptions() {
        return null;
    }

    protected int getVersion() {
        return RecordEntity.VERSION_SHADOW;
    }

    public void setUseExistingAssets(boolean value) {
        useExistingAssets = value;
    }

    /**
     *
     */
    static class ProcessingErrors {

        static final String ERROR_LINES_SPLITTER = ";\n";

        StringBuilder commonErrors;
        Map<String, RecordErrorContainer> recordErrorContainers;

        ProcessingErrors(Collection<? extends IRecord> records) {
            commonErrors = new StringBuilder();
            recordErrorContainers = createRecordErrorContainers(records);
        }

        private Map<String, RecordErrorContainer> createRecordErrorContainers(Collection<? extends IRecord> records) {
            Map<String, RecordErrorContainer> containers = new HashMap<String, RecordErrorContainer>(records.size());
            for (IRecord record : records) {
                RecordErrorContainer container = new RecordErrorContainer(record);
                containers.put(record.getName(), container);
            }
            return containers;
        }

        void addCommonError(String error) {
            addErrorToContainer(error, commonErrors);
        }

        void addRuntimeError(String recordName, String error) {
            addError(recordName, error, true);
        }

        void addConversionError(String recordName, String error) {
            addError(recordName, error, false);
        }

        void addError(String recordName, String error, boolean runtime) {
            StringBuilder errorsContainer = getErrorsContainer(recordName, runtime);
            addErrorToContainer(error, errorsContainer);
        }

        void addErrorToContainer(String error, StringBuilder errorsContainer) {
            if (errorsContainer.length() > 0) {
                errorsContainer.append(ERROR_LINES_SPLITTER);
            }
            errorsContainer.append(error);
        }

        String getAllErrors() {
            StringBuilder errors = new StringBuilder();
            errors.append(commonErrors);
            for (String recordName : recordErrorContainers.keySet()) {
                String recordErrors = recordErrorContainers.get(recordName).getAllErrors();
                if (recordErrors.length() > 0 && errors.length() > 0) {
                    errors.append(ERROR_LINES_SPLITTER);
                }
                errors.append(recordErrors);
            }
            return errors.toString();
        }

        Map<IRecord, String> getRecordConversionErrors() {
            Map<IRecord, String> recordConversionErrors = new HashMap<>();
            for (String recordName : recordErrorContainers.keySet()) {
                RecordErrorContainer errorContainer = recordErrorContainers.get(recordName);
                if (errorContainer.conversionErrors.length() > 0) {
                    recordConversionErrors.put(errorContainer.record, errorContainer.conversionErrors.toString());
                }
            }
            return recordConversionErrors;
        }

        void clearErrors() {
            commonErrors = new StringBuilder();
            clearRecordErrorContainers();
        }

        private void clearRecordErrorContainers() {
            for (RecordErrorContainer recordErrorContainer : recordErrorContainers.values()) {
                recordErrorContainer.clear();
            }
        }

        StringBuilder getErrorsContainer(String recordName, boolean runtime) {
            RecordErrorContainer container = recordErrorContainers.get(recordName);
            return (runtime ? container.runtimeErrors : container.conversionErrors);
        }
    }

    /**
     *
     */
    static class RecordErrorContainer {

        final IRecord record;
        StringBuilder runtimeErrors;
        StringBuilder conversionErrors;

        RecordErrorContainer(IRecord record) {
            this.record = record;
            this.runtimeErrors = new StringBuilder();
            this.conversionErrors = new StringBuilder();
        }

        void clear() {
            this.runtimeErrors = new StringBuilder();
            this.conversionErrors = new StringBuilder();
        }

        String getAllErrors() {
            boolean hasRuntime = runtimeErrors.length() > 0;
            boolean hasConversion = conversionErrors.length() > 0;
            StringBuilder errors = new StringBuilder(record.getRecordPath());
            errors.append(":\n").append(runtimeErrors);
            if (hasRuntime && hasConversion) {
                errors.append(ProcessingErrors.ERROR_LINES_SPLITTER);
            }
            errors.append(conversionErrors);

            return hasRuntime || hasConversion ? errors.toString() : StringUtils.EMPTY;
        }
    }
}
