package com.wiley.cms.cochrane.medlinedownloader;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.10.2009
 */
public class MedlineDownloaderException extends Exception {
    public MedlineDownloaderException() {
        super();
    }

    public MedlineDownloaderException(String message) {
        super(message);
    }

    public MedlineDownloaderException(Throwable t) {
        super(t);
    }

    public MedlineDownloaderException(String message, Throwable t) {
        super(message, t);
    }
}
