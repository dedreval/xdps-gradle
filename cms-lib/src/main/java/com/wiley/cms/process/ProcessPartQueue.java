package com.wiley.cms.process;

import java.util.Iterator;
import java.util.List;

import javax.jms.Message;

import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 09.09.13
 *
 * @param <T> parent process handler
 */
public abstract class ProcessPartQueue<T extends ProcessHandler> {

    protected IProcessStorage storage = ProcessStorageFactory.getFactory().getInstance();

    protected abstract Logger log();

    protected void processPart(ProcessVO pvo, T handler, ProcessPartVO processPart) throws Exception {
        //log().debug(String.format("process part %s", processPart));
        handler.onMessage(pvo, processPart);
    }

    protected void processNextPart(ProcessVO pvo, List<ProcessPartVO> results, IProcessManager manager)
            throws ProcessException {
        manager.processNextPart(pvo, results);
    }

    protected void createProcessParts(ProcessVO pvo, ProcessHandler handler, IProcessManager manager)  {
        //ProcessType pt = pvo.getType();
        //if (pt.logOnStart()) {
        //    manager.logProcessStart(pvo, pt.getLogOnStart());
        //}
        try {
            log().debug(String.format("process [%d] is going to be executed ...", pvo.getId()));
            handler.onMessage(pvo);
            if (pvo.getType().countInMessage() == 0) {
                manager.endProcess(pvo, ProcessState.SUCCESSFUL);
            }
        } catch (Throwable e) {
            manager.endProcess(pvo, e);
        }
    }

    protected void acceptExternalParts(ProcessVO pvo, T handler, ExternalProcess ep, List<ProcessPartVO> parts)
            throws Exception {
        handler.onExternalMessage(pvo, ep);
        for (ProcessPartVO part: parts) {
            handler.onMessage(part);
        }
    }

    private void process(ProcessVO pvo, T handler, ProcessPartVO processPart) {
        try {
            processPart(pvo, handler, processPart);
            if (!processPart.getState().isFailed()) {
                processPart.setState(ProcessState.SUCCESSFUL);
            }

        } catch (Exception e) {
            log().error(String.format("processing %s failed: %s", processPart, e.getMessage()));
            processPart.setState(ProcessState.FAILED);
            processPart.addMessage(e.getMessage());
        }
    }

    protected void onMessage(Message message, IProcessManager manager, Class<T> handlerClass) {
        ProcessVO pvo = null;
        try {
            List<Integer> ids = JMSSender.getIntegerList(message);
            if (ids == null || ids.size() == 0) {
                log().error("empty message");
                return;
            }

            int size = ids.size();
            pvo = manager.findProcess(ids.remove(size - 1));
            if (!pvo.getState().isStarted()) {
                log().warn(String.format("process [%d] was stopped", pvo.getId()));
                return;
            }

            T handler = ProcessHandler.castProcessHandler(pvo.getHandler(), handlerClass);
            if (size == 1) {
                createProcessParts(pvo, handler, manager);

            } else if (pvo.getType().hasExternalChildren()) {
                acceptExternalParts(pvo, handler, ids, manager);
            } else {
                processParts(pvo, handler, ids, manager);
            }

        } catch (ProcessException pe) {
            if (pe.getCode().isRedelivery()) {
                //todo: check and redeliver
                handleError(pvo, manager, pe);

            } else {
                handleError(pvo, manager, pe);
            }
        } catch (Exception e) {
            handleError(pvo, manager, e);
        }
    }

    private void acceptExternalParts(ProcessVO pvo, T handler, List<Integer> partIds, IProcessManager pm)
            throws Exception {

        int jobId = partIds.get(0);
        ExternalProcess ep = storage.getExternalProcess(jobId);
        List<ProcessPartVO> parts = storage.getProcessParts(jobId);

        acceptExternalParts(pvo, handler, ep, parts);

        pm.deleteProcess(jobId, true);

        synchronized (pvo) {
            processNextPart(pvo, parts, pm);
        }
    }

    private void processParts(ProcessVO pvo, T handler, List<Integer> partIds, IProcessManager pm)
            throws ProcessException {
        storage.setProcessPartsState(partIds, ProcessState.STARTED, ProcessState.WAITED);
        List<ProcessPartVO> parts = storage.getProcessParts(partIds);
        for (ProcessPartVO ppr : parts) {
            if (!ppr.getState().isSuccessful()) {
                process(pvo, handler, ppr);
            }
        }
        synchronized (pvo) {
            int committedCount = commitStateMessage(pvo, handler, parts);
            if (committedCount < partIds.size()) {
                storage.setProcessPartsState(partIds, ProcessState.SUCCESSFUL, ProcessState.STARTED);
            }
            processNextPart(pvo, parts, pm);
        }
    }

    private int commitStateMessage(ProcessVO pvo, T handler, List<ProcessPartVO> parts) {
        int ret = 0;
        Iterator<ProcessPartVO> it = parts.iterator();
        boolean canWriteStats = pvo.getType().canWriteStats();
        IProcessStats stats = canWriteStats ? handler.readStats(pvo) : null;
        while (it.hasNext()) {
            ProcessPartVO partVO = it.next();
            ProcessState state = partVO.getState();
            if (partVO.getMessage() != null || state.isFailed()) {
                storage.setProcessPartState(partVO.getId(), state, partVO.getMessage());
                ret++;
                if (canWriteStats) {
                    stats = handler.writeStats(stats, partVO);
                }
            }
            if (state.isFailed()) {
                it.remove();
            }
        }
        if (canWriteStats) {
            pvo.setStats(stats);
        }
        return ret;
    }

    private void handleError(ProcessVO pvo, IProcessManager manager, Exception e) {
        log().error(e);
    }
}
