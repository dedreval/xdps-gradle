package com.wiley.cms.process;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;

import org.apache.commons.lang.StringEscapeUtils;
import org.jdom.Document;
import org.jdom.Element;

import org.jdom.input.SAXBuilder;

import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.handler.TaskProcessHandler;
import com.wiley.cms.process.jms.IQueueProvider;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.process.task.ITaskManager;
import com.wiley.cms.process.task.TaskManager;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 02.07.13
 */
public abstract class ProcessManager implements IProcessManager {
    protected static final Logger LOG = Logger.getLogger(ProcessManager.class);

    private static final int DEFAULT_PROCESS_PART_BATH_SIZE = 50;
    private static final String SUCCESSFUL = "\" successful=\"";
    private static final String COMPLETED = "\" completed=\"";
    private static final int EXTENSION_LENGTH = 3;

    protected ITaskManager tm = TaskManager.Factory.getFactory().getInstance();
    protected IProcessStorage ps = ProcessStorageFactory.getFactory().getInstance();
    protected IProcessCache cache = ProcessCache.Factory.getFactory().getInstance();
    protected QueueConnectionFactory connectionFactory = JMSSender.lookupQueue();

    // just to keep an old approach
    protected abstract void onStart(ProcessHandler handler, ProcessVO pvo) throws ProcessException;

    protected abstract IQueueProvider getQueueProvider();

    public void logProcessStart(ProcessVO pvo, int event, String tag, int objId) {
    }

    public void logProcessEnd(ProcessVO pvo, int event, String tag, int objId) {
    }

    public void logProcessFail(ProcessVO pvo, int event, String tag, int objId, String msg) {
    }

    public ProcessVO startProcess(ProcessHandler handler, ProcessType type, String owner) {
        return startProcess(DbEntity.NOT_EXIST_ID, handler, type, owner, null);
    }

    // just to keep an old approach
    public ProcessVO startProcess(int creatorId, ProcessHandler handler, int priority) {
        ProcessVO pvo = ps.createProcess(creatorId, handler, ProcessType.empty(), priority, null);
        startProcess(handler, pvo);
        return pvo;
    }

    public ProcessVO startProcess(int creatorId, ProcessHandler handler, ProcessType type, String owner,
                                        List<ProcessPartVO> previousResults) {
        ProcessVO pvo = ps.createProcess(creatorId, handler, type, type.getPriority(), owner);
        startProcess(handler, pvo, previousResults);
        return pvo;
    }

    public ProcessVO startProcess(ProcessHandler handler, int priority) {
        return startProcess(DbEntity.NOT_EXIST_ID, handler, priority);
    }

    public void startProcess(ProcessVO pvo) {
        startProcess(pvo.getHandler(), pvo, null);
    }

    public void startProcess(ProcessHandler handler, ProcessVO pvo) {
        startProcess(handler, pvo, null);
    }

    private void startProcess(ProcessHandler handler, ProcessVO pvo, List<ProcessPartVO> res) {
        try {
            ProcessType pt = pvo.getType();
            boolean shouldBeStopped = !processStart(handler, pvo, pt, res);
            if (!ProcessType.isEmpty(pt.getId()) && ((pt.isSync() && !pt.isContainer()) || shouldBeStopped)) {
                endProcess(pvo, pvo.getState().isStarted() ? ProcessState.SUCCESSFUL : pvo.getState());
            }
        } catch (Exception e) {
            onFail(pvo, e);
        }
    }

    public ProcessVO startProcess(int processId) throws ProcessException {
        ProcessVO pvo = findProcess(processId);
        startProcess(pvo.getHandler(), pvo, null);
        return pvo;
    }

    public ArrayList<Integer> processNextPart(ProcessVO pvo, List<ProcessPartVO> res) throws ProcessException {
        ProcessType pt = pvo.getType();
        int size = pt.countInMessage();
        ArrayList<Integer> parts = processNextPart(pvo, pt, pt.getId() == 0 ? getProcessPartBathSize() : size, res);
        if (parts != null && !parts.isEmpty()) {
            onNextPart(pvo, parts);
        }
        return parts;
    }

    // just to keep an old approach
    public void processNextPart(ProcessVO pvo, int partSize) throws ProcessException {
        synchronized (pvo) {
            ArrayList<Integer> parts = processNextPart(pvo, pvo.getType(), partSize, null);
            if (parts != null && !parts.isEmpty()) {
                onNextPart(pvo, parts);
            }
        }
    }

