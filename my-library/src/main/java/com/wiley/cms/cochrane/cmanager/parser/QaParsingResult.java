package com.wiley.cms.cochrane.cmanager.parser;

import java.util.List;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 28-Apr-2007
 */
public class QaParsingResult extends ParsingResult {

    private String message;
    private int goodCount;
    private List<String> badFiles;

    public List<String> getBadFiles() {
        return badFiles;
    }

    public void setBadFiles(List<String> badFiles) {
        this.badFiles = badFiles;
    }

    public int getGoodCount() {
        return goodCount;
    }

    public void setGoodCount(int goodCount) {
        this.goodCount = goodCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
