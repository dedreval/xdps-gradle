package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.Collections;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class LiteratumCentralGeneratorEntire extends LiteratumGeneratorEntire {

    public LiteratumCentralGeneratorEntire(EntireDbWrapper db) {
        super(db);
    }

    @Override
    protected List<String> getAssetsUris(String dbName, String recordName) {
        return Collections.emptyList();
    }

    @Override
    protected String getPathToMl3gRecord(String dbName, String recordName) {
        return FilePathBuilder.ML3G.getPathToEntireMl3gRecord(dbName, recordName, true);
    }

    @Override
    protected boolean addSbnToPath() {
        return false;
    }
}
