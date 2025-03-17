package com.wiley.cms.cochrane.activitylog;

import java.util.ArrayList;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 02.06.2015
 */
public class EventFilter extends MultipleChoiceFilter {

    private static final long serialVersionUID = 1L;

    public EventFilter(ArrayList<ActivityLogEventVO> elements) {
        super(ActivityLogEntity.Field.EVENT, elements);
    }

    @Override
    protected FilterComponent getComponent(Object element, boolean selected) {
        ActivityLogEventVO vo = (ActivityLogEventVO) element;
        return new FilterComponent(vo.getId(), vo.getName(), selected);
    }

    @Override
    public void addSearchTemplate(StringBuilder strb) {
        strb.append(field.getColumnName()).append(" IN (");
        for (FilterComponent cmp : cmps) {
            if (cmp.isSelected()) {
                strb.append(cmp.getId()).append(",");
            }
        }
        strb.setCharAt(strb.length() - 1, ')');
    }
}
