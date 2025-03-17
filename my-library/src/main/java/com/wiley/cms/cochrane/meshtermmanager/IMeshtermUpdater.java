package com.wiley.cms.cochrane.meshtermmanager;

import java.util.Map;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 09.11.2009
 */

public interface IMeshtermUpdater {
    void updateMeshterms(Map<String, String> sourcePaths, int issue, int pckId, String pckName) throws Exception;

    void updateMeshterms(Map<String, String> sourcePaths) throws Exception;
}
