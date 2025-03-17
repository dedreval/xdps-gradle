package com.wiley.cms.cochrane.process.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jms.Queue;

import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchiePackage;
import com.wiley.cms.cochrane.cmanager.contentworker.ContentHelper;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsHelper;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.meshterm.IMeshtermStorage;
import com.wiley.cms.cochrane.cmanager.data.meshterm.MeshtermStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.converter.ml3g.JatsMl3gAssetsManager;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConverter;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConverterIssueDb;
import com.wiley.cms.cochrane.converter.ml3g.Wml3gConverterPrevious;
import com.wiley.cms.cochrane.meshtermmanager.JatsMeshtermManager;
import com.wiley.cms.cochrane.process.BaseAcceptQueue;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.test.ContentChecker;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.IProcessStats;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessSupportVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Property;
import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 *
 */
public class JatsConversionHandler extends ContentHandler<DbHandler, BaseAcceptQueue, Map<String, IRecord>> {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(JatsConversionHandler.class);
    
    private Map<String, IRecord> initialRecords;

    private int convertTA = ConversionRecordHandler.WITH_SPECIAL;

    public JatsConversionHandler() {
    }

    public JatsConversionHandler(DbHandler handler, int reconvertTA) {
        super(handler);
        setConvertTranslations(reconvertTA);
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + 1;
    }

    @Override
    protected int getFunctionalParamCount() {
        return 1;
    }

