package com.wiley.cms.cochrane.cmanager.parser;

import org.apache.commons.lang.StringUtils;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.Record;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 26.01.2010
 */
public class EntireRndResultHandler extends RndResultHandler {
    public EntireRndResultHandler(int jobId, RndParsingResult result) {
        super(jobId, result);
    }

    protected void fillIn(String filePath, boolean completed) {
        String repository = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY);
        String dbName = StringUtils.substringBetween(filePath, repository + "/", "/entire");
        String recordName = filePath.substring(filePath.lastIndexOf("/") + 1, filePath.lastIndexOf(".xml"));
        result.setDbName(dbName);
        curr = new Record(recordName, completed);
    }
}
