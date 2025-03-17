package com.wiley.cms.cochrane.process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.activitylog.LogEntity;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.IDeliveringService;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.render.IRenderManager;
import com.wiley.cms.cochrane.process.handler.ContentHandler;
import com.wiley.cms.cochrane.process.handler.DbHandler;
import com.wiley.cms.cochrane.process.handler.PackageHandler;
import com.wiley.cms.cochrane.process.handler.QaServiceHandler;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.IProcessStorage;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.handler.TaskProcessHandler;
import com.wiley.cms.process.jms.IQueueProvider;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 1/23/2018
 */
@Stateless
@Local(ICMSProcessManager.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CMSProcessManager extends ProcessManager implements ICMSProcessManager, IBeanProvider {
    public static final String LOOKUP_NAME = CochraneCMSPropertyNames.buildLookupName(
                    "CMSProcessManager", ICMSProcessManager.class);

    private static final Logger LOG = Logger.getLogger(CMSProcessManager.class);

    @EJB(beanName = "QueueProvider")
    private IQueueProvider qp;

    @EJB(beanName = "FlowLogger")
    private IFlowLogger flowLogger;

    @EJB(beanName = "DeliveringService")
    private IDeliveringService ds;

    @EJB(beanName = "RenderManager")
    private IRenderManager rm;

    @EJB(beanName = "QaManager")
    private IQaManager qm;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public TaskVO createProcessTask(int processId, String name, long delay) throws ProcessException {
        return createDelayedProcessTask(processId, name, LOOKUP_NAME, delay, null);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public TaskVO findProcessTask(String taskName, String partUri) throws ProcessException {
        List<TaskVO> list = findProcessTasks(taskName);
        for (TaskVO task: list) {
            TaskProcessHandler handler = (TaskProcessHandler) task.getHandler();
            List<ProcessPartVO> parts = ps.getProcessParts(handler.getProcId());
            for (ProcessPartVO part: parts) {
                if (partUri.equals(part.uri)) {
                    return task;
                }
            }
        }
        return null;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<TaskVO> findProcessTasks(String taskName) {
        return tm.findTasks(taskName);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void deletePublishProcess(int processId) {
        try {
            ProcessVO pvo = findProcess(processId);
            if (pvo != null && pvo.getType().isEmpty()) {
                ps.deleteProcess(processId);
            }
        } catch (ProcessException pe) {
            LOG.warn(pe.getMessage());
        }
    }

    @Override
    public ExternalProcess findFailedPackageProcess(int dfId) {

        List<ProcessVO> list = findPackageProcesses(BaseManager.LABEL_MAIN_PACKAGE, dfId);
        if (list.isEmpty()) {
            return null;
        }

        return findFailedProcess(list.get(list.size() - 1));  // take the latest
    }

    private ExternalProcess findFailedProcess(ExternalProcess parent) {

        if (parent.getState().isFailed()) {
            return parent;
        }

        ExternalProcess ret = null;
        List<ExternalProcess> list = ps.getExternalProcessChildren(parent.getId());
        for (ExternalProcess pvo: list) {
            ret = findFailedProcess(pvo);
            if (ret != null) {
                break;
            }
        }
        return ret;
    }

    @Override
    public List<ProcessVO> findPackageProcesses(String processLabel, int dfId) {

        List<ProcessVO> list = ps.findProcesses(processLabel);
        List<ProcessVO> ret = null;
        for (ProcessVO p: list) {
            PackageHandler ph = ProcessHandler.getProcessHandler(p.getHandler(), PackageHandler.class);
            if (ph != null && ph.getPackageId() == dfId) {
                if (ret == null) {
                    ret = new ArrayList<>();
                }
                ret.add(p);
            }
        }
        return ret == null ? Collections.emptyList() : ret;
    }

    public void resendCentralQAService(int dfId, List<RecordEntity> records) {
        List<ProcessVO> list = ps.findProcesses(PROC_TYPE_UPLOAD_CENTRAL_QAS);
        if (list.isEmpty()) {
            return;
        }
        ProcessVO qas = null;
        QaServiceHandler qh = null;

        for (ProcessVO p: list) {
            qh = ProcessHandler.getProcessHandler(p.getHandler(), QaServiceHandler.class);
            if (qh != null && qh.getContentHandler().getDfId() == dfId) {
                qas = p;
                break;
            }
        }
        if (qas != null) {
            LOG.debug(String.format("%s for resend found", qas));
            Map<String, IRecord> map = new HashMap<>();
            for (RecordEntity e: records) {
                map.put(e.getName(), e);
            }
            qh.acceptResult(map);

            List<ExternalProcess> extProcess = ps.getExternalProcessChildren(qas.getId());
            extProcess.forEach(p -> deleteProcess(p.getId(), true));
            startProcess(qh, qas);
        }
    }

    @Override
    public List<ProcessVO> findActiveContentDbProcesses(int issue, int type) {

        List<ProcessVO> list = ps.findProcesses(type);
        List<ProcessVO> ret = null;
        for (ProcessVO p: list) {
            ContentHandler ch = ProcessHandler.getProcessHandler(p.getHandler(), ContentHandler.class);
            if (ch == null) {
                continue;
            }
            DbHandler dbh = ProcessHandler.getProcessHandler(ch.getContentHandler(), DbHandler.class);
            if (dbh != null && dbh.getIssue() == issue) {
                if (ret == null) {
                    ret = new ArrayList<>();
                }
                ret.add(p);
            }
        }
        return ret == null ? Collections.emptyList() : ret;
    }

    @Override
    public void logProcessStart(ProcessVO pvo, int event, String tag, int objId) {

        int size = pvo.getSize();
        ProcessState state = pvo.getState();
        String logMsg = ProcessHelper.buildProcessMsg(pvo.getId(),
                size > 0 ? "count=" + size + " " + state : state.toString());
        logProcess(pvo, event == 0 ? ILogEvent.PROCESS_STARTED : event, LogEntity.LogLevel.INFO,
                LogEntity.EntityLevel.PROCESS, tag, objId, logMsg);
    }

    @Override
    public void logProcessEnd(ProcessVO pvo, int event, String tag, int objId) {
        logProcess(pvo, event == 0 ? ILogEvent.PROCESS_COMPLETED : event, LogEntity.LogLevel.INFO,
            LogEntity.EntityLevel.PROCESS, tag, objId,
                ProcessHelper.buildProcessMsg(pvo.getId(), pvo.getState().toString()));
    }

    @Override
    public void logProcessFail(ProcessVO pvo, int event, String tag, int objId, String msg) {
        String logMsg = ProcessHelper.buildProcessMsg(pvo.getId(), msg != null ? msg : pvo.getState().toString());
        logProcess(pvo, event == 0 ? ILogEvent.PROCESS_FAILED : event, LogEntity.LogLevel.ERROR,
                LogEntity.EntityLevel.PROCESS, tag, objId, logMsg);
    }

    @Override
    public IActivityLog getActivityLog() {
        return flowLogger.getActivityLog();
    }

    @Override
    public IFlowLogger getFlowLogger() {
        return flowLogger;
    }

    @Override
    public IDeliveringService getDeliveringService() {
        return ds;
    }

    @Override
    public IProcessStorage getProcessStorage() {
        return ps;
    }

    @Override
    public IRenderManager getRenderManager() {
        return rm;
    }

    @Override
    public IQaManager getQaManager() {
        return qm;
    }

    @Override
    protected void onStart(ProcessHandler handler, ProcessVO pvo) throws ProcessException {
        // do nothing, it's just to keep an old approach
    }

    @Override
    protected void onNextPart(ProcessVO pvo, ArrayList<Integer> partIds) throws ProcessException {
        ProcessType pt = pvo.getType();
        sendProcessPart(pvo, pt.getGroup(), partIds, qp.getQueue(pt.getQueueName()));
    }

    @Override
    protected IQueueProvider getQueueProvider() {
        return qp;
    }

    protected void logProcess(ProcessVO pvo, int event, LogEntity.LogLevel level, LogEntity.EntityLevel entityLevel,
                              String tag, int objId, String msg) {
        getActivityLog().log(level, entityLevel, event, objId, tag, pvo.getOwner(), msg);
    }
}
