package com.wiley.cms.cochrane.process;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.DbRecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.IVersionManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.process.handler.RenderingRecordHandler;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.render.services.IRenderingProvider;
import com.wiley.tes.util.DbConstants;
import com.wiley.tes.util.Extensions;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10.09.13
 */
@Stateless
@Local(IEntireRenderingManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EntireRenderingManager extends BaseRenderingManager implements IEntireRenderingManager {
    private static final String LOOKUP_NAME = CochraneCMSPropertyNames.buildLookupName(
            "EntireRenderingManager", IEntireRenderingManager.class);

    @EJB(beanName = "EntireDBStorage")
    private IEntireDBStorage edbs;

    @EJB(beanName = "VersionManager")
    private IVersionManager vm;

    @EJB(beanName = "ResultsStorage")
    private IResultsStorage rs;

    @EJB(beanName = "RecordManager")
    private IRecordManager rm;

    private List<String> records;
    private final IRecord current = RecordHelper.createIRecord();
    private Map<Integer, ICDSRMeta> fullHistory;

    @Override
    public void startRendering(RenderingPlan plan, Integer[] recordIds, String dbName, boolean withPrevious) {

        if (recordIds == null || recordIds.length == 0) {
            startRendering(plan, records, dbName,  withPrevious);
            return;
        }

        if (recordIds.length <= DbConstants.DB_PACK_SIZE) {
            startRendering(plan, edbs.findRecordNames(Arrays.asList(recordIds)), dbName,  withPrevious);
            return;
        }

        List<String> recs = new ArrayList<>(recordIds.length);
        List<Integer> list = new ArrayList<>(DbConstants.DB_PACK_SIZE);
        for (int id: recordIds) {
            list.add(id);
            if (list.size() == DbConstants.DB_PACK_SIZE) {
                recs.addAll(edbs.findRecordNames(list));
                list.clear();
            }
        }

        if (!list.isEmpty()) {
            recs.addAll(edbs.findRecordNames(list));
        }
        startRendering(plan, recs, dbName, withPrevious);
    }

    @Override
    public void startRendering(RenderingPlan plan, String dbName, boolean withPrevious) {
        startRendering(plan, records, dbName,  withPrevious);
    }

    @Override
    public void startRendering(RenderingPlan plan, List<String> records, String dbName, boolean withPrevious) {

        String label = LABEL_RENDERING_RECORDS;
        this.records = records;
        database = BaseType.find(dbName).get();
        boolean entire = records == null || records.isEmpty();
        if (entire) {
            this.records = edbs.findRecordNames(dbName, 0, Constants.UNDEF);
            label = LABEL_RENDERING_ENTIRE;
        }
        startProcess(new RenderingRecordHandler(label, dbName, plan.id(), withPrevious), getPriority(entire));
    }

    @Override
    public void onStart(ProcessHandler handler, ProcessVO pvo) throws ProcessException {
        RenderingRecordHandler ph = ProcessHandler.castProcessHandler(handler, RenderingRecordHandler.class);
        startRendering(ph, pvo);
    }

    public void endRendering(RenderingRecordHandler handler, ProcessVO pvo) {
        onEndEntire(handler, pvo);
    }

    public void startRendering(RenderingRecordHandler handler, ProcessVO pvo) throws ProcessException {

        RenderingRecordHandler ph = ProcessHandler.castProcessHandler(handler, RenderingRecordHandler.class);
        String dbName = ph.getDbName();
        if (database == null) {
            database = BaseType.find(dbName).get();
            if (records == null) {
                records = findRecordNames(pvo, dbName, edbs);
            }
        }

        onStartEntire(ph, pvo);

        int partSize;
        int startCount = records.size();
        int count;
        int creatorId = pvo.getId();
        int planId = ph.getPlanId();
        RenderingPlan plan = RenderingPlan.get(planId);

        logStart(pvo, dbName, plan, startCount, ", initial count=");
        LOG.info(String.format("%s %s is starting: count=%d", dbName, pvo, startCount));

        try {
            boolean cdsr = database.isCDSR();
            boolean editorial = !cdsr && database.isEditorial();
            if (cdsr || editorial) {
                partSize = CochraneCMSPropertyNames.getEntireRerenderingPartSize();
                count = startRendering(creatorId, dbName, planId, cdsr && ph.hasPrevious(),
                        pvo.getPriority(), partSize);
            } else {
                throw new ProcessException("unsupported database: " + dbName, creatorId);
            }

            if (count == 0) {
                throw new ProcessException("no any workable records");
            }

            if (count != startCount) {
                logStart(pvo, dbName, plan, count, ", count=");
            }

            LOG.info(String.format("%s %s started: count=%d", dbName, pvo, count));

        } catch (ProcessException pe) {
            logError(pvo, dbName, pe.getMessage());
            throw pe;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            logError(pvo, dbName, ErrorInfo.Type.SYSTEM.getMsg());
            throw new ProcessException(e.getMessage());
        } finally {

            this.records.clear();
            this.records = null;
            this.database = null;
        }
    }

    private void logStart(ProcessVO pvo, String dbName, RenderingPlan plan, int count, String msg) {
        logProcessEntire(pvo.getId(), dbName, ILogEvent.RND_STARTED, "plan=" + plan.planName + msg + count,
                false, pvo.getOwner());
    }

    private void logError(ProcessVO pvo, String dbName, String msg) {
        logErrorProcessEntire(pvo.getId(), dbName, ILogEvent.RND_NOT_STARTED, msg, false, pvo.getOwner());
    }

    private int startRendering(int creatorId, String dbName, int planId, boolean prev, int priority, int partSize)
            throws Exception {
        IRepository rp = RepositoryFactory.getRepository();

        File[] prevDirs = null;
        List<List<URI>> prevUris = null;
        List<List<Boolean>> prevRawData = null;
        List<List<String>> prevParams = null;
        String prevPath = null;
        int ret = 0;

        if (prev) {
            prevPath = FilePathBuilder.getPathToPrevious();
            prevDirs = rp.getFilesFromDir(prevPath);
            prevUris = new ArrayList<>();
            prevRawData = new ArrayList<>();
            prevParams = new ArrayList<>();
        }

        int[] planIds = {planId};
        List<String> params = new ArrayList<>();
        int j = 0;
        InputArrays input = new InputArrays(partSize, RenderingPlan.isPdfFOP(planId), database.isCDSR());
        boolean cdsrFop = input.needFopParams();

        int allSize = records.size();
        int countParts = allSize / partSize;
        int count = 0;
        if (allSize % partSize > 0) {
            countParts++;
        }

        List<Integer> toStart = new ArrayList<>();
        StringBuilder err = new StringBuilder();

        for (String recordName : records) {

            input.uris[j] = getFilePath(dbName, recordName, planId, err);
            if (input.uris[j] == null) {
                continue;
            }
            input.rawDataExists[j] = rp.isFileExists(FilePathBuilder.getPathToEntireRawData(dbName, recordName));

            if (cdsrFop) {
                fullHistory = initHistory(recordName, prev);
            }

            if (prev) {
                ret += getPreviousRendering(recordName, prevUris, prevRawData, prevParams, prevDirs, prevPath, planId);
            }
            if (cdsrFop) {
                input.partParameters[j] = addFopParameters4CDSR(RecordEntity.VERSION_LAST, input.uris[j]);
            }

            if (++j == partSize) {
                j = 0;
                if (prev) {
                    createPreviousRendering(creatorId, params, planIds, prevUris, prevRawData, prevParams, priority);
                }
                RenderingHelper.addPlanParams(params, planIds, input.uris.length, dbName, input.needFullPdfOnly4All());
                if (++count == countParts)  {  // the latest iteration
                    RenderingHelper.startRendering(creatorId, params, input.uris, input.rawDataExists,
                            input.partParameters, priority);

                } else {
                    int id = RenderingHelper.createRendering(creatorId, params, input.uris, input.rawDataExists,
                            input.partParameters, priority);
                    addToStart(id, toStart);
                    input = new InputArrays(partSize, input.fop, input.cdsr);
                    params.clear();
                }
            }
            ret++;
        }

        if (j != 0) {
            if (prev) {
                createPreviousRendering(creatorId, params, planIds, prevUris, prevRawData, prevParams, priority);
            }
            startRemain(creatorId, dbName, planIds, params, priority, input, j);
        }

        for (int prevId: toStart) {
            // just to speed up
            startRendering(prevId);
        }

        if (err.length() > 0) {
            MessageSender.sendForDatabase(dbName, MessageSender.MSG_TITLE_RENDERING_FAILED,
                    ContentLocation.ENTIRE.getShortString(0, dbName, null), err.toString());
        }

        if (fullHistory != null) {
            fullHistory.clear();
        }

        return ret;
    }

    private void startRemain(int creatorId, String dbName, int[] planIds, List<String> params, int priority,
                             InputArrays input, int j) throws Exception {
        InputArrays remain = new InputArrays(j, input.fop, input.cdsr);
        for (int i = 0; i < j; i++) {

            remain.uris[i] = input.uris[i];
            remain.rawDataExists[i] = input.rawDataExists[i];
            if (remain.partParameters != null) {
                remain.partParameters[i] = input.partParameters[i];
            }
        }
        RenderingHelper.addPlanParams(params, planIds, remain.uris.length, dbName, input.fop && database.isEditorial());
        RenderingHelper.startRendering(creatorId, params, remain.uris, remain.rawDataExists, remain.partParameters,
                priority);
    }

    private void addToStart(int id, List<Integer> toStart) {
        if (toStart.size() < IRenderingProvider.MAX_SESSION - 1) {
            toStart.add(id);
        }
    }

    private void createPreviousRendering(int creatorId, List<String> jobParams, int[] planIds, List<List<URI>> prevUris,
        List<List<Boolean>> prevRawData, List<List<String>> prevParameters, int priority) throws Exception {

        String dbName = CochraneCMSPropertyNames.getCDSRDbName();
        int size = prevUris.size();

        for (int i = 0; i < size; i++) {

            List<URI> listUri = prevUris.get(i);
            if (listUri.isEmpty()) {
                continue;
            }

            URI[] uris = new URI[listUri.size()];
            uris = listUri.toArray(uris);

            List<Boolean> listRawData = prevRawData.get(i);
            boolean[] rawData = new boolean[listRawData.size()];
            for (int j = 0; j < rawData.length; j++) {
                rawData[j] = listRawData.get(j);
            }

            List<String> listParameters = prevParameters.get(i);
            String[] parameters = listParameters.isEmpty() ? null : listParameters.toArray(new String[0]);

            RenderingHelper.addPlanParams(jobParams, planIds, uris.length, dbName, false);
            RenderingHelper.createRendering(creatorId, jobParams, uris, rawData, parameters, priority);

            jobParams.clear();
            listRawData.clear();
            listUri.clear();
            listParameters.clear();
        }

        prevRawData.clear();
        prevUris.clear();
        prevParameters.clear();
    }

    private URI getFilePath(String dbName, String recordName, int planId, StringBuilder err) {
        try {
            String path;
            if (RenderingPlan.isPdfFOP(planId)) {
                path = FilePathCreator.getFilePathForEntireMl3gXml(dbName, recordName);
            } else {
                boolean insertTA = RenderingPlan.isHtml(planId);
                current.setName(recordName);
                path = insertTA && database.hasTranslationHtml()
                        ? taInserter.getSourceForRecordWithInsertedAbstracts(current,
                            FilePathCreator.getFilePathToSourceEntire(dbName, recordName),
                            database.getTranslationModeHtml())
                        : FilePathCreator.getFilePathToSourceEntire(dbName, recordName);
            }
            return FilePathCreator.getUri(path);

        } catch (Exception e) {
            String msg = recordName + " is failed to render because: " + e.getMessage();
            err.append("\n").append(msg);
            return null;
        }
    }

    private int getPreviousRendering(String cdNumber, List<List<URI>> prevUris, List<List<Boolean>> prevRawData,
        List<List<String>> prevParameters, File[] prevDirs, String basePath, int planId) throws Exception {

        IRepository rp = RepositoryFactory.getRepository();
        int pieceSize = CochraneCMSPropertyNames.getEntireRerenderingPartSize();

        if (prevUris.isEmpty()) {
            prevUris.add(new ArrayList<>());
            prevRawData.add(new ArrayList<>());
            prevParameters.add(new ArrayList<>());
        }

        List<URI> uris = prevUris.get(prevUris.size() - 1);
        List<Boolean> rawData = prevRawData.get(prevRawData.size() - 1);
        List<String> parameters = prevParameters.get(prevParameters.size() - 1);
        int ret = 0;

        for (File version: prevDirs) {

            if (version.isFile()) {
                continue;
            }

            String versionDir = basePath + version.getName();
            String srcFile;
            String fopParam = null;
            boolean insertTA = RenderingPlan.isHtml(planId);
            URI uri;
            current.setName(cdNumber);
            if (RenderingPlan.isPdfFOP(planId)) {
                int versionNumber = FilePathBuilder.extractPreviousNumber(version.getName());
                srcFile = FilePathCreator.getPreviousMl3gXmlPath(cdNumber, versionNumber);
                uri = buildURI(cdNumber, srcFile, insertTA);
                fopParam = addFopParameters4CDSR(versionNumber, uri);

            } else {
                String srcDir = FilePathCreator.getPreviousRecordDir(cdNumber, versionDir);
                srcFile = srcDir + Extensions.XML;
                uri = buildURI(cdNumber, srcFile, insertTA);
            }

            if (!rp.isFileExists(srcFile)) {
                continue;
            }

            String rawFile = FilePathBuilder.buildRawDataPathByUri(srcFile, cdNumber);
            Boolean raw = !rp.isFileExists(rawFile) ? Boolean.FALSE : Boolean.TRUE;

            if (uris.size() == pieceSize) {

                rawData = new ArrayList<>();
                prevRawData.add(rawData);
                uris = new ArrayList<>();
                prevUris.add(uris);
                parameters = new ArrayList<>();
                prevParameters.add(parameters);

            }
            rawData.add(raw);
            uris.add(uri);
            if (fopParam != null) {
                parameters.add(fopParam);
            }

            ret++;
        }
        return ret;
    }

    private URI buildURI(String cdNumber, String srcFile, boolean insertTA) throws Exception {
        current.setName(cdNumber);
        return insertTA && database.hasTranslationHtml() ? FilePathCreator.getUri(
                taInserter.getSourceForRecordWithInsertedAbstracts(current, srcFile,
                        database.getTranslationModeHtml())) : FilePathCreator.getUri(srcFile);
    }

    //private String buildFullPdfSetParam(int versionNumber, URI uri) {
    //    ICDSRMeta meta = versionNumber == RecordEntity.VERSION_LAST
    //            ? fullHistory.get(RecordEntity.VERSION_LAST) : fullHistory.get(versionNumber);

    //    if (meta != null && (!meta.isStageR() || meta.isWithdrawn())) {
    //        StringBuilder sb = new StringBuilder();
    //        ProcessHelper.addKeyValue(IRenderingProvider.PART_PARAM_FULL_PDF_ONLY, Boolean.TRUE.toString(), sb);
    //        return ProcessHelper.buildUriParam(uri.toString(), sb.toString());
    //    }
    //    return null;
    //}

    private String addFopParameters4CDSR(int versionNumber, URI uri) {
        ICDSRMeta meta = versionNumber == RecordEntity.VERSION_LAST ? fullHistory.get(RecordEntity.VERSION_LAST)
                : fullHistory.get(versionNumber);
        if (meta == null) {
            return null;
        }

        StringBuilder sb = null;
        if (!meta.isStageR() || meta.isWithdrawn()) {
            sb = new StringBuilder();
            ProcessHelper.addKeyValue(IRenderingProvider.PART_PARAM_FULL_PDF_ONLY, Boolean.TRUE.toString(), sb);
        }
        String ta4FopParam = TranslatedAbstractVO.getLanguages4FopAsStr(meta.getLanguages());
        if (ta4FopParam != null) {
            if (sb == null) {
                sb = new StringBuilder();
            }
            ProcessHelper.addKeyValue(IRenderingProvider.PART_PARAM_LANGUAGES, ta4FopParam, sb);
        }
        return sb != null ? ProcessHelper.buildUriParam(uri.toString(), sb.toString()) : null;
    }

    private Map<Integer, ICDSRMeta> initHistory(String cdNumber, boolean prev) {

        List<? extends ICDSRMeta> list = rs.findLatestMetadataHistory(cdNumber);
        Map<Integer, ICDSRMeta> ret = new HashMap<>();
        if (list.isEmpty())   {
            return ret;
        }

        ICDSRMeta last = list.remove(0);
        ret.put(RecordEntity.VERSION_LAST, last);
        List<String> taList = rm.getLanguages(cdNumber);
        if (!taList.isEmpty()) {
            last.setLanguages(new HashSet<>(taList));
        }

        if (prev) {
            List<PrevVO> history = vm.getVersions(cdNumber);
            Map<Integer, Set<String>> taHistory = getHistoryTranslations(RecordHelper.buildRecordNumber(cdNumber, 2));
            for (ICDSRMeta meta : list) {
                Integer v = findVersion(meta.getPubNumber(), history);
                if (v == null) {
                    continue;
                }
                ret.put(v, meta);
                if (taHistory != null) {
                    meta.setLanguages(taHistory.get(v));
                }
            }
        }
        return ret;
    }

    private Map<Integer, Set<String>> getHistoryTranslations(int recordNumber) {
        List<DbRecordVO> list = rm.getTranslationHistory(recordNumber, null);
        if (list.isEmpty()) {
            return null;
        }
        Map<Integer, Set<String>> ret = new HashMap<>();
        for (DbRecordVO ta: list) {
            ret.computeIfAbsent(ta.getVersion(), f -> new HashSet<>()).add(ta.getLanguage());
        }
        return ret;
    }

    private Integer findVersion(int pub, List<PrevVO> history) {
        for (PrevVO prev: history) {
            if (prev.pub == pub) {
                return prev.version;
            }
        }
        return null;
    }

    private static final class InputArrays {
        final URI[] uris;
        final boolean[] rawDataExists;
        final String[] partParameters;
        final boolean fop;
        final boolean cdsr;

        private InputArrays(int partSize, boolean fopPlan, boolean isCdsr) {
            fop = fopPlan;
            cdsr = isCdsr;
            uris = new URI[partSize];
            rawDataExists = new boolean[partSize];
            partParameters = needFopParams() ? new String[partSize] : null;
        }

        private boolean needFullPdfOnly4All() {
            return fop && !cdsr;
        }

        private boolean needFopParams() {
            return fop && cdsr;
        }
    }

    /**
     * Just factory
     */
    public static final class Factory extends AbstractBeanFactory<IEntireRenderingManager> {
        private static final Factory INSTANCE = new Factory();

        private Factory() {
            super(LOOKUP_NAME);
        }

        public static Factory getFactory() {
            return INSTANCE;
        }
    }
}
