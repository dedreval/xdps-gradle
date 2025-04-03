package com.wiley.cms.cochrane.cmanager.publish.send.wol;

import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 10.01.2018
 */
@Deprecated
public class WolLoaderResponse {

    private final boolean success;
    private final boolean repeat;
    private final int statusCode;
    private final List<String> sentUnits;
    private final String message;

    public WolLoaderResponse(boolean success, boolean repeat, int statusCode, List<String> sentUnits, String message) {
        this.success = success;
        this.repeat = repeat;
        this.statusCode = statusCode;
        this.sentUnits = sentUnits;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public List<String> getSentUnits() {
        return sentUnits;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "{success=" + success
                + ", tryAgain=" + repeat
                + ", resultCode=" + statusCode
                + ", message='" + message + '\'' + '}';
    }
}
