package com.wiley.cms.cochrane.activitylog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import static com.wiley.cms.cochrane.activitylog.ActivityLogEntity.Field;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 01.06.2015
 */
public class RequestParameters implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ActivityLogEntity.Field orderByField;
    private final boolean descOrder;
    private final ArrayList<FieldFilter> filters;
    private int begIdx;
    private int limit;

    public RequestParameters(Field orderByField, boolean descOrder, Collection<FieldFilter> filters) {
        this.orderByField = orderByField == null ? Field.DATE : orderByField;
        this.descOrder = descOrder;
        this.filters = new ArrayList<>(filters);
    }

    public RequestParameters(Field orderByField,
                             boolean descOrder,
                             Collection<FieldFilter> filters,
                             int begIdx,
                             int limit) {
        this(orderByField, descOrder, filters);
        this.begIdx = begIdx;
        this.limit = limit;
    }

    public ActivityLogEntity.Field getOrderByField() {
        return orderByField;
    }

    public boolean isDescOrder() {
        return descOrder;
    }

    public int getBeginIndex() {
        return begIdx;
    }

    public void setBeginIndex(int beginIndex) {
        this.begIdx = beginIndex;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String buildSearchTemplate() throws Exception {
        StringBuilder strb = new StringBuilder();
        for (FieldFilter filter : filters) {
            if (filter.isActive()) {
                if (strb.length() > 0) {
                    strb.append(" AND ");
                }
                filter.addSearchTemplate(strb);
            }
        }

        return strb.toString();
    }

    public int filterSize() {
        return filters.size();
    }
}
