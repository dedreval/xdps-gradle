package com.wiley.cms.cochrane.activitylog;

import java.util.ArrayList;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 02.06.2015
 */
public class MultipleChoiceFilter extends FieldFilter {

    private static final long serialVersionUID = 1L;

    public MultipleChoiceFilter(ActivityLogEntity.Field field, ArrayList<?> elements) {
        this(field, elements, null);
    }

    public MultipleChoiceFilter(ActivityLogEntity.Field field, ArrayList<?> elements, Object activeElement) {
        super(field, new ArrayList<FilterComponent>(elements.size()));

        for (Object element : elements) {
            cmps.add(getComponent(element, element.equals(activeElement)));
        }
    }

    protected FilterComponent getComponent(Object element, boolean selected) {
        return new FilterComponent(element, selected);
    }

    @Override
    public boolean isActive() {
        for (FilterComponent cmp : cmps) {
            if (cmp.isSelected()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void reset() {
        for (FilterComponent cmp : cmps) {
            cmp.setSelected(false);
        }
    }

    @Override
    public void addSearchTemplate(StringBuilder strb) {
        String className = (cmps.isEmpty() ? null : cmps.get(0).getValue().getClass().getName());

        strb.append(field.getColumnName()).append(" IN (");
        for (FilterComponent cmp : cmps) {
            if (cmp.isSelected()) {
                strb.append(className).append(".").append(cmp.getValue()).append(",");
            }
        }
        strb.setCharAt(strb.length() - 1, ')');
    }
}
