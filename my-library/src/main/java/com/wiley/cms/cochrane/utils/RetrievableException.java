package com.wiley.cms.cochrane.utils;

/**
 * @author <a href='mailto:sgulin@wiley.ru'>Svyatoslav Gulin</a>
 * @version 16.01.2012
 */
public class RetrievableException extends Exception {
    public RetrievableException() {
        super();
    }

    public RetrievableException(String message) {
        super(message);
    }

    public RetrievableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetrievableException(Throwable cause) {
        super(cause);
    }
}