package com.wiley.cms.cochrane.cmanager.data;

import com.wiley.cms.process.entity.DbEntity;

import javax.persistence.MappedSuperclass;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 20.06.2012
 */
@MappedSuperclass
public abstract class RecordPublishEntity extends DbEntity {

    public static final int CONVERSION = 6;
    public static final int CONVERTED = 1;
    public static final int CONVERSION_FAILED = 2;
    public static final int SENDING = 3;
    public static final int SENT = 4;
    public static final int SENDING_FAILED = 5;

    private static final List<Integer> CONVERTED_STATES = new ArrayList<Integer>();
    static {
        CONVERTED_STATES.add(CONVERSION);
        CONVERTED_STATES.add(CONVERTED);
        CONVERTED_STATES.add(SENDING);
        CONVERTED_STATES.add(SENT);
        CONVERTED_STATES.add(SENDING_FAILED);
    }

    private static final List<Integer> NOT_CONVERTED_STATES = new ArrayList<Integer>();
    static {
        NOT_CONVERTED_STATES.add(CONVERSION_FAILED);
    }

    private String name;
    private Date date;
    private int state;

    public static List<Integer> getConvertedStates() {
        return CONVERTED_STATES;
    }

    public static List<Integer> getNotConvertedStates() {
        return NOT_CONVERTED_STATES;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }
}
