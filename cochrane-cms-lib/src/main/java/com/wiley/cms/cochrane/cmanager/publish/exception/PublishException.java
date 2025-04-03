package com.wiley.cms.cochrane.cmanager.publish.exception;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class PublishException extends Exception {
    public PublishException() {
        super();
    }

    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }

    public PublishException(String message) {
        super(message);
    }

    public PublishException(Throwable cause) {
        super(cause);
    }
}
