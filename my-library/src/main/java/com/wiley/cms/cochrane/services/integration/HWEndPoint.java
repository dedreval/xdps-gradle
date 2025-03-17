package com.wiley.cms.cochrane.services.integration;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/9/2022
 */
public final class HWEndPoint extends EndPoint {

    private HWEndPoint(String name, IEndPointLocation path) {
        super(name, path);
    }

    public static IEndPoint create(String name, IEndPointLocation path) {
        return new HWEndPoint(name, path);
    }

    @Override
    public boolean isTestMode() {
        return CochraneCMSPropertyNames.isSemanticoPublishTestMode()
                && CochraneCMSPropertyNames.isSemanticoApiCallTestMode();
    }

    @Override
    public Boolean testCall() {
        Boolean ret1 = CochraneCMSPropertyNames.isSemanticoPublishTestMode() ? null : testFtpCall(true);
        Boolean ret2 = CochraneCMSPropertyNames.isSemanticoApiCallTestMode() ? null : testApiCall();
        if (ret1 != null && ret2 != null) {
            return ret1 && ret2;
        }
        return  ret1 != null ? ret1 : ret2;
    }

    private static Boolean testApiCall() {
        return null;
        //return HWClient.Factory.getFactory().getInstance().testCall();
    }
}
