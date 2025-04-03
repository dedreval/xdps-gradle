package com.wiley.cms.cochrane.activitylog;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 01.06.2015
 */
public abstract class FieldFilter implements Serializable {

    private static final long serialVersionUID = 1L;

    protected final ActivityLogEntity.Field field;
    protected ArrayList<FilterComponent> cmps;

    public FieldFilter(ActivityLogEntity.Field field, ArrayList<FilterComponent> cmps) {
        this.field = field;
        this.cmps = cmps;
    }

    public ActivityLogEntity.Field getField() {
        return field;
    }

    public ArrayList<FilterComponent> getComponents() {
        return cmps;
    }

    public abstract boolean isActive();

    public abstract void reset();

    public abstract void addSearchTemplate(StringBuilder strb) throws Exception;
}
