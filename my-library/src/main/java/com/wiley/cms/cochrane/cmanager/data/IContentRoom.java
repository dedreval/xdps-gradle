package com.wiley.cms.cochrane.cmanager.data;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/3/2019
 */
public interface IContentRoom {

    String getDbName();

    default Integer getIssueId() {
        return null;
    }

    default Integer getPackageId() {
        return null;
    }
}
