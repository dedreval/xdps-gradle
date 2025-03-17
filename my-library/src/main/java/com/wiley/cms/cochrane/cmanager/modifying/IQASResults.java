package com.wiley.cms.cochrane.cmanager.modifying;

import java.util.Map;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IQASResults {
    String getQasResults();

    boolean isRenderAutoStart();

    boolean isQasCompleted();

    boolean isQasSuccessful();

    Map<String, String> getMessagesPassed();

    Map<String, String> getMessagesFailed();
}
