package com.wiley.cms.cochrane.activitylog;

import java.util.ArrayList;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * @version 17.12.2018
 */
public class NumberEntryFilter extends FieldFilter {
    private static final long serialVersionUID = 1L;

    private final FilterComponent cmp;

    public NumberEntryFilter(ActivityLogEntity.Field field, Number value) {
        super(field, new ArrayList<>(1));

        cmp = new FilterComponent(value);
        cmps.add(cmp);
    }

    @Override
    public boolean isActive() {
        return cmp.getValue() != null;
    }

    @Override
    public void reset() {
        cmp.setValue(null);
    }

    @Override
    public void addSearchTemplate(StringBuilder sb) {
        sb.append(field.getColumnName()).append("=").append(cmp.getValue());
    }
}