    private void startNextByPartProcess(ProcessVO pvo, List<ProcessPartVO> res, boolean completed) {
        if (pvo == null || res == null) {
            return;
        }

        if (!completed) {
            pvo.setFreeToCompleted(completed);
        }

        startProcess(pvo.getHandler(), pvo, res);

        if (completed) {
            pvo.setFreeToCompleted(completed);
            LOG.info(String.format("process %s is ready to be completed ...", pvo));
        }
    }

    private ArrayList<Integer> processNextPart(ProcessVO pvo, ProcessType pt, int partSize, List<ProcessPartVO> res)
            throws ProcessException {

        ProcessVO nextByPart = null;
        if (hasNext4Type(pvo, pt) && pt.startNextByPart()) {
            // should be always false for an old approach
            nextByPart = findProcess(pvo.getNextId());
        }

        int processId = pvo.getId();
        if (!pvo.getState().isStarted()) {
            LOG.debug(String.format("process [%d] was stopped", processId));
            return null;
        }

        boolean completed = false;
        ArrayList<Integer> parts = null;

        if (pt.hasExternalChildren()) {
            if (pvo.isFreeToCompleted()) {
                completed = checkCompletion(pvo, pt);
            }
        } else {
            parts = ps.findNotStartedProcessParts(processId, partSize);
            if (!parts.isEmpty()) {
                ps.setProcessPartsState(parts, ProcessState.WAITED);
            } else if (pvo.isFreeToCompleted()) {
                completed = checkCompletion(pvo, pt);
            }
        }

        startNextByPartProcess(nextByPart, res, completed);
        return parts;
    }

    private boolean checkCompletion(ProcessVO pvo, ProcessType pt) {
        int processId = pvo.getId();
        int unfinished = pt.hasExternalChildren() ? ps.getProcessChildrenCount(processId)
                : ps.getUnFinishedProcessPartCount(processId);
        if (unfinished == 0) {
            endProcess(pvo, ProcessState.SUCCESSFUL);
            return true;
        }
        LOG.info(String.format("process [%d] - uncompleted parts (%d)", processId, unfinished));
        return false;
    }

    public boolean existProcess(String label) {
        List<ProcessVO> list = ps.findProcesses(label);
        if (!list.isEmpty()) {
            LOG.info(String.format("%s already exists", list.get(0)));
            return true;
        }
        return false;
    }

    protected void acceptResults(int jobId, Queue acceptQueue) {
        acceptResults(jobId, ps.getExternalProcess(jobId), acceptQueue, new JMSSender.MessageProcessCreator());
    }

    protected void acceptResults(ProcessVO creator, ExternalProcess pvo, Queue acceptQueue)
            throws Exception {
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(pvo.getId());
        sendProcessPart(creator, ids, acceptQueue);
    }

    protected void acceptResults(int jobId, ExternalProcess pvo, Queue acceptQueue,
                                 JMSSender.MessageProcessCreator mpc) {
        if (pvo == null) {
            LOG.warn(String.format("process [%d] does not exist", jobId));
            return;
        }
        try {
            mpc.setProcess(pvo);
            sendMessage(pvo.getPriority(), acceptQueue, mpc);
        } catch (Exception e) {

            LOG.error(e.getMessage(), e);
            ps.setProcessState(pvo.getId(), ProcessState.FAILED);
            //setProcessState(pvo, ProcessState.FAILED, e.getMessage());
        }
    }

    protected int getProcessPartBathSize() {
        return DEFAULT_PROCESS_PART_BATH_SIZE;
    }

    protected void setProcessState(ProcessVO pvo, ProcessState state, String message) {
        ps.setProcessState(pvo, state, message);
        pvo.setState(state);
        pvo.addMessage(message);
    }

    protected ProcessVO getProcessInCache(int processId) {
        return cache.getProcess(processId);
    }

    protected ProcessVO getProcess(int processId) {
        ProcessVO ret = getProcessInCache(processId);
        return ret == null ? ps.getProcess(processId) : ret;
    }

    public void endProcess(ProcessVO pvo, Throwable exception) {
        onFail(pvo, exception);
    }

