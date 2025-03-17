package com.wiley.cms.cochrane.activitylog;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 02.06.2015
 */
public class TextEntryFilter extends FieldFilter {

    private static final long serialVersionUID = 1L;

    private final FilterComponent cmp;

    public TextEntryFilter(ActivityLogEntity.Field field) {
        this(field, StringUtils.EMPTY);
    }

    public TextEntryFilter(ActivityLogEntity.Field field, String value) {
        super(field, new ArrayList<FilterComponent>(1));

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
    public void addSearchTemplate(StringBuilder strb) {
        strb.append(field.getColumnName()).append(" like '%").append(parse((String) cmp.getValue())).append("%'");
    }

    private static String parse(String value) {
        if (StandardCharsets.ISO_8859_1.newEncoder().canEncode(value)) {
            return StringEscapeUtils.escapeSql(value);
        }
        throw new UnsupportedOperationException(String.format("only %s characters supported for this search",
                StandardCharsets.ISO_8859_1));
    }
}
