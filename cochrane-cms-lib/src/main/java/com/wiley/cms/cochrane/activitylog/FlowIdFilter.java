package com.wiley.cms.cochrane.activitylog;

import java.util.ArrayList;
import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/30/2021
 */
public class FlowIdFilter extends FieldFilter {

    private static final long serialVersionUID = 1L;

    private final FilterComponent cmp;

    public FlowIdFilter(ActivityLogEntity.Field field) {
        this(field, StringUtils.EMPTY);
    }

    private FlowIdFilter(ActivityLogEntity.Field field, String value) {
        super(field, new ArrayList<>(1));

        cmp = new FilterComponent(value);
        cmps.add(cmp);
    }

    @Override
    public boolean isActive() {
        return StringUtils.isNotEmpty((String) cmp.getValue());
    }

    @Override
    public void reset() {
        cmp.setValue(StringUtils.EMPTY);
    }

    @Override
    public void addSearchTemplate(StringBuilder sb) {
        String value = ((String) cmp.getValue()).trim();
        if (value.contains("[") && value.endsWith("]")) {
            value = value.replace("]", "");
            String[] parts = value.split("\\[");
            addToSearch("id", parts[parts.length - 1].trim(), sb);

        } else  {
            addToSearch(field.getColumnName(), value, sb);
        }
    }

    private static void addToSearch(String column, String value, StringBuilder sb) {
        sb.append(column).append("=").append(Long.parseLong(value));
    }
}