    public void endProcess(ProcessVO pvo, ProcessState state) {
        try {
            pvo.setState(state);   // todo: a state can be failed
            onEnd(pvo);

            ProcessType pt = pvo.getType();
            if (pt.canWriteStats()) {
                LOG.debug(String.format("process %s completed with: %s", pvo, pvo.getHandler().readStats(pvo)));
            } else {
                LOG.debug(String.format("process %s completed", pvo));
            }
            if (pt.logOnEnd()) {
                pvo.getHandler().logOnEnd(pvo, this);
            }

            boolean nextStarted = false;
            ProcessVO child = null;
            if (!pt.isEmpty() && !pvo.isFreeToCompletedWithParent()) {

                if (pvo.hasNext() && (!pt.hasNext() || pt.startNextByEnd())) {
                    ProcessVO nextPvo = ps.findProcess(pvo.getNextId());
                    ProcessHandler nextHandler = nextPvo.getHandler();
                    pvo.getHandler().pass(pvo, nextHandler);
                    startProcess(nextHandler, nextPvo);
                    nextStarted = true;

                } else if (pt.hasNext() && pt.startNextByEnd()) {
                    child = startNextChild(pvo.getCreatorId(), pt.getNext(), pvo.getHandler(), pvo);
                    nextStarted = !child.getState().isFailed();
                }  
            }
            if (pt.deleteOnEnd()) {
                deleteProcess(pvo, state, pvo.isFreeToCompletedWithParent()
                        || (!nextStarted && (child == null || !child.getState().isFailed())));
            }
        } catch (Exception e) {
            onFail(pvo, e);
        }
    }

    public ProcessVO findProcess(int processId) throws ProcessException {
        ProcessVO ret = getProcessInCache(processId);
        if (ret == null) {
            LOG.debug(String.format("cannot find process [%d] in cache", processId));
            ret = ps.findProcess(processId);
            if (ret != null) {
                cache.addProcess(ret);
            }
        }
        return ret;
    }

    public void deleteProcess(int processId, boolean withChildren) {
        if (withChildren) {
            List<ExternalProcess> list = ps.getExternalProcessChildren(processId);
            for (ExternalProcess p : list) {
                deleteProcess(p.getId(), true);
            }
        }
        deleteProcess(processId);
    }

    public void deleteProcess(int processId) {
        cache.removeProcess(processId);
        ps.deleteProcess(processId);
    }

    protected int stopCreator(ProcessVO creator, ProcessState childState) {
        int count;
        synchronized (creator) {
            int creatorId = creator.getId();
            count = ps.getProcessChildrenCountNotState(creatorId, ProcessState.FINISHED_STATES);
            if (count == 0) {
                endProcess(creator, creator.getState().isFailed() ? ProcessState.FAILED
                        : (creator.getType().isDependChildren() ? childState : ProcessState.SUCCESSFUL));
            } else {
                LOG.debug(String.format("%d children of %s remained", count, creator));
            }
        }
        return count;
    }

    public int deleteProcess(ProcessVO creator, int processId, boolean stopCreator) {
        deleteProcess(processId);
        if (!stopCreator || creator == null) {
            return -1;
        }
        return stopCreator(creator, false);
    }

    protected int stopCreator(ProcessVO creator, boolean delete) {
        int count;
        synchronized (creator) {
            int creatorId = creator.getId();
            count = ps.getProcessChildrenCount(creatorId);
            if (count == 0) {
                // get last updated version from cache
                if (delete) {
                    LOG.debug(String.format("process [%d] was deleted", creatorId));
                    deleteProcess(creatorId);
                    return count;
                }
                ProcessVO fresh = getProcessInCache(creatorId);
                if (fresh != null) {
                    endProcess(fresh, fresh.getState().isFailed() ? ProcessState.FAILED : ProcessState.SUCCESSFUL);
                }
            }
        }
        return count;
    }

    protected void onNextPart(ProcessVO pvo, ArrayList<Integer> partIds) throws ProcessException {
    }

    // just to support old approach
    protected final void onStart(ProcessVO pvo) {
        onStart(pvo.getHandler(), pvo, ProcessType.empty());
    }

    protected final boolean onStart(ProcessHandler handler, ProcessVO pvo, ProcessType pt) {
        if (!pvo.getState().isStarted()) {

            setProcessState(pvo, ProcessState.STARTED, null);
            cache.addProcess(pvo);
            LOG.debug(String.format("process %s is starting ...", pvo));
            if (pt.logOnStart()) {
                handler.logOnStart(pvo, this);
            }
            return true;
        }
        return false;
    }

