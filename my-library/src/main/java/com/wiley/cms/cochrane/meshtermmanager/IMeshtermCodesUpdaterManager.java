package com.wiley.cms.cochrane.meshtermmanager;

import com.wiley.cms.process.IProcessManager;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 16.12.2015
 */
public interface IMeshtermCodesUpdaterManager extends IProcessManager {

    @Deprecated
    void updateMeshtermCodes();

    void prepareMeshtermCodes(String user) throws Exception;
}
