package com.wiley.cms.cochrane.cmanager.entirerender;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.BatchRenderer;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.cms.render.services.IProvideRendering;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 20.09.2011
 */
@Deprecated
@Stateless
@Local(IEntireRendering.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EntireRendering implements IEntireRendering {
    private static final Logger LOG = Logger.getLogger(EntireRendering.class);
    private static final int THREE = 3;
    private static final int HUNDRED = 100;
    private static final String ISSUE_YEAR = "issueYear=";
    private static final String ISSUE_NUMBER = "issueNumber=";
    private static final String CENTRAL_RECORDS_NOT_FOUND_MSG = "central records not found";
    private static final String RECORDS_FOUND_MSG = "records found ";

    @EJB
    private ITranslatedAbstractsInserter taInserter;

    @EJB(beanName = "RecordCache")
    private IRecordCache cache;

    private IRecord current = RecordHelper.createIRecord();

    public boolean startRenderingEntireCDSR(RenderingPlan plan, List<String> records) throws Exception {

        List<String> entireRecords = EntireDBStorageFactory.getFactory().getInstance().findSysrevRecordNames();
        entireRecords.retainAll(records);
        return startRenderingForCDSR(plan, entireRecords, false);
    }

    public boolean startRenderingEntireCDSR(RenderingPlan plan, Integer[] recordIds, boolean withPrevious)
            throws Exception {

        List<Integer> list = Arrays.asList(recordIds);
        List<String> entireRecords = EntireDBStorageFactory.getFactory().getInstance().findRecordNames(list);
        return startRenderingForCDSR(plan, entireRecords, withPrevious);
    }

    public boolean startRenderingEntireCentral(RenderingPlan plan, List<String> records) throws Exception {
        if (records == null || records.size() == 0) {
            LOG.debug(CENTRAL_RECORDS_NOT_FOUND_MSG);
            return false;
        }
        return renderingEntireCentral(plan, records);
    }

    public boolean startRenderingEntireCentral(RenderingPlan plan, Integer[] recordIds) throws Exception {

        List<Integer> list = Arrays.asList(recordIds);
        List<String> entireRecords = EntireDBStorageFactory.getFactory().getInstance().findRecordNames(list);
        return renderingEntireCentral(plan, entireRecords);
    }

    private boolean renderingEntireCentral(RenderingPlan plan, List<String> records) throws Exception {

        IEntireDBStorage edbs = EntireDBStorageFactory.getFactory().getInstance();
        int pieceSize = CochraneCMSPropertyNames.getCentralRerenderingPartSize();

        boolean isSuccessful = false;


        int recordsSize = records.size();
        for (int i = 0; i < recordsSize; i += pieceSize) {

            List<String> subRecords = records.subList(i,
                    recordsSize - i >= pieceSize ? (i + pieceSize) : recordsSize);
            List<EntireDBEntity> entireRecords = edbs.getRecordList(CochraneCMSPropertyNames.getCentralDbName(), 0, 0,
                subRecords.toArray(new String[subRecords.size()]), null, 0, false);
            LOG.debug(RECORDS_FOUND_MSG + subRecords.size());

            Map<Integer, List<String>> recsByIssue = new HashMap<Integer, List<String>>();
            for (EntireDBEntity entity : entireRecords) {

                if (!recsByIssue.containsKey(entity.getLastIssuePublished())) {
                    recsByIssue.put(entity.getLastIssuePublished(), new ArrayList<String>());
                }

                recsByIssue.get(entity.getLastIssuePublished()).add(entity.getName());
            }

            for (Integer issue : recsByIssue.keySet()) {
                String[] requestParameters = new String[]{ISSUE_YEAR + issue / HUNDRED,
                    ISSUE_NUMBER + issue % HUNDRED};

                isSuccessful = startRenderingForCentral(plan, recsByIssue.get(issue), requestParameters);
                if (!isSuccessful) {
                    return false;
                }
            }
        }

        return isSuccessful;
    }

    public boolean startRenderingEntireCDSR(RenderingPlan plan) throws Exception {
        List<String> recordNames = EntireDBStorageFactory.getFactory().getInstance().findSysrevRecordNames();
        return startRenderingEntireCDSR(plan, recordNames);
    }

    private boolean startRenderingForCentral(RenderingPlan plan, List<String> records, String[] requestParameters)
            throws Exception {

        IRepository rp = RepositoryFactory.getRepository();
        String timestamp = String.valueOf(System.currentTimeMillis());

        URI[] uris = new BatchRenderer<String>(records, timestamp) {

            @Override
            public String getDbTitle() {
                return CochraneCMSPropertyNames.getCentralDbName();
            }

            @Override
            public String getSourceFilePath(String entity) throws URISyntaxException {
                return FilePathCreator.getUri(FilePathCreator.getFilePathToSourceEntire(
                        getDbTitle(), entity)).toString();
            }
        }.createUrisForCentral();

        boolean[] isRawDataExists = new boolean[uris.length];

        for (int i = 0; i < uris.length; i++) {
            isRawDataExists[i] = rp.isFileExists(FilePathCreator.getFilePathToUrlsCentralEntire(
                    CochraneCMSPropertyNames.getCentralDbName(), timestamp, i));

            if (!startOneJobWithCallBack(plan, new URI[]{uris[i]},
                    isRawDataExists, CochraneCMSPropertyNames.getCentralDbName(), requestParameters)) {
                return false;
            }
        }

        return true;
    }

    private boolean startPreviousRenderingForCDSR(RenderingPlan plan, List<List<URI>> prevUris,
                                                  List<List<Boolean>> prevRawData) throws Exception {

        int size = prevUris.size();

        for (int i = 0; i < size; i++) {

            List<URI> listUri = prevUris.get(i);
            URI[] uris = new URI[listUri.size()];
            uris = listUri.toArray(uris);

            List<Boolean> listRawData = prevRawData.get(i);
            boolean[] rawData = new boolean[listRawData.size()];
            for (int j = 0; j < listRawData.size(); j++) {
                rawData[j] = listRawData.get(j);
            }

            if (!startOneJobWithCallBack(plan, uris, rawData, CochraneCMSPropertyNames.getCDSRDbName(), null)) {
                return false;
            }
            listUri.clear();
            listRawData.clear();
        }

        prevUris.clear();
        prevRawData.clear();
        return true;
    }

    private void getPreviousRenderingForCDSR(String record, List<List<URI>> prevUris, List<List<Boolean>> prevRawData,
        File[] prevDirs, String basePath, IRepository rp, boolean isHtml) throws Exception {

        int pieceSize = CochraneCMSPropertyNames.getEntireRerenderingPartSize();
        List<URI> uris = prevUris.get(prevUris.size() - 1);
        List<Boolean> rawData = prevRawData.get(prevRawData.size() - 1);

        for (File version: prevDirs) {

            if (version.isFile()) {
                continue;
            }

            String versionDir = basePath + version.getName();
            String srcDir = FilePathCreator.getPreviousRecordDir(record, versionDir);
            String srcFile = srcDir + FilePathCreator.XML_EXT;
            if (!rp.isFileExists(srcFile)) {
                continue;
            }

            BaseType bt = BaseType.find(CochraneCMSPropertyNames.getCDSRDbName()).get();
            current.setName(record);
            URI uri = isHtml && bt.hasTranslationHtml()
                    ? FilePathCreator.getUri(taInserter.getSourceForRecordWithInsertedAbstracts(
                        current, srcFile, bt.getTranslationModeHtml())) : FilePathCreator.getUri(srcFile);

            String rawFile = FilePathBuilder.buildRawDataPathByDir(srcDir, record);
            Boolean raw = !rp.isFileExists(rawFile) ? Boolean.FALSE : Boolean.TRUE;

            if (uris.size() == pieceSize) {
                uris = new ArrayList<>();
                prevUris.add(uris);
                rawData =  new ArrayList<Boolean>();
                prevRawData.add(rawData);
            }
            uris.add(uri);
            rawData.add(raw);
        }
    }

    private boolean startRenderingForCDSR(RenderingPlan plan, List<String> entireRecords, boolean prev)
            throws Exception {

        String dbName = CochraneCMSPropertyNames.getCDSRDbName();
        //todo it is not working for multiple starts
        cache.addSingleProcess(dbName, ILogEvent.RND_STARTED);

        URI[] uris = new URI[entireRecords.size()];
        boolean[] isRawDataExists = new boolean[entireRecords.size()];
        IRepository rp = RepositoryFactory.getRepository();
        int pieceSize = CochraneCMSPropertyNames.getEntireRerenderingPartSize();
        int j = 0;


        File[] prevDirs = null;
        List<List<URI>> prevUris = null;
        List<List<Boolean>> prevRawData = null;
        String prevPath = null;

        if (prev) {
            prevPath = FilePathBuilder.getPathToPrevious();
            prevDirs = rp.getFilesFromDir(prevPath);
            prevUris = new ArrayList<List<URI>>();
            prevUris.add(new ArrayList<URI>());
            prevRawData = new ArrayList<List<Boolean>>();
            prevRawData.add(new ArrayList<Boolean>());
        }

        boolean isHtml = RenderingPlan.isHtml(plan.id());

        for (String recordName : entireRecords) {

            uris[j] = getFilePath(CochraneCMSPropertyNames.getCDSRDbName(), recordName, isHtml);
            isRawDataExists[j] = rp.isFileExists(getSourcePath(dbName, recordName));
            if (prevDirs != null) {
                getPreviousRenderingForCDSR(recordName, prevUris, prevRawData, prevDirs, prevPath, rp, isHtml);
            }

            if (++j % pieceSize == 0) {
                j = 0;
                if (!startOneJobWithCallBack(plan, uris, isRawDataExists, CochraneCMSPropertyNames.getCDSRDbName(),
                        null)) {
                    return false;
                }
            }
        }

        boolean res = true;
        if (j != 0) {
            URI[] urisRemainder = new URI[j];
            boolean[] isRawDataExists2 = new boolean[j];
            for (int i = 0; i < j; i++) {
                urisRemainder[i] = uris[i];
                isRawDataExists2[i] = isRawDataExists[i];
            }

            res = startOneJobWithCallBack(plan, urisRemainder, isRawDataExists2,
                    CochraneCMSPropertyNames.getCDSRDbName(), null);
        }

        if (prev) {
            res = startPreviousRenderingForCDSR(plan, prevUris, prevRawData);
        }

        return res;
    }

    private URI getFilePath(String dbName, String recordName, boolean insertTranslatedAbstracts) throws Exception {
        URI result = null;

        if (CochraneCMSPropertyNames.getCentralDbName().equals(dbName)) {
            result = FilePathCreator.getUri(FilePathCreator.getFilePathToSourceEntire(dbName, recordName));

        } else if (CochraneCMSPropertyNames.getCDSRDbName().equals(dbName)) {

            BaseType bt = BaseType.find(dbName).get();
            if (insertTranslatedAbstracts && bt.hasTranslationHtml()) {
                current.setName(recordName);
                result = FilePathCreator.getUri(taInserter.getSourceForRecordWithInsertedAbstracts(
                    current, FilePathCreator.getFilePathToSourceEntire(dbName, recordName),
                        bt.getTranslationModeHtml())
                );
            } else {
                result = FilePathCreator.getUri(FilePathCreator.getFilePathToSourceEntire(dbName, recordName));
            }
        }

        return result;
    }

    private String getSourcePath(String dbName, String recordName) {
        if (CochraneCMSPropertyNames.getCentralDbName().equals(dbName)) {
            return FilePathCreator.getFilePathForEnclosureEntire(CochraneCMSPropertyNames.getCentralDbName(),
                    recordName.substring(recordName.length() - THREE),
                    recordName + ".xml");
        } else if (CochraneCMSPropertyNames.getCDSRDbName().equals(dbName)) {
            //return FilePathCreator.getFilePathForEnclosureEntire(CochraneCMSPropertyNames.getCDSRDbName(), recordName,
            //        "table_n/" + recordName + "RawData.xml");
            return FilePathBuilder.getPathToEntireRawData(CochraneCMSPropertyNames.getCDSRDbName(), recordName);
        }

        throw new IllegalArgumentException();
    }


    private boolean startOneJobWithCallBack(RenderingPlan plan, URI[] uris, boolean[] isRawDataExists, String dbName,
                                            String[] requestParameters) {
        int jobId = -1;
        IProvideRendering ws = null;
        try {
            URI callback = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_SERVICE_URL)
                + "AcceptRndEntire?wsdl");

            ws = WebServiceUtils.getProvideRendering();

            jobId = ws.render("cochrane", plan.planName, uris, isRawDataExists, callback, requestParameters,
                    IProcessManager.LOW_PRIORITY);
        } catch (Exception ex) {
            LOG.error(ex, ex);
            return false;
        } finally {
            WebServiceUtils.releaseServiceProxy(ws, IProvideRendering.class);
        }

        logRndStarted(jobId, plan, "", dbName);
        return jobId != -1;
    }

    private void logRndStarted(int jobId, RenderingPlan plan, String message, String dbName) {
        IActivityLogService logService;
        try {
            logService = AbstractManager.getActivityLogService();
            logService.info(ActivityLogEntity.EntityLevel.FILE, ILogEvent.RND_STARTED, 1, "entire-" + dbName,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME),
                    "job id=" + jobId + ", plan=" + plan.planName + " " + message);
        } catch (Exception e) {
            LOG.error(e);
        }
    }
}