    private boolean processStart(ProcessHandler handler, ProcessVO pvo, ProcessType pt, List<ProcessPartVO> results)
            throws ProcessException {

        boolean wasStarted = onStart(handler, pvo, pt);
        boolean ret = true;
        if (ProcessType.isEmpty(pt.getId())) {
            // just to support old approach
            handler.onStart(pvo, this);

        } else if (pt.isDependChildren()) {
            ret = startChildren(handler, pvo, pt);

        } else if (!pt.isSync()) {
            createNextByPartChildren(pvo.getCreatorId(), pt, handler, pvo);
            handler.onStartAsync(pvo, results, this, getQueueProvider().getQueue(pt.getQueueName()));

        } else {
            handler.onStartSync(pvo, results, this);
        }

        if (!wasStarted && pvo.getState().isStarted()) {
            LOG.debug(String.format("process %s started", pvo));
        }
        return ret;
    }

    private boolean startChildren(ProcessHandler handler, ProcessVO pvo, ProcessType pt) throws ProcessException {
        int creatorId = pvo.getId();
        boolean ret = false;
        if (pt.hasChildren()) {
            LOG.debug(String.format("process %s has children to create and start...", pvo));
            Iterator<ProcessType> it = pt.getNextChildren();
            while (it.hasNext()) {
                ProcessVO child = startNextChild(creatorId, it.next(), handler, pvo);
                ret = ret || !child.getState().isFailed();
            }
        } else {
            List<ExternalProcess> list = ps.getExternalProcessChildren(creatorId);
            if (!list.isEmpty()) {
                startChildren(pvo, list);
                ret = startChildren(pvo, list) || ret;
            }
        }
        return ret;
    }

    private ProcessVO startNextChild(int creatorId, ProcessType type, ProcessHandler prevHandler, ProcessVO prev) {
        ProcessHandler newHandler = passHandler(type, prevHandler, prev);
        return startProcess(creatorId, newHandler, type, prev.getOwner(), null);
    }

    private void createNextByPartChildren(int creatorId, ProcessType prevType, ProcessHandler prevHandler,
                                          ProcessVO prev) throws ProcessException {
        boolean createNext = prevType.startNextByPart();
        ProcessType nextType = prevType.getNext();
        ProcessVO pvo = prev;
        ProcessHandler handler = prevHandler;

        while (nextType != null && createNext) {
            handler = passHandler(nextType, handler, prev);
            ProcessVO nextPvo = ps.createProcess(creatorId, handler, nextType, nextType.getPriority(), pvo.getOwner());
            pvo.setNextId(nextPvo.getId());
            ps.setNextProcess(pvo);
            pvo = nextPvo;
            createNext = nextType.startNextByPart();
            nextType = nextType.getNext();
        }
    }

    private ProcessHandler passHandler(ProcessType type, ProcessHandler prevHandler, ProcessVO prev) {
        ProcessHandler newHandler = ProcessHandler.createHandler(type, prevHandler);
        prevHandler.pass(prev, newHandler);
        if (!type.isEmpty()) {
            newHandler.setName(type.getName());
        }
        return newHandler;
    }

    private boolean startChildren(ProcessVO parent, List<ExternalProcess> list) throws ProcessException {
        Set<Integer> nextIds = new HashSet<>();
        for (ExternalProcess ep : list) {
            if (ep.hasNext()) {
                nextIds.add(ep.getNextId());
            }
        }
        boolean ret = false;
        LOG.debug(String.format("process %s has %d children where next %d)", parent, list.size(), nextIds.size()));
        for (ExternalProcess ep : list) {
            if (nextIds.contains(ep.getId())) {
                LOG.debug(String.format("process [%d] will be start later ... ", ep.getId()));
                continue;
            }
            ProcessVO child = startProcess(ep.getId());
            ret = ret || !child.getState().isFailed();
        }
        return ret;
    }

    protected void onEnd(ProcessVO pvo) throws ProcessException {
        ps.setProcessState(pvo, pvo.getState(), pvo.getMessage());
        ProcessVO removed = cache.removeProcess(pvo.getId());
        pvo.getHandler().onEnd(removed == null ? pvo : removed, this);
        LOG.debug(String.format("process %s is finishing ...", pvo));
    }

    protected final void onFail(int processId, Throwable th) {
        ProcessVO pvo = getProcess(processId);
        if (pvo != null) {
            onFail(pvo, th);
        }
    }

