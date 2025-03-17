package com.wiley.cms.cochrane.services.integration;

import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.test.PackageChecker;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/9/2022
 */
public final class AriesEndPoint extends EndPoint {

    private AriesEndPoint(String name, IEndPointLocation path) {
        super(name, path);
    }

    public static IEndPoint create() {
        return new AriesEndPoint(PackageChecker.ARIES, PublishProfile.getProfile().get().getWhenReadyPubLocation(
                PubType.MAJOR_TYPE_ARIES, PubType.TYPE_ARIES_ACK_D, BaseType.getCDSR().get().getId()));
    }

    @Override
    public Boolean testCall() {
        return testFtpCall(true);
    }
}
