package com.wiley.cms.cochrane.utils;

/**
 * @author <a href='mailto:sgulin@wiley.ru'>Svyatoslav Gulin</a>
 * @version 16.01.2012
 */
public class NonRetrievableException extends Exception {
    public NonRetrievableException() {
        super();
    }

    public NonRetrievableException(String message) {
        super(message);
    }

    public NonRetrievableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonRetrievableException(Throwable cause) {
        super(cause);
    }
}