    protected void onFail(ProcessVO pvo, Throwable th) {

        //boolean wasEnded = pvo.getState().isCompleted();
        LOG.warn(String.format("process [%d] failed: %s", pvo.getId(), th.getMessage()));
        setProcessState(pvo, ProcessState.FAILED, th.getMessage());

        cache.removeProcess(pvo.getId());

        ProcessHandler ph = pvo.getHandler();
        ProcessType pt = pvo.getType();
        //if (!wasEnded) {

        ph.onEnd(pvo, this);

        if (pt.logOnFail()) {
            ph.logOnFail(pvo, th.getMessage(), this);
        }
        //}
        if (pt.deleteOnFail()) {
            deleteProcess(pvo, ProcessState.FAILED, !hasNext4Type(pvo, pt));  //todo check next ...
        }
    }

    // to support an old approach
    public void sendProcessPart(ProcessVO pvo, final ArrayList<Integer> ids, Queue partsQueue)
            throws ProcessException {
        sendProcessPart(pvo, null, ids, partsQueue);
    }

    protected void sendProcessPart(ProcessVO pvo, String group, final ArrayList<Integer> ids, Queue partsQueue)
            throws ProcessException {
        sendMessage(pvo.getPriority(), partsQueue,
                (Session session) -> JMSSender.buildProcessMessage(pvo.getId(), group, ids, session));
    }

    public void sendProcess(ProcessVO pvo, ProcessType pt, Queue queue) throws ProcessException {
        sendMessage(pvo.getPriority(), queue,
                (Session session) -> JMSSender.buildProcessMessage(pvo.getId(), pt.getGroup(), session));
    }

    protected void sendMessage(int priority, Queue partsQueue, JMSSender.MessageCreator mc) throws ProcessException {
        if (!sendMessage(priority, partsQueue, mc, false)) {
            sendMessage(priority, partsQueue, mc, true);
        }
    }

    private boolean sendMessage(int priority, Queue partsQueue, JMSSender.MessageCreator mc, boolean repeat)
            throws ProcessException {
        try {
            JMSSender.send(repeat ? JMSSender.lookupQueue(JMSSender.DEFAULT_CONNECTION_LOOKUP)
                    : connectionFactory, partsQueue, mc, priority, 0);
            return true;
        } catch (Exception e) {
            if (repeat) {
                throw new ProcessException(e);
            }
            LOG.warn("trying to repeat operation by:" + e.getMessage());
        }
        return false;
    }

    protected String buildPartParams(int ind, String partUri) {
        return null;
    }

    // to support an old approach
    protected void sendProcessParts(ProcessVO pvo, URI[] parts, int sessionCount, Queue partsQueue)
            throws ProcessException {
        sendProcessParts(pvo, parts, 0, parts.length, sessionCount, partsQueue);
    }

    public Integer createProcessPart(Integer processId, ProcessPartVO part, ProcessState state)
            throws ProcessException {
        return ps.createProcessPart(processId, part.uri, ProcessHelper.buildParametersString(part.parametersMap),
                state);
    }

    public Integer createProcessPart(Integer processId, ProcessPartVO part) throws ProcessException {
        return createProcessPart(processId, part, ProcessState.WAITED);
    }

    public void sendProcessParts(ProcessVO pvo, ProcessType pt, List<ProcessPartVO> parts, int sessionCount,
                                 int jmsSize, Queue partsQueue) throws ProcessException {
        int partsSize = parts.size();
        int initialSize = sessionCount > 0 ? sessionCount * jmsSize : partsSize;
        ArrayList<Integer> list = new ArrayList<>(Math.min(partsSize, initialSize));
        if (parts.isEmpty()) {
            list.add(DbEntity.NOT_EXIST_ID);
            sendProcessParts(pvo, pt.getGroup(), list, jmsSize, partsQueue);
            return;
        }

        Integer processId = pvo.getId();
        int curSize = 0;
        for (ProcessPartVO part: parts) {
            if (curSize++ < initialSize) {
                Integer id = createProcessPart(processId, part, ProcessState.WAITED);
                list.add(id);
            } else {
                createProcessPart(processId, part, ProcessState.NONE);
            }
        }
        sendProcessParts(pvo, pt.getGroup(), list, jmsSize, partsQueue);
    }

