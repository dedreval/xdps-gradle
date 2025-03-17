package com.wiley.cms.process;

import java.util.Date;

import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.entity.ProcessEntity;
import com.wiley.cms.process.res.ProcessType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 02.12.12
 */
public class ExternalProcess extends ProcessSupportVO {
    private static final long serialVersionUID = 1L;

    protected final ProcessType type;

    private final int creatorId;
    private int nextId;
    private Date startDate;
    private final int priority;
    private String label;

    public ExternalProcess(ProcessEntity pe) {
        this(pe.getId(), pe.getCreatorId(), ProcessType.empty(), pe.getNextId(), pe.getStartDate(), pe.getState(),
                pe.getPriority());
        setLabel(pe.getLabel());
        setMessage(pe.getMessage());
    }

    public ExternalProcess(int id, Date date) {
        this(id, DbEntity.NOT_EXIST_ID, ProcessType.empty(), DbEntity.NOT_EXIST_ID, date, ProcessState.NONE,
                IProcessManager.USUAL_PRIORITY);
    }

    public ExternalProcess(int id) {
        this(id, new Date());
    }

    public ExternalProcess() {
        this(DbEntity.NOT_EXIST_ID, new Date());
    }

    public ExternalProcess(int id, int creatorId, ProcessType type, int nextId, Date date, ProcessState state,
                           int priority) {
        super(id);

        this.creatorId = creatorId;
        this.type = type;
        this.nextId = nextId;
        startDate = date;
        this.priority = priority;
        setState(state);
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date date) {
        startDate = date;
    }

    public boolean hasCreator() {
        return creatorId != DbEntity.NOT_EXIST_ID;
    }

    public int getCreatorId() {
        return creatorId;
    }

    public int getPriority() {
        return priority;
    }

    public int getNextId() {
        return nextId;
    }

    public void setNextId(int nextId) {
        this.nextId = nextId;
    }

    public boolean hasNext() {
        return nextId != DbEntity.NOT_EXIST_ID;
    }

    public boolean hasEmptyType() {
        return ProcessType.isEmpty(type.getId());
    }

    @Override
    public String toString() {
        return toString(startDate, creatorId, getId());
    }

    public static String toString(Object label, int creatorId, int id) {
        return String.format("%s [%d]-[%d]", label, creatorId, id);
    }
}

