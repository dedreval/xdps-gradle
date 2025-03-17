package com.wiley.cms.cochrane.cmanager.parser;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 15.08.2011
 */
public class CmrUnitStatusParser extends TagUnitStatusParser {
    private static final Map<String, String> STATUSES;

    static {
        STATUSES = new HashMap<String, String>();
        STATUSES.put("NEW", "New");
    }

    @Override
    public Map<String, String> getStatuses() {
        return STATUSES;
    }
}
