package com.wiley.cms.cochrane.cmanager.parser;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 15.08.2011
 */
public class CentralUnitStatusParser extends TagUnitStatusParser {
    private static final Map<String, String> STATUSES;

    private static final String NEW = "New";
    private static final String UPDATED = "Updated";
    private static final String WITHDRAWN = "Withdrawn from publication for reasons stated in the review";

    static {
        STATUSES = new HashMap<String, String>();
        STATUSES.put("NEW", NEW);
        STATUSES.put("UPDATED", UPDATED); //
        STATUSES.put("UPDATE", UPDATED);  // add two statuses for one status in database
        STATUSES.put("WITHDRAWN", WITHDRAWN);
    }

    @Override
    public Map<String, String> getStatuses() {
        return STATUSES;
    }
}
