package com.wiley.cms.cochrane.services.integration;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/9/2022
 */
public final class WOLLITEndPoint extends EndPoint {

    private WOLLITEndPoint(String name, IEndPointLocation path) {
        super(name, path);
    }

    public static IEndPoint create(String name, IEndPointLocation path) {
        return new WOLLITEndPoint(name, path);
    }

    @Override
    public boolean isTestMode() {
        return CochraneCMSPropertyNames.isWollitPublishTestMode();
    }

    @Override
    public Boolean testCall() {
        return testFtpCall(true);
    }
}
