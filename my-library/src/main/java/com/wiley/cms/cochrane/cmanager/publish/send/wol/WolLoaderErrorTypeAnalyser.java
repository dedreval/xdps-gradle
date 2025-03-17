package com.wiley.cms.cochrane.cmanager.publish.send.wol;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 10.01.2018
 */
public class WolLoaderErrorTypeAnalyser {

    private final String[] ignorableErrors;
    private final String[] unrecoverableErrors;

    public WolLoaderErrorTypeAnalyser() {
        this(CochraneCMSProperties.getProperty("cms.cochrane.publish.wol.ignorable_errors", ""),
                CochraneCMSProperties.getProperty("cms.cochrane.publish.wol.unrecoverable_errors", ""));
    }

    public WolLoaderErrorTypeAnalyser(String jointIgnorableErrors, String jointUnrecoverableErrors) {
        this.ignorableErrors = splitErrors(jointIgnorableErrors);
        this.unrecoverableErrors = splitErrors(jointUnrecoverableErrors);
    }

    private static String[] splitErrors(String jointErrors) {
        String[] errors = StringUtils.split(jointErrors, "#");
        if (errors == null) {
            errors = ArrayUtils.EMPTY_STRING_ARRAY;
        }

        return errors;
    }

    public ErrorType getErrorType(String errMsg) {
        boolean ignorable, unrecoverable;
        ignorable = isErrorBelongsPatterns(errMsg, ignorableErrors);
        unrecoverable = !ignorable && isErrorBelongsPatterns(errMsg, unrecoverableErrors);

        return getErrorType(ignorable, unrecoverable);
    }

    private boolean isErrorBelongsPatterns(String errMsg, String[] patterns) {
        for (String pattern : patterns) {
            if (errMsg.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private ErrorType getErrorType(boolean ignorable, boolean unrecoverable) {
        ErrorType errorType;
        if (ignorable) {
            errorType = ErrorType.IGNORABLE;
        } else if (unrecoverable) {
            errorType = ErrorType.UNRECOVERABLE;
        } else {
            errorType = ErrorType.RECOVERABLE;
        }
        return errorType;
    }

    /**
     *
     */
    public enum ErrorType {

        IGNORABLE, RECOVERABLE, UNRECOVERABLE
    }
}
