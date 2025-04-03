package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.IDeliveringService;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.IQaService;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.IssueWorker;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.process.IQaManager;
import com.wiley.cms.cochrane.process.IRenderingManager;
import com.wiley.cms.cochrane.process.RenderingManager;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.tes.util.Logger;

import javax.naming.NamingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 30-Aug-2007
 */
public class ResumeAction extends AbstractAction {
    private static final Logger LOG = Logger.getLogger(ResumeAction.class);

    private DeliveryFileWrapper dfWrapper;

    public ResumeAction(DeliveryFileWrapper dfWrapper) {
        setConfirmable(true);

        this.dfWrapper = dfWrapper;
    }

    public int getId() {
        return Action.RESUME_ACTION;
    }

    public String getDisplayName() {
        return CochraneCMSProperties.getProperty("df.action.resume.name");
    }

    public void perform(IVisit visit) {
        if (!CochraneCMSProperties.getBoolProperty("cms.cochrane.resume", false)) {
            return;
        }
        try {
            int st = dfWrapper.getInterimStatusId();
            LOG.debug("Resume started, status= " + st);
            logAction(visit, ActivityLogEntity.EntityLevel.FILE, ILogEvent.RESUME, dfWrapper.getId(),
                      dfWrapper.getName());

            switch (st) {
                //case IDeliveryFileStatus.STATUS_PACKAGE_DELETED:
                //    startQa(dfWrapper.getId());
                //    break;
                case IDeliveryFileStatus.STATUS_QAS_STARTED:
                    resumeQA();
                    break;
                //case IDeliveryFileStatus.STATUS_QAS_ACCEPTED:
                //    IRenderingService rndService = CochraneCMSPropertyNames.lookup("RenderingService",
                //            IRenderingService.class);
                //    rndService.startRendering(dfWrapper.getId(), null);
                //    break;
                case IDeliveryFileStatus.STATUS_RENDERING_STARTED:
                    resumeRendering();
                    break;

                case IDeliveryFileStatus.STATUS_SHADOW:
                    //if (DeliveryPackage.isSystemReview(dfWrapper.getName())
                    //    && dfWrapper.getStatusId() == IDeliveryFileStatus.STATUS_RND_SUCCESS_AND_FULL_FINISH) {
                   //     publishPackage(true);
                    //} else {
                    IDeliveringService dlvService = CochraneCMSPropertyNames.lookup("DeliveringService",
                            IDeliveringService.class);
                    dlvService.finishUpload(dfWrapper.getIssue().getId(), dfWrapper.getId(), dfWrapper.getRecords());
                    break;

                case IDeliveryFileStatus.STATUS_REVMAN_CONVERTED:
                case IDeliveryFileStatus.STATUS_REVMAN_CONVERTING:
                    IContentManager cm = CochraneCMSPropertyNames.lookup("ContentManager", IContentManager.class);
                    cm.resumeOldPackage(dfWrapper.getId(), dfWrapper.getName());
                    break;

                //case IDeliveryFileStatus.STATUS_PUBLISHING_STARTED:
                //case IDeliveryFileStatus.STATUS_PUBLISHING_FAILED:
                //    publishPackage(dfWrapper.getStatusId() != IDeliveryFileStatus.STATUS_PUBLISHING_FAILED);
                //    break;

                default:
                    LOG.info("No actions for status " + st);
            }
        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    @Deprecated
    private void startQa(Integer id) {
        IQaService qaService = null;
        try {
            qaService = initQaService();
            qaService.deleteQasResultByDf(dfWrapper.getId());
            if (dfWrapper.getName().contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL))
                    || dfWrapper.getName().contains(CochraneCMSProperties.getProperty(
                        CochraneCMSPropertyNames.CLCMR))) {
                qasRequestForCentral(dfWrapper.getId(), dfWrapper.getName());
            } else {
                qasRequestForCommon();
            }
            qaService.updateDeliveryFile(id, true);
        } catch (Exception e) {
            LOG.error(e);
            if (qaService != null) {
                qaService.updateDeliveryFile(id, false);
            }
            Map<String, String> map = new HashMap<String, String>();
            map.put("jobId", "-");
            map.put("deliveryFileName", dfWrapper != null ? dfWrapper.getName() : "");
            map.put("database", dfWrapper.getName());
            map.put("report", e.getMessage());

            MessageSender.sendMessage("qas_job_failed", map);
        }
    }

