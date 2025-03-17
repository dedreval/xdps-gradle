package com.wiley.cms.cochrane.process;

import java.util.List;

import com.wiley.cms.process.IProcessManager;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 10.09.13
 */
public interface IConversionManager extends IProcessManager {

    @Deprecated
    void startRevmanConversion(Integer[] recordIds, int ta, boolean withPrevious, String logName, boolean async);

    @Deprecated
    void startRevmanConversion(Integer[] recordIds, int ta, boolean withPrevious,
                               String logName, boolean async, int nextId);

    void performRevmanConversion(int processId, String dbName, List<Integer> recordIds, boolean withPrevious,
        boolean withTa, boolean onlyTa) throws Exception;
}
