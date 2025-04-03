package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;

import java.util.Collections;
import java.util.List;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/6/2018
 */
public class LiteratumCentralGenerator extends LiteratumGenerator {
    public LiteratumCentralGenerator(ClDbVO db) {
        super(db);
    }

    @Override
    protected List<String> getAssetsUris(int issueId, String dbName, String recordName, boolean outdated) {
        return Collections.emptyList();
    }

    @Override
    protected String getPathToMl3gRecord(int issueId, String dbName, String recordName) {
        return FilePathBuilder.ML3G.getPathToMl3gRecord(issueId, dbName, recordName, true);
    }

    @Override
    protected boolean addSbnToPath() {
        return false;
    }
}
