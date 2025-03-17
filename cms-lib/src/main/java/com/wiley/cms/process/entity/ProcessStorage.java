package com.wiley.cms.process.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;


import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.IProcessStorage;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.06.13
 */
@Stateless
@Local(IProcessStorage.class)
public class ProcessStorage extends AbstractModelController implements IProcessStorage {
    private static final Logger LOG = Logger.getLogger(ProcessStorage.class);

    @PersistenceContext
    private EntityManager em;

    @Override
    protected EntityManager getManager() {
        return em;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ProcessVO createProcess(ProcessHandler ph, ProcessType type, int priority) {
        return createProcess(DbEntity.NOT_EXIST_ID, ph, type, priority);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ProcessVO createProcess(int parentId, ProcessHandler ph, ProcessType type, int priority) {
        return createProcess(parentId, ph, type, priority, null);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ProcessVO createProcess(int parentId, ProcessHandler ph, ProcessType type, int priority, String owner) {
        return createProcess(parentId, ph, type, ph.getName(), priority, ProcessState.WAITED, owner);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ProcessVO createProcess(int parentId, ProcessHandler ph, ProcessType type, String label, int priority,
                                   ProcessState state, String owner) {
        ProcessEntity pe = new ProcessEntity(parentId, ph, label, state);
        pe.setPriority(priority);
        pe.setType(type.getId());
        pe.setOwner(owner);

        em.persist(pe);
        return new ProcessVO(pe, ph, type);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ProcessVO getProcess(int processId) {
        ProcessEntity pe = em.find(ProcessEntity.class, processId);
        if (pe != null) {
            return new ProcessVO(pe);
        }
        return null;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ExternalProcess getExternalProcess(int processId) {
        ProcessEntity pe = em.find(ProcessEntity.class, processId);
        if (pe != null) {
            return new ExternalProcess(pe.getId(), pe.getCreatorId(), ProcessType.get(pe.getType(),
                f -> new ProcessType(pe.getType())).get(), pe.getNextId(), pe.getStartDate(), pe.getState(),
                    pe.getPriority());
        }
        return null;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<ExternalProcess> getExternalProcessChildren(int processId) {
        return (List<ExternalProcess>) ProcessEntity.queryProcessChildren(processId, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<ExternalProcess> getExternalProcessChildren(int processId, ProcessState state, int limit) {
        return (List<ExternalProcess>) ProcessEntity.queryProcessChildren(processId, state, em).setMaxResults(
                limit).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public int getProcessChildrenCount(int processId) {
        return getSingleResultIntValue(ProcessEntity.queryProcessChildrenCount(processId, em));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getProcessChildrenCountNotState(int processId, Collection<ProcessState> states) {
        return getSingleResultIntValue(ProcessEntity.queryProcessChildrenCountNot(processId, states, em));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getProcessChildrenCount(int processId, Collection<ProcessState> states) {
        return getSingleResultIntValue(ProcessEntity.queryProcessChildrenCount(processId, states, em));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ProcessVO findProcess(int processId) throws ProcessException {
        return new ProcessVO(findProcessEntity(processId));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<ProcessVO> findProcesses(String label) {
        return (List<ProcessVO>) ProcessEntity.queryProcess(label, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<ProcessVO> findProcesses(int type) {
        return (List<ProcessVO>) ProcessEntity.queryProcess(type, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<ProcessVO> findProcesses(int parentId, Collection<ProcessState> states) {
        return ProcessEntity.queryProcess(parentId, states, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<ProcessVO> findProcesses(String label, Collection states) {
        return ProcessEntity.queryProcessOrderedByPriorityAndCreationDate(label, states, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteProcess(int processId) {

        ProcessPartEntity.queryDelete(processId, em).executeUpdate();
        ProcessEntity pe = em.find(ProcessEntity.class, processId);
        if (pe != null) {
            em.remove(pe);
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public ArrayList<Integer> findNotStartedProcessParts(int processId, int maxCount) {
        return (ArrayList<Integer>) ProcessPartEntity.queryProcessPartRecord(
                processId, ProcessState.NOT_STARTED_STATES, maxCount, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<ProcessPartVO> getProcessParts(int processId) {
        return (List<ProcessPartVO>) ProcessPartEntity.queryProcessPartRecords(
                processId, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @SuppressWarnings("unchecked")
    public List<Integer> getProcessPartUIDs(int processId, int skip, int batchSize) {
        return (List<Integer>) ProcessPartEntity.queryProcessPartRecordUIDs(
                    processId, skip, batchSize, em).getResultList();
    }

    public List<String> getProcessPartMessages(int processId, int skip, int batchSize) {
        return ProcessPartEntity.queryProcessPartRecordMessage(processId, skip, batchSize, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getProcessPartCount(int processId) {
        return getSingleResultIntValue(ProcessPartEntity.queryProcessPartRecordCount(processId, em));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getProcessPartCountInStates(int processId, List<ProcessState> states) {
        return getSingleResultIntValue(ProcessPartEntity.queryProcessPartRecordCount(processId, states, em));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public int getUnFinishedProcessPartCount(int processId) {
        return getSingleResultIntValue(ProcessPartEntity.queryProcessPartRecordCountNotInState(
                processId, ProcessState.FINISHED_STATES, em));
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setNextProcess(ExternalProcess pvo) throws ProcessException {
        ProcessEntity pe = findProcessEntity(pvo.getId());
        pe.setNextId(pvo.getNextId());

        em.merge(pe);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void updateProcess(int processId, boolean freeToCompleted) throws ProcessException {
        ProcessEntity pe = findProcessEntity(processId);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setProcessState(ProcessVO pvo, ProcessState state, String message) {

        int processId = pvo.getId();
        ProcessEntity pe = em.find(ProcessEntity.class, processId);
        if (pe == null) {
            return;
        }

        ProcessState oldState = pe.getState();

        pe.setState(state);
        if (state.isStarted()) {

            Date date = new Date();
            pe.setStartDate(date);
            pvo.setStartDate(date);
        }

        if (oldState != state) {
            pe.setLastDate(new Date());
        }

        pe.setMessage(message);

        em.merge(pe);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setProcessState(int processId, ProcessState state) {

        ProcessEntity pe = em.find(ProcessEntity.class, processId);
        if (pe == null) {
            return;
        }

        ProcessState oldState = pe.getState();

        pe.setState(state);
        if (ProcessState.isStarted(state)) {
            pe.setStartDate(new Date());
        }

        if (oldState != state) {
            pe.setLastDate(new Date());
        }

        em.merge(pe);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createProcessPart(int processId, String uri, String params) throws ProcessException {
        return createProcessPart(processId, uri, params, ProcessState.NONE);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int createProcessPart(int processId, String uri, String params, ProcessState state) throws ProcessException {

        ProcessEntity pe = findProcessEntity(processId);

        ProcessPartEntity pre = new ProcessPartEntity();
        pre.setParent(pe);
        pre.setUri(uri);
        pre.setState(state);
        pre.setParams(params);

        em.persist(pre);
        return pre.getId();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public ProcessPartVO startProcessPart(int processPartId) throws ProcessException {

        ProcessPartEntity ppe = findProcessPartEntity(processPartId);

        ProcessEntity parent = ppe.getParent();

        if (!parent.getState().isStarted()) { // || ProcessState.isStarted(ppe.getState())) {
            LOG.warn(String.format("process part [%d] parent [%d] was stopped", processPartId, parent.getId()));
            return null;
        }

        ppe.setState(ProcessState.STARTED);
        em.merge(ppe);

        return new ProcessPartVO(ppe);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ProcessPartVO getProcessPart(int processPartId) throws ProcessException {
        return new ProcessPartVO(findProcessPartEntity(processPartId));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<ProcessPartVO> getProcessParts(Collection<Integer> processPartIds) {
        return (List<ProcessPartVO>) ProcessPartEntity.queryProcessPartRecords(processPartIds, em).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setProcessPartState(int partId, ProcessState state, String msg) {

        ProcessPartEntity ppe = em.find(ProcessPartEntity.class, partId);
        if (ppe == null) {
            return;
        }
        ppe.setState(state);
        if (msg != null) {
            ppe.setMessage(msg);
        }
        em.merge(ppe);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setProcessPartsState(Collection<Integer> processPartIds, ProcessState state) {
        ProcessPartEntity.queryUpdateProcessPartRecordState(processPartIds, state, em).executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setProcessPartsState(Collection<Integer> partIds, ProcessState state, ProcessState... states) {
        ProcessPartEntity.queryUpdateProcessPartState(partIds, state, ProcessState.asList(states), em).executeUpdate();
    }

    private ProcessEntity findProcessEntity(int processId) throws ProcessException {
        ProcessEntity pe = em.find(ProcessEntity.class, processId);
        if (pe == null) {
            throw new ProcessException(String.format("process [%d] not found", processId));
        }
        return pe;
    }

    private ProcessPartEntity findProcessPartEntity(int partId) throws ProcessException {
        ProcessPartEntity ppe = em.find(ProcessPartEntity.class, partId);
        if (ppe == null) {
            throw new ProcessException(String.format("process part [%d] not found", partId));
        }
        return ppe;
    }
}