    @Deprecated
    private void qasRequestForCentral(Integer dfId, String dfName) throws Exception {
        List<String> uris = RecordStorageFactory.getFactory().getInstance().getRecordPathByDf(dfId, false);

        IQaService qaService = initQaService();

        ArrayList<URI> files = new ArrayList<URI>(Integer.valueOf(
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.QAS_PACKAGE_SIZE)));
        int j = 0;
        boolean isDelay = false;
        for (String uri : uris) {
            if (j == Integer.valueOf(
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.QAS_PACKAGE_SIZE))) {
                qaService.startQas(files, dfId, dfName, isDelay);
                isDelay = true;
                j = 0;
                files = new ArrayList<URI>(Integer.valueOf(
                        CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.QAS_PACKAGE_SIZE)));
            }

            files.add(FilePathCreator.getUri(uri));
            ++j;
        }
        if (j != 0) {
            qaService.startQas(files, dfId, dfName, isDelay);
        }

    }

    private IQaService initQaService() throws NamingException {
        return CochraneCMSPropertyNames.lookup("QaService", IQaService.class);
    }

    @Deprecated
    private void qasRequestForCommon() throws Exception {
        int offset = 0;
        int limit = Integer.parseInt(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.QAS_PACKAGE_SIZE));
        IRecordStorage recStorage = AbstractManager.getRecordStorage();
        IQaService qaService = initQaService();
        boolean isDelay = false;

        int dfId = dfWrapper.getId();
        int issueId = dfWrapper.getIssue().getId();
        String dbName = dfWrapper.getDbName();
        String dfName = dfWrapper.getName();


        List<Object[]> recNames = recStorage.getRecordPaths(dfId, false, 0, limit);
        while (!recNames.isEmpty()) {
            List<String> resUris = RecordHelper.getRecordSourceUris(issueId, dbName, recNames);
            if (resUris.isEmpty()) {
                LOG.error("Failed to get resources uris from source group for records " + recNames);
            }

            List<URI> files = new ArrayList<URI>(resUris.size());
            for (String resUri : resUris) {
                files.add(FilePathCreator.getUri(resUri));
            }

            qaService.startQas(files, dfId, dfName, isDelay);
            isDelay = true;

            offset += recNames.size();
            recNames = recStorage.getRecordPaths(dfId, false, offset, limit);
        }
    }

    /*private void startSendingResult() throws MalformedURLException, NamingException, ServiceException,
            URISyntaxException {

        URI callback = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_SERVICE_URL)
                + "AcceptRenderingResults/AcceptRenderingResults?wsdl");
        List<Integer> ids = AbstractWrapper.getResultStorage().getNotCompletedJobId(dfWrapper.getId());
        for (Integer id : ids) {
            IProvideRendering ws = WebServiceUtils.getProvideRendering();
            ws.sendJobResult(id, callback);

        }
    }*/

    protected void resumeQA() throws Exception {
        IQaManager manager = IssueWorker.getQaManager();

        String dbName = dfWrapper.getDbName();
        int dfId = dfWrapper.getId();

        List<Object[]> recNames = AbstractManager.getRecordStorage().getRecordPaths(dfId, false, 0, 0);
        LOG.info(String.format("%d record of %s package [%d] to QA have been found...", recNames.size(), dbName, dfId));
        if (recNames.isEmpty()) {
            return;
        }

        DeliveryPackageInfo manifest =  new DeliveryPackageInfo(dfWrapper.getIssue().getId(), dbName,
                dfWrapper.getDbId(), dfWrapper.getId(), dfWrapper.getName());
        for (Object[] params: recNames) {
            manifest.addFile(params[0].toString(), params[1].toString());
            manifest.addRecordPath(params[0].toString(), params[1].toString());
        }
        manager.startQa(manifest, dfWrapper.getId(), dfWrapper.getName(), dbName);
    }

    protected void resumeRendering() throws Exception {

        IRenderingManager rm = RenderingManager.Factory.getFactory().getInstance();
        rm.resumeRendering(dfWrapper.getId());
    }

    /*private void startQaSendingResult(int packageId)
        throws MalformedURLException, NamingException, ServiceException, URISyntaxException {

        URI callback = new URI(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_SERVICE_URL)
                + "AcceptQaResults?wsdl");
        List<Integer> ids = AbstractWrapper.getResultStorage().getNotCompletedQaJobId(dfWrapper.getId());
        for (Integer id : ids) {
            IProvideQa ws = WebServiceUtils.getProvideQa();
            ws.sendJobResult(id, callback);
        }
    }*/
}

