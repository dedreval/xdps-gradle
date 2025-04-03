package com.wiley.cms.cochrane.test;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 6/11/2020
 */
public class LogPatterns {

    /** s1: an action name, s2: an action subject, d3: s2 identifier */
    public static final String OP_OBJECT_START = "%s started for %s [%d]";
    public static final String OP_OBJECT_END = "%s completed for %s [%d]";

    /** s1: an action name, s2: an action subject, d3: s2 identifier, s4: a error message */
    public static final String OP_OBJECT_FAIL = "%s failed for %s [%d] because %s";

    /** s1: an action name, s2: an action owner, s3: an action subject, d4: s3 identifier */
    public static final String OP_OBJECT_STARTING_BY = "%s is being started by %s for %s [%d]";

    /** s1: an action name, s2: an action owner, s3: an action subject, d4: s3 identifier */
    public static final String OP_OBJECT_START_BY = "%s started by %s for %s [%d]";

    private LogPatterns() {
    }
}
