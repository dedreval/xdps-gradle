package com.wiley.cms.cochrane.process.handler;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jms.Queue;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.process.BaseAcceptQueue;
import com.wiley.cms.cochrane.process.CMSProcessManager;
import com.wiley.cms.cochrane.process.RenderingHelper;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.render.services.IRenderingProvider;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/7/2019
 *
 */
public class RenderFOPHandler extends ContentHandler<DbHandler, BaseAcceptQueue, Map<String, IRecord>>
        implements Serializable, IContentResultAcceptor<Map<String, IRecord>> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(RenderFOPHandler.class);

    private Map<String, IRecord> initialRecords;

    public RenderFOPHandler() {
    }

    public RenderFOPHandler(DbHandler handler) {
        super(handler);
    }

    @Override
    protected Class<DbHandler> getTClass() {
        return DbHandler.class;
    }

    @Override
    public void onExternalMessage(ProcessVO pvo, BaseAcceptQueue queue)  {
        queue.processMessage(this, pvo);
    }

    @Override
    protected void onStartAsync(ProcessVO pvo, List<ProcessPartVO> inputData, CMSProcessManager manager, Queue queue)
            throws ProcessException {

        Map<String, IRecord> startRecords;
        if (inputData != null) {
            startRecords = takeResult(inputData);
            if (!checkInitialRecords(pvo, startRecords, false, IDeliveryFileStatus.STATUS_RND_NOT_STARTED)) {
                return;
            }
        } else {
            startRecords = initialRecords;
            checkInitialRecords(pvo, startRecords, true, IDeliveryFileStatus.STATUS_RND_NOT_STARTED);
        }
        manager.getRenderManager().createFOPRendering(startRecords.values());
        int size = startRecords.size();
        URI[] uris = new URI[size];
        String[] params = new String[size];
        List<String> jobParams = initRenderParams(uris, params, startRecords);
        try {
            RenderingHelper.startRendering(pvo.getId(), RenderingPlan.PDF_FOP.planName, jobParams, uris, params);
        } catch (Exception e) {
            throw new ProcessException(e);
        }
    }

    @Override
    public void acceptResult(Map<String, IRecord> records) {
        initialRecords = records;
    }

    @Override
    public void acceptResult(PackageUnpackHandler fromHandler, ProcessVO from) {
        initialRecords = fromHandler.takeResult(from);
    }

    @Override
    public void acceptResult(Wml3gValidationHandler fromHandler, ProcessVO from) {
        initialRecords = fromHandler.takeResult(from);
    }

    @Override
    public void logOnStart(ProcessVO pvo, ProcessManager manager) {
        super.logOnStart(pvo, manager);
        setDeliveryFileStatus(IDeliveryFileStatus.STATUS_RENDERING_STARTED, true);
    }

    private List<String> initRenderParams(URI[] uris, String[] params, Map<String, IRecord> startRecords) {

        String dbName = getContentHandler().getDbName();
        int issueId = getContentHandler().getIssueId();
        List<String> jobParams = new ArrayList<>();

        boolean cdsr = CochraneCMSPropertyNames.getCDSRDbName().equals(dbName);
        RenderingHelper.addJobParams(jobParams, dbName, !cdsr);

        int i = 0;
        for (Map.Entry<String, IRecord> entry : startRecords.entrySet()) {
            IRecord record = entry.getValue();
            Integer historyNumber = record.getHistoryNumber();
            ContentLocation cl = historyNumber != null && historyNumber > RecordEntity.VERSION_LAST
                    ? ContentLocation.ISSUE_PREVIOUS : ContentLocation.ISSUE;
            URI uri = RenderingHelper.createURI(issueId, dbName, record, cl);
            if (uri != null) {
                uris[i] = uri;
                StringBuilder sbParams = new StringBuilder();
                ProcessHelper.addKeyValue(IRenderingProvider.PART_PARAM_RAW_EXIST,
                       record.isRawExist() ? Boolean.TRUE.toString() : Boolean.FALSE.toString(), sbParams);

                addFullPdfOnlyParam(record, sbParams, cdsr);
                addLanguagesParam(record, sbParams, cdsr);

                params[i++] = sbParams.toString();
            }
        }
        return  jobParams;
    }

    private static void addFullPdfOnlyParam(IRecord record, StringBuilder sbParams, boolean cdsr) {
        if (!cdsr || !record.isStageR() || record.isWithdrawn()) {
            ProcessHelper.addKeyValue(IRenderingProvider.PART_PARAM_FULL_PDF_ONLY, Boolean.TRUE.toString(), sbParams);
        }
    }

    private static void addLanguagesParam(IRecord record, StringBuilder sbParams, boolean cdsr) {
        if (cdsr) {
            String ta4FopParam = TranslatedAbstractVO.getLanguages4FopAsStr(record.getLanguages());
            if (ta4FopParam != null) {
                ProcessHelper.addKeyValue(IRenderingProvider.PART_PARAM_LANGUAGES, ta4FopParam, sbParams);
            }
        }
    }
}
