package com.wiley.cms.cochrane.process;

import java.io.File;
import java.util.List;

import javax.ejb.EJB;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;

import com.wiley.cms.cochrane.process.handler.RenderingRecordHandler;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.qaservice.services.WebServiceUtils;
import com.wiley.cms.render.services.IRenderingProvider;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10.09.13
 */
public abstract class BaseRenderingManager extends BaseManager implements IBaseRenderingManager {

    @EJB(beanName = "TranslatedAbstractsInserter")
    protected ITranslatedAbstractsInserter taInserter;

    @EJB(beanName = "RecordCache")
    protected IRecordCache rcache;

    protected BaseType database;

    public boolean startRendering(int renderingJobId) {
        try {
            IRenderingProvider ws = WebServiceUtils.getRenderingProvider();
            return ws.resumeRendering(renderingJobId);
        } catch (Exception e) {
            LOG.error(e);
        }
        return false;
    }

    public boolean checkRecordOnProcessed(String recName, String basePath, long startTime) {

        if (rcache.containsRecord(recName)) {
            return true;
        }

        String filePath = basePath + recName;  // It doesn't work for CENTRAL
        File fl = new File(filePath);
        return fl.exists() && fl.lastModified() > startTime;
    }

    public boolean finalizeRendering(ProcessVO creator, int processId) {

        int creatorId = creator.getId();
        int rest = deleteProcess(creator, processId, true);
        if (rest == 0) {
            //if (creator.hasNext() && !asynchronousStart(creator.getNextId(),
            //        CochraneCMSBeans.getQueueProvider().getTaskQueue())) {
            //    logErrorProcess(creatorId, ILogEvent.EXCEPTION, "can't start next process", creator.getNextId());
            //}

            //if (creator.hasNext()) {
            //    try {
            //        CochraneCMSBeans.getCMSProcessManager().startProcess(creator.getNextId());
            //    }  catch (Exception e) {
            //        logErrorProcess(creatorId, ILogEvent.EXCEPTION, "can't start next process", creator.getNextId());
            //    }
            //}
            return true;
        } else {
            LOG.info(String.format("%s sub-processes remained: %d", creator, rest));
            List<ExternalProcess> list = ps.getExternalProcessChildren(creatorId, ProcessState.WAITED, 1);
            if (!list.isEmpty()) {
                startRendering(list.get(0).getId());
            }
        }
        return false;
    }

    @Override
    public int deleteProcess(ProcessVO creator, int renderingJobId, boolean stopCreator) {

        int ret = super.deleteProcess(creator, renderingJobId, stopCreator);
        if (renderingJobId == DbEntity.NOT_EXIST_ID) {
            return ret;
        }
        try {
            RenderingHelper.clearRendering(renderingJobId);
        } catch (Exception e) {
            LOG.error(e);
        }
        return ret;
    }

    public void onEndEntire(RenderingRecordHandler handler, ProcessVO pvo) {

        if (RenderingHelper.isEntire(handler)) {
            clearCache(pvo);
        }

        if (!pvo.getState().isFailed()) {

            String dbName = handler.getDbName();
            String planName = RenderingPlan.get(handler.getPlanId()).planName;

            logProcessEntire(pvo.getId(), dbName, ILogEvent.RND_COMPLETED, "plan=" + planName, false, pvo.getOwner());

            MessageSender.sendForDatabase(dbName, MessageSender.MSG_TITLE_ENTIRE_RENDERING_COMPLETED, planName);
        }
        LOG.info(String.format("%s %s finished", handler.getDbName(), pvo));

        if (pvo.hasCreator()) {
            stopCreator(getProcess(pvo.getCreatorId()), true);
        }
    }

    void onStartEntire(RenderingRecordHandler handler, ProcessVO pvo) throws ProcessException {
        if (database.isCDSR() && RenderingHelper.isEntire(handler)) {
            initCache(handler.getDbName(), pvo);
        }
    }

    private void clearCache(ProcessVO pvo) {
        try {
            RenderingRecordHandler ph = ProcessHandler.castProcessHandler(pvo.getHandler(),
                    RenderingRecordHandler.class);
            String dbName = ph.getDbName();
            ExternalProcess pp = rcache.getSingleProcess(dbName, ILogEvent.RND_STARTED);
            if (pvo.getId().equals(pp.getId())) {
                rcache.removeSingleProcess(dbName, ILogEvent.RND_STARTED);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private void initCache(String dbName, ProcessVO pvo) throws ProcessException {

        ExternalProcess pp = rcache.getSingleProcess(dbName, ILogEvent.RND_STARTED);
        if (pp != null && pp.exists() && !pp.getState().isCompleted()) {
            LOG.warn(String.format("process %s for %s already started", pp, dbName));
            throw new ProcessException(String.format("entire process for %s already started", dbName), pvo.getId());
        }
        rcache.addSingleProcess(pvo, dbName, ILogEvent.RND_STARTED);
    }
}
