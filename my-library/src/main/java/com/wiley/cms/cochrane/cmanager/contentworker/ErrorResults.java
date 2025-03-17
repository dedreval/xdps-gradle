package com.wiley.cms.cochrane.cmanager.contentworker;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.utils.ErrorInfo;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/4/2016
 */
public class ErrorResults {

    public final StringBuilder err;
    public final Set<String> externalResults;
    public final Map<String, ErrorInfo> curr = new HashMap<String, ErrorInfo>();

    public ErrorResults() {
        this(null);
    }

    public ErrorResults(Set<String> externalFailedResults) {
        err = new StringBuilder();
        externalResults = externalFailedResults;
    }
}