    public void sendProcessParts(ProcessVO pvo, ProcessType pt, int sessionCount, int jmsSize, Queue partsQueue)
            throws ProcessException {
        sendProcessParts(pvo, pt.getGroup(), takeNotStartedParts(pvo, sessionCount, jmsSize), jmsSize, partsQueue);
    }

    private void sendProcessParts(ProcessVO pvo, String group, List<Integer> ids, int jmsSize, Queue partsQueue)
            throws ProcessException {
        if (ids.size() <= jmsSize && ids instanceof ArrayList) {
            sendProcessPart(pvo, group, (ArrayList<Integer>) ids, partsQueue);
            return;
        }
        ArrayList<Integer> list = new ArrayList<>(jmsSize);
        for (Integer id : ids) {
            list.add(id);
            if (list.size() == jmsSize) {
                sendProcessPart(pvo, group, list, partsQueue);
                list.clear();
            }
        }
        if (!list.isEmpty()) {
            sendProcessPart(pvo, group, list, partsQueue);
        }
    }

    private List<Integer> takeNotStartedParts(ProcessVO pvo, int sessionCount, int batch) throws ProcessException {
        List<Integer> ids = ps.findNotStartedProcessParts(pvo.getId(), sessionCount * batch);
        if (ids.isEmpty()) {
            throw new ProcessException(String.format("no process parts for %s found", pvo));
        }
        ps.setProcessPartsState(ids, ProcessState.WAITED);
        return ids;
    }

    protected void sendProcessParts(ProcessVO pvo, URI[] parts, int startIndex, int count, int sessionCount,
                                    Queue partsQueue) throws ProcessException {

        int pId = pvo.getId();

        int pieceSize = getProcessPartBathSize();
        int allStartSize = pieceSize * sessionCount;
        int startMsgSize = (count > pieceSize) ? pieceSize : count;

        ArrayList<Integer> ids = null;
        int j = 0;
        List<ArrayList<Integer>> sends = new ArrayList<>();
        for (int i = 0; i < count; i++) {

            String uri = parts[i + startIndex].toString();

            if (i >= allStartSize) {
                ps.createProcessPart(pId, uri, buildPartParams(i, uri));

            } else {
                if (ids == null) {
                    int rest = count - i;
                    startMsgSize = pieceSize <= rest ? pieceSize : rest;
                    ids = new ArrayList<>(startMsgSize);
                    sends.add(ids);
                }

                ids.add(j++, ps.createProcessPart(pId, uri, buildPartParams(i, uri), ProcessState.WAITED));

                if (j >= startMsgSize) {
                    ids = null;
                    j = 0;
                }
            }
        }
        for (ArrayList<Integer> sendIds : sends) {
            sendProcessPart(pvo, null, sendIds, partsQueue);
        }
    }

    // to support an old approach
    protected void createProcessParts(ProcessVO pvo, URI[] parts, int startIndex, int count) throws ProcessException {
        int pId = pvo.getId();
        for (int i = 0; i < count; i++) {
            String uri = parts[i + startIndex].toString();
            ps.createProcessPart(pId, uri, buildPartParams(i, uri));
        }
    }

