package com.wiley.cms.cochrane.services.integration;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/9/2022
 */
public final class UDWEndPoint extends EndPoint {

    private UDWEndPoint(String name, IEndPointLocation path) {
        super(name, path);
    }

    public static IEndPoint create(String name, IEndPointLocation path) {
        return new UDWEndPoint(name, path);
    }

    @Override
    public Boolean testCall() {
        return testFtpCall(false);
    }
}