    @Override
    protected void init(String... params) throws ProcessException {
        super.init(params);
        setConvertTranslations(params[super.getParamCount()]);
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, convertTA);
    }

    @Override
    protected Class<DbHandler> getTClass() {
        return DbHandler.class;
    }

    @Override
    public void onMessage(ProcessVO pvo, ProcessPartVO processPart) throws Exception {

        ContentLocation location = getContentHandler().getContentLocation();
        boolean writeStats = pvo.getType().canWriteStats();
        Context context = new Context(writeStats, isConvertTA(), CochraneCMSPropertyNames.lookupFlowLogger());
        boolean cdsrImport = hasDeliveryFile() && CmsUtils.isImportIssue(getContentHandler().getIssueId());
        Map<ContentLocation, Collection<? extends IRecord>> records = getRecords(processPart, location, context,
                cdsrImport);
        if (records.isEmpty()) {
            LOG.warn("process part[%d] [%d] - no records found", processPart.getId(), processPart.parentId);
            setResults(processPart, null, context);
            return;
        }

        Map<String, IRecord> results = new HashMap<>();
        String topics = context.flLogger.getRecordCache().getTopicsSummary();
        boolean onlyTa = isOnlyConvertTA();

        for (Map.Entry<ContentLocation, Collection<? extends IRecord>> entry: records.entrySet()) {
            ContentLocation cl = entry.getKey();
            Collection<? extends IRecord> list = entry.getValue();
            for (IRecord record: list) {
                if (!checkJatsAvailable(record, context)) {
                    continue;
                }
                if (onlyTa ? convertTranslations(record, cl, context)
                        : convert(record, cl, topics, context, cdsrImport)) {
                    results.put(RevmanMetadataHelper.buildPubName(record.getName(), record.getPubNumber()), record);
                }
            }
        }
        if (hasDeliveryFile()) {
            //boolean aut = DeliveryPackage.isAut(getContentHandler().getDfName());
            CochraneCMSBeans.getRenderManager().resetJatsRendering(results.values());
            //if (!cdsrImport) {
            //    results.keySet().forEach(pubName -> context.flLogger.onProductConverted(
            //            RevmanMetadataHelper.parseCdNumber(pubName), aut));
            //}
        }
        setResults(processPart, results, context);
        String packageName = StringUtils.isNotBlank(getContentHandler().getDfName())
                ? getContentHandler().getDfName() : "N/A";
        context.sendConversionErrors(getContentHandler().getDbName(),
                getContentHandler().getIssue(), location, packageName);
    }

    private boolean checkJatsAvailable(IRecord record, Context context) {
        if (record.getRecordPath().contains(JatsPackage.JATS_FOLDER) || (!record.isJats() && hasDeliveryFile())) {
            return true;
        }
        if (record.isJats()) {
            context.addError(record, "no JATS content found");
        } else  {
            context.stats.addNotJatsArticle();
            context.addError(record, NO_JATS_META_FOUND);
        }
        return false;
    }

    @Override
    protected void onStartAsync(ProcessVO pvo, List<ProcessPartVO> inputData, CMSProcessManager manager, Queue queue)
            throws ProcessException {

        Map<String, IRecord> startRecords;
        if (inputData != null) {
            startRecords = takeResult(inputData);
        } else {
            startRecords = initialRecords;
            if (!pvo.getType().isCreatePartsBefore()) {
                checkInitialRecords(pvo, startRecords, true, IDeliveryFileStatus.STATUS_RND_NOT_STARTED);
            }
        }

        List<ProcessPartVO> list = startRecords != null && !startRecords.isEmpty()
                        ? createProcessParts(pvo, startRecords.values()) : null;
        super.onStartAsync(pvo, list, manager, queue);
    }

    @Override
    public void acceptResult(Map<String, IRecord> records) {
        initialRecords = records;
    }

    @Override
    public void acceptResult(PackageUnpackHandler fromHandler, ProcessVO from) {
        initialRecords = fromHandler.takeResult(from);
    }

    private static void setResults(ProcessPartVO processPart, Map<String, IRecord> results, Context context) {
        processPart.setOutput(results);

        if (!context.stats.isEmpty()) {
            processPart.setStats(context.stats);
        }
    }

    private Map<ContentLocation, Collection<? extends IRecord>> getRecords(ProcessPartVO processPart,
            ContentLocation cl, IErrorCollector context, boolean cdsrImport) throws Exception {

        Map<ContentLocation, Collection<? extends IRecord>> ret = Collections.emptyMap();
        boolean entire = !cdsrImport && getContentHandler().isEntire();
        List<Integer> ids = getIdsParam(processPart);

        if (!entire && !cdsrImport) {
            List<IRecord> list = RecordStorageFactory.getFactory().getInstance().getTinyRecords(ids);
            if (!list.isEmpty()) {
                ret = new EnumMap<>(ContentLocation.class);
                ret.put(cl, list);
            }
            return ret;
        }
        return cdsrImport ? checkMetadataToImport(getContentHandler().getDbName(), ids, cl, context)
            : checkEntireMetadata(getContentHandler().getDbName(), ids, cl, isOnlyConvertTA(), true, context);
    }

    private Map<ContentLocation, Collection<? extends IRecord>> checkMetadataToImport(String dbName,
        Collection<Integer> ids, ContentLocation cl, IErrorCollector context) {

        List<IRecord> list = RecordStorageFactory.getFactory().getInstance().getTinyRecords(ids);
        if (list.isEmpty()) {
            return Collections.emptyMap();
        }
        Collection<IRecord> prevList = new ArrayList<>();
        Iterator<IRecord> it = list.iterator();
        while (it.hasNext()) {
            IRecord record = it.next();
            Integer historyNumber = record.getHistoryNumber();
            if (historyNumber != null && historyNumber > RecordEntity.VERSION_LAST) {
                it.remove();
                prevList.add(record);
            }
        }
        return collectStartRecords(dbName, list, prevList, cl, ContentLocation.ISSUE_PREVIOUS, context);
    }

    private boolean convertTranslations(IRecord record, ContentLocation cl, Context context) {
        try {
            prepareTranslations(record, cl, context, false);
            return true;

        } catch (Throwable th) {
            Throwable cause = th.getCause();
            if (cause == null) {
                cause = th;
            }
            String error = cause.getMessage();
            logWml3gError(DatabaseEntity.CDSR_KEY, getContentHandler().getIssue(), record.getId(), record.getName(),
                    error, ActivityLogFactory.getFactory().getInstance());
            context.addError(record, error);
            return false;
        }
    }

    private Wml3gConverter getLegacyConverter(IRecord record, Context context) {
        Map<Integer, IRecord> recs = new HashMap<>();
        recs.put(record.getId(), record);
        Wml3gConverterIssueDb ret = context.wml3gConverter(getContentHandler());
        ret.setRecords(recs);
        return ret;
    }

    private Wml3gConverter getLegacyConverter(IRecord record) {
        List<String> recsList = new ArrayList<>();
        recsList.add(record.getName());
        Map<String, IRecord> recs = new HashMap<>();
        recs.put(record.getName(), record);
        Wml3gConverterPrevious ret = new Wml3gConverterPrevious(getContentHandler().getIssueId(),
                getContentHandler().getIssue(), recsList, record.getHistoryNumber());
        ret.setRecords(recs);
        return ret;
    }

    private static void convertLegacy(Context context, Wml3gConverter wml3gConv) throws Exception {
        wml3gConv.setTmpFolder(context.tmpFolder);
        Set<Integer> errRecords = wml3gConv.execute();
        if (!errRecords.isEmpty()) {
            throw new Exception(wml3gConv.getProcessingErrors());
        }
    }

    private boolean convert(IRecord record, ContentLocation cl, String topics, Context context, boolean cdsrImport) {
        String result1 = null;
        String result2 = null;
        String lastResult = null;
        String descriptor = null;
        String pubDates = null;
        Integer issueId = getContentHandler().getIssueId();
        String dbName = getContentHandler().getDbName();
        BaseType bt  = BaseType.getCDSR().get();
        String recordPath = record.getRecordPath();
        String cdNumber = record.getName();
        Integer version = record.getHistoryNumber();
        context.tmpFolder = cl.getPathToMl3gTmpDir(issueId, dbName, version, cdNumber) + FilePathCreator.SEPARATOR;
        try {
            if (CochraneCMSPropertyNames.isConversionImitateError()) {
                throw new CmsException("an imitated error");
            }
            context.stats.addArticle();
            Map<String, String> taResults = prepareTranslations(record, cl, context, cdsrImport);
            String result;
            if (record.isJats()) {
                result1 = context.conv.convertJats(recordPath, IConverterAdapter.JATS_CDSR,
                        IConverterAdapter.WILEY_ML3G, null, null, context.strictValidation);
                descriptor = JatsMl3gAssetsManager.generateAssetsAndPackageDescriptor(bt, record, issueId,
                        cl, false, context.helper(), context.rp);
                pubDates = JatsMl3gAssetsManager.createPublishDateDescriptor(
                        getContentHandler().getIssue(), bt, record);
                result = updateWML3G(record, result1, JatsMeshtermManager.generateMeshTerms(record, context.ms),
                    getMl3gToMl3gExtraXmls(descriptor, topics, pubDates), context.strictValidation, context.conv);
            } else {
                result = context.readContentFromTmpFolder(cdNumber + Extensions.XML);
            }

            if (taResults != null && !taResults.isEmpty()) {
                result2 = result;
                result = context.conv.convertSource(result, IConverterAdapter.WILEY_ML3G_SEQ,
                        IConverterAdapter.WILEY_ML3G_JOINED, null, taResults, context.strictValidation);
            }

            if (context.strictValidation) {
                // a last final validation
                lastResult = result;
                String err = context.conv.validate(result, IConverterAdapter.WILEY_ML3GV2_GRAMMAR);
                if (err != null && !err.isEmpty()) {
                    throw new CmsException(err);
                }
            }
            String baseMl3gPath = cl.getPathToMl3g(issueId, dbName, version) + cdNumber;
            RecordHelper.putFile(result, baseMl3gPath + Extensions.XML, context.rp);
            String publicationType = ContentChecker.getUnitPublicationType(result,
                    context.helper().getDocumentLoader());

            if (!record.isJats()) {
                RecordHelper.putFile(context.readContentFromTmpFolder(cdNumber + Extensions.ASSETS),
                        baseMl3gPath + Extensions.ASSETS, context.rp);
            }
            context.stats.addArticleSuccessful();
            if (context.saveTmpResults) {
                saveTmpResults(cdNumber, result1, result2, null, descriptor, pubDates, context.tmpFolder, context.rp);
            }
            if (hasDeliveryFile() && !cdsrImport) {
                String dfName = getContentHandler().getDfName();
                context.flLogger.onProductConverted(cdNumber, publicationType, isForDashboard(bt, dfName));
            }
            return true;

        } catch (Throwable th) {
            Throwable cause = th.getCause();
            if (cause == null) {
                cause = th;
            }
            IActivityLog logger = ActivityLogFactory.getFactory().getInstance();
            String error = cause.getMessage();
            logWml3gError(DatabaseEntity.CDSR_KEY, getContentHandler().getIssue(), record.getId(), cdNumber,
                    error, logger);
            context.addError(record, error);
            saveTmpResults(cdNumber, result1, result2, lastResult, descriptor, pubDates, context.tmpFolder, context.rp);

            if (hasDeliveryFile()) {
                if (!cdsrImport) {
                    String dfName = getContentHandler().getDfName();
                    context.flLogger.onProductPackageError(ILogEvent.PRODUCT_CONVERTED, dfName, cdNumber,
                        "Conversion error: " + error, true, isForDashboard(bt, dfName),
                            CmsUtils.isScheduledIssue(getContentHandler().getIssueId()));
                }
                RecordStorageFactory.getFactory().getInstance().flushQAResults(record,
                    ArchiePackage.canAut(getContentHandler().getDfName()), false, error, logger);
                Collection<Integer> badRecs = new HashSet<>();
                badRecs.add(record.getId());
                CochraneCMSBeans.getRenderManager().updateRendering(RenderingPlan.PDF_FOP.id(), Collections.emptySet(),
                        badRecs, true);
            }
            return false;
        }
    }

    private static boolean isForDashboard(BaseType bt, String dfName) {
        return bt.hasSFLogging() && (DeliveryPackage.isAut(dfName) || !DeliveryPackage.isArchie(dfName));
    }

    private Set<String> convertPackageTranslations(IRecord record, ContentLocation cl,
        Iterable<DbRecordVO> list, Map<String, String> ret, Context context, boolean cdsrImport) throws Exception {
        Set<String> retracted = null;
        boolean versionCheck4Import = cdsrImport
                && Property.get("cms.cochrane.jats.import.validation-ta-version", "true").get().asBoolean();
        for (DbRecordVO ta : list) {
            String language = ta.getLanguage();
            if (ta.isDeleted()) {
                retracted = retracted == null ? new HashSet<>() : retracted;
                retracted.add(language);
                continue;
            }
            convertTranslation(ta, record, cl, ret, context, versionCheck4Import, cdsrImport);
        }
        record.setRetractedLanguages(retracted);
        if (!ret.isEmpty()) {
            record.addLanguages(ret.keySet());
        }
        return retracted;
    }

    private void convertTranslation(DbRecordVO ta, IRecord record, ContentLocation cl, Map<String, String> ret,
            Context context, boolean versionCheck4Import, boolean convert4Import) throws Exception {
        Integer issueId = getContentHandler().getIssueId();
        Integer dfId = getContentHandler().getDfId();
        String cdNumber = record.getName();
        String lang = ta.getLanguage();
        String pubName = ta.getLabel();
        if (convert4Import && RevmanMetadataHelper.parsePubNumber(pubName) != record.getPubNumber()) {
            return;
        }
        String taFolderPath = cl.getPathToJatsTA(issueId, dfId, record.getHistoryNumber(), lang,
                convert4Import ? pubName : cdNumber);
        String taPath = RecordHelper.findPathToJatsTAOriginalRecord(taFolderPath, cdNumber, context.rp);

        if (versionCheck4Import) {
            context.checkJatsVersion(pubName, lang, taPath, record.getCochraneVersion());
            if (!convert4Import) {
                return;
            }
        }
        String result = context.conv.convertJats(taPath, IConverterAdapter.JATS_CDSR, IConverterAdapter.WILEY_ML3G,
                null, null, context.strictValidation);
        RecordHelper.putFile(result, cl.getPathToMl3gTA(issueId, dfId, record.getHistoryNumber(), lang,
                convert4Import ? pubName : cdNumber), context.rp);
        ret.put(lang, result);
    }

    private Map<String, String> prepareTranslations(IRecord record, ContentLocation cl, Context context,
                                                    boolean cdsrImport) throws Exception {
        String cdNumber = record.getName();
        int recordNumber = RecordHelper.buildRecordNumberCdsr(cdNumber);
        Map<String, String> ret = null;
        Set<String> retracted = null;
        Integer dfId = getContentHandler().getDfId();

        if (hasDeliveryFile()) {
            List<DbRecordVO> list = context.rm.getTranslations(recordNumber, dfId);
            if (!list.isEmpty()) {
                ret = new HashMap<>();
                retracted = convertPackageTranslations(record, cl, list, ret, context, cdsrImport);
            }
        }

        boolean fromEntire = cdsrImport || getContentHandler().isEntire() || hasNoJatsInPackage(
                dfId, record.getRecordPath()) || DeliveryPackage.isRepeat(getContentHandler().getDfName());

        if (fromEntire || record.isUnchanged()) {
            // get translations previously uploaded
            if (record.isJats() || isOnlyConvertTA()) {
                ret = getTranslationsExisted(record, recordNumber, retracted, cdsrImport, ret, context);
            } else if (ContentLocation.ISSUE == cl) {
                convertLegacy(context, getLegacyConverter(record, context));
            } else if (ContentLocation.ISSUE_PREVIOUS == cl) {
                // conversion for Import
                convertLegacy(context, getLegacyConverter(record));
            } else {
                throw new UnsupportedOperationException(
                        "processing of NOT JATS content through the JatsConversionHandler is not supported");
            }
        }
        if (ret != null && !ret.isEmpty()) {
            record.addLanguages(ret.keySet());
        }
        return ret;
    }

    private static boolean hasNoJatsInPackage(Integer dfId, String recordPath) {
        return dfId == null || !recordPath.contains(dfId + FilePathCreator.SEPARATOR + JatsPackage.JATS_FOLDER);
    }

    private Map<String, String> getTranslationsExisted(IRecord record, int recordNumber,
        Collection<String> retracted, boolean cdsrImport, Map<String, String> map, Context context) throws Exception {

        boolean toConvert = isConvertTA();
        Integer version = record.getHistoryNumber();
        boolean previous = version != null && version > RecordEntity.VERSION_LAST;
        ContentLocation location = previous ? ContentLocation.PREVIOUS : ContentLocation.ENTIRE;

        List<DbRecordVO> list = previous
                ? context.rm.getTranslationHistory(recordNumber, record.getHistoryNumber())
                : context.rm.getLastTranslations(recordNumber);
        StringBuilder errs2Import = null;

        Map<String, String> ret = map == null && !list.isEmpty() ? new HashMap<>() : map;
        for (DbRecordVO ta : list) {

            String language = ta.getLanguage();
            if (ret.containsKey(language) || retracted != null && retracted.contains(language)) {
                continue;
            }
            if (!ta.isJats()) {
                context.stats.addNotJatsTranslation();
                if (cdsrImport) {
                    errs2Import = context.checkNotExistedTa2Import(ta.getLabel(), language, errs2Import);
                }
                LOG.warn(String.format("cannot process not JATS %s.%s", ta.getLabel(), language));
                continue;
            }
            try {
                if (toConvert) {
                    context.stats.addTranslation();
                    convertTranslation(ta, record, location, ret, context, cdsrImport, false);
                    context.stats.addTranslationSuccessful();
                    continue;

                } else if (cdsrImport) {
                    convertTranslation(ta, record, location, ret, context, true, false);
                }
            }  catch (Exception e) {
                context.addError(ta.getLabel(), e.getMessage());
                continue;
            }

            String taPath = location.getPathToMl3gTA(null, null, version, language, record.getName());
            if (!context.rp.isFileExists(taPath)) {
                context.addError(ta.getLabel(), String.format("can not find %s.%s in ML3G", ta.getLabel(), language));
                continue;
            }
            ret.put(language, InputUtils.readStreamToString(context.rp.getFile(taPath)));
        }
        if (errs2Import != null) {
            throw new CmsException(errs2Import.toString());
        }
        return ret;
    }

    private static List<ProcessPartVO> createProcessParts(ProcessVO pvo, Iterable<IRecord> startRecords) {
        int batch = pvo.getType().batch();
        List<ProcessPartVO> ret = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int start = 0;
        for (IRecord record: startRecords) {
            ProcessHelper.addIdsParam(record.getId(), sb);
            start++;
            if (start == batch) {
                ret.add(new ProcessPartVO(DbEntity.NOT_EXIST_ID, pvo.getId(), "", sb.toString(), ProcessState.NONE));
                sb = new StringBuilder();
                start = 0;
            }
        }
        if (start > 0) {
            ret.add(new ProcessPartVO(DbEntity.NOT_EXIST_ID, pvo.getId(), "", sb.toString(), ProcessState.NONE));
        }
        return ret;
    }
        
    private boolean isOnlyConvertTA() {
        return convertTA == ConversionRecordHandler.ONLY_SPECIAL;
    }

    private boolean isConvertTA() {
        return convertTA == ConversionRecordHandler.WITH_SPECIAL || isOnlyConvertTA();
    }

    private void setConvertTranslations(int value) {
        convertTA = value;
    }

    private void setConvertTranslations(String value) throws ProcessException {
        setConvertTranslations(getIntegerParam(value));
    }

    @Override
    public IProcessStats readStats(ProcessSupportVO pvo) {
        String ret = pvo.getStats();
        return ret != null ? new Stats(ret, isConvertTA()) : Stats.EMPTY;
    }

    @Override
    public IProcessStats writeStats(IProcessStats stats, ProcessSupportVO processPart) {
        Object partStats = readStats(processPart);
        return partStats instanceof Stats ? ((Stats) partStats).add(stats) : stats;
    }

    private static final class Context extends ErrorHolder {
        final IRepository rp = RepositoryFactory.getRepository();
        final IMeshtermStorage ms = MeshtermStorageFactory.getFactory().getInstance();
        final IConverterAdapter conv = CochraneCMSBeans.getConverter();
        final IRecordManager rm = CochraneCMSBeans.getRecordManager();
        final IFlowLogger flLogger;
        final boolean saveTmpResults = ContentHelper.saveTmpResults();
        final boolean strictValidation = JatsHelper.isStrictValidation();
        String tmpFolder = "";
        Wml3gConverterIssueDb wml3gConv;
        final IStats stats;
        Set<String> translationsNotExisted;
        JatsHelper jatsHelper;

        private Context(boolean hasStats, boolean convertTa, IFlowLogger logger) {
            stats = hasStats ? new Stats(convertTa) : Stats.EMPTY;
            flLogger = logger;
        }

        @Override
        public void addJatsError(ICDSRMeta meta, String err) {
            stats.addNotJatsArticle();
            addError(meta, err);
        }

        @Override
        public void addJatsError(String name, String err) {
            stats.addNotJatsArticle();
            addError(name, err);
        }

        private Wml3gConverterIssueDb wml3gConverter(DbHandler dh) {
            if (wml3gConv == null) {
                wml3gConv = new Wml3gConverterIssueDb(dh.getIssueId(), dh.getIssue(), dh.getDbId(), dh.getDbName());
            }
            return wml3gConv;
        }

        private String readContentFromTmpFolder(String fileName) throws Exception {
            return InputUtils.readStreamToString(rp.getFile(tmpFolder + fileName));
        }

        private void checkJatsVersion(String pubName, String lang, String taPath, String jatsVersion) throws Exception {
            if (jatsVersion == null) {
                return;
            }
            String version = helper().extractCochraneVersion(taPath, rp);
            if (!version.equals(jatsVersion)) {
                throw new CmsException(String.format(
                    "an article' version %s doesn't match to the 'cochrane-version-number' in %s.%s - %s",
                        jatsVersion, pubName, lang, version));
            }
        }

        private StringBuilder checkNotExistedTa2Import(String pubName, String lang, StringBuilder errs)
                throws Exception {
            StringBuilder ret = errs;

            if (translationsNotExisted == null) {
                translationsNotExisted = new HashSet<>();
                String path = FilePathBuilder.getPathToEntire(CochraneCMSPropertyNames.getCDSRDbName())
                        + "translations_not_existed";
                if (rp.isFileExistsQuiet(path)) {
                    String list = InputUtils.readStreamToString(rp.getFile(path));
                    translationsNotExisted.addAll(Arrays.asList(list.split("\n")));
                }
            }
            if (translationsNotExisted.contains(FilePathBuilder.buildTAName(lang, pubName))) {
                LOG.warn(String.format(
                    "not JATS %s.%s existed in XDPS, but will be omitted because it belongs to the out-of-date scope",
                        pubName, lang));
            } else {
                String msg = String.format(
                    "cannot process not JATS %s.%s existed in XDPS, but not included in the JATS package to import",
                        pubName, lang);
                if (!Property.get("cms.cochrane.jats.import.validation-ta-all_exist", "false").get().asBoolean()) {
                    LOG.warn(msg);
                    return ret;
                }
                if (ret == null) {
                    ret = new StringBuilder();
                }
                ret.append("\n").append(msg);
            }
            return ret;
        }

        private JatsHelper helper() {
            if (jatsHelper == null) {
                jatsHelper = new JatsHelper();
            }
            return jatsHelper;
        }
    }

    private interface IStats extends IProcessStats {
        default void addArticle() {
        }

        default void addTranslation() {
        }

        default void addArticleSuccessful() {
        }

        default void addTranslationSuccessful() {
        }

        default void addNotJatsArticle() {
        }

        default void addNotJatsTranslation() {
        }

        @Override
        default String asString() {
            return "0/0/0;0/0/0";
        }
    }

    private static final class Stats implements IStats {
        private static final IStats EMPTY = new IStats() {
            @Override
            public String toString() {
                return "empty stats";
            }
        };

        int[] articles = {0, 0, 0};
        int[] translations = {0, 0, 0};
        final boolean convertTa;

        private Stats(boolean convertTa) {
            this.convertTa = convertTa;
        }

        private Stats(String strValue, boolean convertTa) {
            this(convertTa);

            String[] strs = strValue.split(";");
            parseGroup(strs[0], articles);
            if (strs.length > 1) {
                parseGroup(strs[1], translations);
            }
        }

        private static void parseGroup(String str, int[] stats) {
            String[] strs = str.split("/");
            stats[0] = parseValue(strs[0]);
            if (strs.length > 1) {
                stats[1] = parseValue(strs[1]);
            }
            if (strs.length > 2) {
                stats[2] = parseValue(strs[2]);
            }
        }

        public void addArticle() {
            articles[0]++;
        }

        public void addTranslation() {
            translations[0]++;
        }

        public void addArticleSuccessful() {
            articles[1]++;
        }

        public void addTranslationSuccessful() {
            translations[1]++;
        }

        public void addNotJatsArticle() {
            articles[2]++;
        }

        public void addNotJatsTranslation() {
            translations[2]++;
        }

        @Override
        public boolean isEmpty() {
            return articles[0] == 0 && articles[2] == 0 && translations[0] == 0 && translations[2] == 0;
        }

        @Override
        public String asString() {
            return String.format("%d/%d/%d;%d/%d/%d",
                    articles[0], articles[1], articles[2], translations[0], translations[1], translations[2]);
        }

        @Override
        public String toString() {
            // CPP5-705
            return String.format("\nJATS articles converted %d from %d, RevMan %d not converted;" + (convertTa
                            ? "\nJATS translations converted %d from %d, not JATS %d not converted"
                            : "\nJATS translations converted %d from %d, not JATS %d (conversion is not performed)"),
                articles[1], articles[0], articles[2], translations[1], translations[0], translations[2]);
        }

        private Stats add(Object obj) {
            if (obj instanceof Stats) {

                Stats ret = (Stats) obj;
                ret.articles[0] += articles[0];
                ret.articles[1] += articles[1];
                ret.articles[2] += articles[2];
                ret.translations[0] += translations[0];
                ret.translations[1] += translations[1];
                ret.translations[2] += translations[2];
                return ret;
            }
            return this;
        }

        private static int parseValue(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException nfe) {
                LOG.warn(nfe.getMessage());
            }
            return 0;
        }
    }
}
