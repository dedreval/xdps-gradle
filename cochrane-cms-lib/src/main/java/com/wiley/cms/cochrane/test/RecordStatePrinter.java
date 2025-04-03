package com.wiley.cms.cochrane.test;

import java.util.Collection;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/6/2020
 */
public class RecordStatePrinter implements IRecordHook {

    @Override
    public void capture(Integer clDbId, Collection<String> cdNumbers, String operation) {
        Hooks.LOG.info(CochraneCMSBeans.getContentSupport().getRecordsState(clDbId, cdNumbers, operation));
    }

    @Override
    public void capture(Integer dfId, String operation) {
        Hooks.LOG.info(CochraneCMSBeans.getContentSupport().getRecordsStateByPackage(dfId, operation));
    }
}
