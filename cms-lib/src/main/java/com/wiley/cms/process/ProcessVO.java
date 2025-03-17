package com.wiley.cms.process;

import java.util.Date;

import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.entity.ProcessEntity;
import com.wiley.cms.process.res.ProcessType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.06.13
 */
public class ProcessVO extends ExternalProcess {
    private static final long serialVersionUID = 1L;

    private String params;
    private ProcessHandler handler;

    private int canBeCompleted = 1;
    private final String owner;
    private int size = -1;

    public ProcessVO(ProcessEntity pe) {

        this(pe.getId(), pe.getCreatorId(), ProcessType.find(pe.getType()).get(), pe.getStartDate(), pe.getState(),
                pe.getPriority(), pe.getOwner());
        params = pe.getParams();
        setNextId(pe.getNextId());
        setMessage(pe.getMessage());
        setLabel(pe.getLabel());
    }

    public ProcessVO(ProcessEntity pe, ProcessHandler handler, ProcessType type) {
        this(pe.getId(), pe.getCreatorId(), type, pe.getStartDate(), pe.getState(), pe.getPriority(),
                pe.getOwner());
        this.handler = handler;
        setNextId(pe.getNextId());
        setMessage(pe.getMessage());
        setLabel(pe.getLabel());
    }

    public ProcessVO(int id, int creatorId, ProcessType type, Date date, ProcessState state, int priority,
        String owner) {

        super(id, creatorId, type, DbEntity.NOT_EXIST_ID, date, state, priority);
        this.owner = owner;
    }

    boolean isFreeToCompleted() {
        return canBeCompleted >= 1;
    }

    void setFreeToCompleted(boolean completed) {
        canBeCompleted = completed ? 1 : 0;
    }

    boolean isFreeToCompletedWithParent() {
        return canBeCompleted == 2;
    }

    public void setFreeToCompletedWithParent() {
        canBeCompleted = 2;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public ProcessHandler getHandler() {
        if (handler == null) {
            handler = ProcessHandler.createHandler(params);
            handler.setName(getLabel());
        }
        return handler;
    }

    public String getOwner() {
        return owner;
    }

    public ProcessType getType() {
        return type;
    }

    @Override
    public String toString() {
        return ExternalProcess.toString(getLabel(), getCreatorId(), getId());
    }
}

