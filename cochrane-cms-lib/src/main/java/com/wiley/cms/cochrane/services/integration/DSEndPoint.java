package com.wiley.cms.cochrane.services.integration;

import java.util.Date;

import com.wiley.cms.cochrane.cmanager.publish.MonthlyScheduler;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/9/2022
 */
public final class DSEndPoint extends EndPoint {

    private DSEndPoint(String name, IEndPointLocation path) {
        super(name, path);
    }

    public static IEndPoint create(String name, IEndPointLocation path) {
        return new DSEndPoint(name, path);
    }

    @Override
    public Boolean testCall() {
        return testFtpCall(false);
    }

    @Override
    public String[] getSchedule() {
        IEndPointLocation path = getEndPointLocation();
        if (path != null && path.getBaseType().isCentral() && !path.isEntirePath()) {
            String schedule = MonthlyScheduler.getTemplate();
            Date date = MonthlyScheduler.getActualStartDate();
            return new String[] {
                schedule != null ? schedule : Constants.NA,
                date != null ? Now.formatDateUTC(date) : Constants.NA};
        }
        return super.getSchedule();
    }
}
