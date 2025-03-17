package com.wiley.cms.cochrane.converter;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.05.2010
 */
public class ConverterException extends Exception {
    public ConverterException() {
        super();
    }

    public ConverterException(String message) {
        super(message);
    }

    public ConverterException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConverterException(Throwable cause) {
        super(cause);
    }
}