    public String buildProcessReport(int processId, boolean isOk, String repo, String... params) throws Exception {
        StringBuilder report = appendReportStart();
        List<ProcessPartVO> parts = ps.getProcessParts(processId);
        if (parts.isEmpty()) {
            throw new ProcessException("no report parts");
        }

        boolean[] signs;
        if (parts.get(0).uri.contains("xmlurls")) {
            signs = createReportForUrls(processId, report, parts, repo);
        } else {
            signs = createProcessReport(processId, report, parts, repo);
        }
        appendReportEnd(report, processId, signs[0], signs[1], params);
        return report.toString();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public TaskVO createProcessTask(int processId, String name, String lookup) throws ProcessException {
        return createDelayedProcessTask(processId, name, lookup, -1, null);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public TaskVO createDelayedProcessTask(int processId, String name, String lookup, long delay, Date date)
            throws ProcessException {
        TaskVO task = tm.addTask(name, delay, date, new TaskProcessHandler(processId, lookup));
        if (task == null) {
            throw new ProcessException("couldn't create a delayed task for the process", processId);
        }
        return task;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean asynchronousStart(int taskProcessId, Queue taskQueue) {
        TaskVO task = tm.findTask(taskProcessId);

        if (task != null) {
            tm.updateTask(taskProcessId, ProcessState.STARTED, false, null);
            if (tm.sendTask(task, taskQueue)) {
                return true;
            }
            tm.updateTask(taskProcessId, ProcessState.FAILED, null, task.getMessage());
            LOG.error(String.format("couldn't start task process [%d] asynchronously because of: %s",
                    task.getId(), task.getMessage()));
        } else {
            LOG.error(String.format("couldn't find task [%d] to start process", taskProcessId));
        }
        return false;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void asynchronousStart(ProcessVO pvo, String name, String lookup, Queue taskQueue) throws ProcessException {

        TaskVO task = createProcessTask(pvo.getId(), name, lookup);
        tm.updateTask(task.getId(), ProcessState.STARTED, false, null);

        if (!tm.sendTask(task, taskQueue)) {
            tm.updateTask(task.getId(), ProcessState.FAILED, null, task.getMessage());
            LOG.error(String.format("couldn't start %s asynchronously because of: %s", pvo, task.getMessage()));
            throw new ProcessException("couldn't start the process asynchronously", pvo.getId());
        }
        LOG.info(String.format("process %s will be started...", pvo));
    }

    private boolean[] createProcessReport(int processId, StringBuilder report, List<ProcessPartVO> parts,
                                          String repository) {
        boolean jobCompleted = true;
        boolean jobPassed = true;
        boolean[] ret = new boolean[]{false, false};

        for (ProcessPartVO part : parts) {

            if (!checkPart(part, ret)) {
                continue;
            }
            boolean completed = ret[0];
            if (jobPassed && !completed) {
                jobCompleted = false;
            }
            boolean passed = ret[1];
            if (jobPassed && !passed) {
                jobPassed = false;
            }

            String msg = passed ? null : (part.getMessage() == null ? "" : part.getMessage().replace(
                    "<message>", "").replace("</message>", ""));
            if (msg != null) {
                msg = StringEscapeUtils.escapeXml(msg);
            }
            appendReportResults(report, part.getId(), part.uri, completed, passed, msg);
            if (passed) {
                String dir = processId + "/" + part.getId();
                String err = FileUtils.buildFilePaths(repository, dir, report);
                if (err != null && !repeatBuildFilePaths(repository, dir, report)) {
                    appendBuildFilePathError(part.uri, report, err);
                }
            }
            appendReportEnd(report);
        }

        ret[0] = jobCompleted;
        ret[1] = jobPassed;
        return ret;
    }

    private boolean checkPart(ProcessPartVO part, boolean[] ret) {

        boolean completed = part.getState().isCompleted();
        if (!completed) {
            LOG.warn(String.format("uncompleted part %s [%d], state=%s", part.uri, part.getId(), part.getState()));
        }

        boolean success = part.getState().isSuccessful();
        if (!success) {
            LOG.debug(String.format("%d     %s     pass=%b success=%b", part.getId(), part.uri, completed, success));
        }
        ret[0] = completed;
        ret[1] = success;
        return completed;
    }

    private boolean[] createReportForUrls(int jobId, StringBuilder report, List<ProcessPartVO> parts,
                                          String repository) {

        SAXBuilder builder = new SAXBuilder();

        boolean jobCompleted = true;
        boolean jobPassed = true;
        boolean[] ret = new boolean[]{false, false};

        for (ProcessPartVO part : parts) {

            if (!checkPart(part, ret)) {
                continue;
            }
            boolean passed = ret[1];
            if (jobPassed && !passed) {
                jobPassed = false;
            }
            boolean completed = ret[0];
            if (jobPassed && !completed) {
                jobCompleted = false;
            }

            Document doc = parseDoc(part.uri, builder);
            if (doc == null) {
                LOG.error("Couldn't read " + part.uri);
                jobPassed = false;
                continue;
            }
            List<Element> files = doc.getRootElement().getChildren("file");
            for (Element file : files) {

                String filePath = file.getAttributeValue("href");
                appendReportResults(report, part.getId(), filePath, jobCompleted, jobPassed, null);
                String[] parts1 = filePath.split("/");
                String recordName = parts1[parts1.length - 1].replace(".xml", "");
                String dir = new StringBuilder().append(jobId).append("/").append(part.getId()).append("/").append(
                        recordName.substring(recordName.length() - EXTENSION_LENGTH)).append("/").append(
                        recordName).toString();

                String err = FileUtils.buildFilePaths(repository, dir, report);
                if (err != null && !repeatBuildFilePaths(repository, dir, report)) {
                    appendBuildFilePathError(parts1[parts1.length - 1], report, err);
                }
                appendReportEnd(report);
            }
        }

        ret[0] = jobCompleted;
        ret[1] = jobPassed;
        return ret;
    }

    private boolean repeatBuildFilePaths(final String repository, final String dir, final StringBuilder report) {
        RepeatableOperation ro = new RepeatableOperation() {
            @Override
            protected void perform() throws Exception {
                String err = FileUtils.buildFilePaths(repository, dir, report);
                if (err != null) {
                    throw new Exception(err);
                }
            }
        };
        return ro.performOperation();
    }

    private void appendBuildFilePathError(String recordName, StringBuilder report, String err) {
        String body = report.toString();
        body = body.replace(recordName + "\" completed=\"true\" successful=\"true\"",
                recordName + "\" completed=\"true\" successful=\"false\" message=\"" + err + "\"");
        report.delete(0, report.length());
        report.append(body);
    }

    private StringBuilder appendReportResults(StringBuilder sb, boolean completed, boolean passed) {
        return sb.append(COMPLETED).append(completed).append(SUCCESSFUL).append(passed).append("\"");
    }

    private StringBuilder appendReportEnd(StringBuilder sb) {
        sb.append("</output>");
        sb.append("</result>");
        return sb;
    }

    private StringBuilder appendReportResults(StringBuilder sb, long id, String url, boolean completed, boolean passed,
                                              String msg) {
        sb.append("<result id=\"").append(id).append("\" url=\"").append(url);
        appendReportResults(sb, completed, passed);

        if (msg != null) {
            sb.append(" message=\"").append(msg.replaceAll("\"", "'")).append("\"");
        }
        return sb.append(">").append("<output>");
    }

    private StringBuilder appendReportStart() {
        StringBuilder report = new StringBuilder();
        return report.append("<?xml version=\"1.0\"?>").append("<results>");
    }

    private Document parseDoc(String uri, SAXBuilder builder) {
        try {
            return builder.build(new StringReader(FileUtils.readStream(new URI(uri))));

        } catch (Exception e) {
            LOG.error(e);
        }
        return null;
    }

    public String buildProcessReport(int processId, String message, boolean isOk) {

        boolean jobCompleted = true;
        boolean jobPassed = true;
        boolean[] ret = new boolean[]{false, false};

        StringBuilder report = appendReportStart().append(" <files>");

        List<ProcessPartVO> parts = ps.getProcessParts(processId);

        for (ProcessPartVO part : parts) {

            if (!checkPart(part, ret)) {
                continue;
            }

            boolean completed = ret[0];
            if (jobPassed && !completed) {
                jobCompleted = false;
            }

            boolean passed = ret[1];
            if (jobPassed && !passed) {
                jobPassed = false;
            }

            String uri = part.uri;
            report.append("  <file uri=\"").append(uri);
            appendReportResults(report, completed, passed).append(">");

            String msg = part.getMessage();

            if (msg == null && !completed) {
                LOG.warn(String.format("part %s was interrupted, processId=%d, state=%s",
                        part.uri, processId, part.getState()));
                msg = createInterruptedMessage();
            }

            report.append(msg).append("  </file>");
        }
        report.append(" </files>");
        if (isOk) {
            jobPassed = true;
        }
        if (message != null) {
            appendReportEnd(report, processId, jobCompleted, jobPassed, "\" message=\"", message);
        } else {
            appendReportEnd(report, processId, jobCompleted, jobPassed);
        }
        return report.toString();
    }

    private void appendReportEnd(StringBuilder report, int processId, boolean completed, boolean passed,
                                 String... messages) {

        report.append(" <job id=\"").append(processId);
        appendReportResults(report, completed, passed);
        for (String msg : messages) {
            report.append(" ").append(msg);
        }
        report.append("/></results>");
    }

    private String createInterruptedMessage() {
        return "<messages><message>Checking was interrupted</message></messages>";
    }

    private boolean hasNext4Type(ProcessVO pvo, ProcessType pt) {
        return pvo.hasNext() && pt.hasNext();
    }

    private void deleteProcess(ProcessVO pvo, ProcessState state, boolean stopCreator) {
        deleteProcess(pvo.getId());
        if (stopCreator && pvo.hasCreator()) {
            stopCreator(getProcess(pvo.getCreatorId()), state);
        }
    }
}
