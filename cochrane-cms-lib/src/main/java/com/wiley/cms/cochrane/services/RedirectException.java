package com.wiley.cms.cochrane.services;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class RedirectException extends Exception {
    public RedirectException() {
        super();
    }

    public RedirectException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedirectException(String message) {
        super(message);
    }

    public RedirectException(Throwable cause) {
        super(cause);
    }
}
