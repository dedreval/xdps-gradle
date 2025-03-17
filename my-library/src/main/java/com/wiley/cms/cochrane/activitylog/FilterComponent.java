package com.wiley.cms.cochrane.activitylog;

import com.wiley.cms.cochrane.utils.Constants;

import java.io.Serializable;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 04.06.2015
 */
public class FilterComponent implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private Object value;
    private boolean selected;

    FilterComponent(Object value) {
        this(Constants.UNDEF, value, false);
    }

    FilterComponent(Object value, boolean selected) {
        this(Constants.UNDEF, value, selected);
    }

    FilterComponent(int id, Object value, boolean selected) {
        this.id = id;
        this.value = value;
        this.selected = selected;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
