package com.wiley.cms.cochrane.process;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/10/2015
 */
public interface IOperationManager {

    void clearDB(int dbId, String dbName, String user);

    void performTerm2NumCreation(boolean download, String user);

    void updateMeshtermCodes(String user);

    void importRecords(int dbId, Integer[] recordIds, String user);

    void restoreRecords(int dbId, Integer[] recordIds, String user);
}
