package com.wiley.cms.cochrane.cmanager.data.stats;

import java.util.List;

import com.wiley.cms.cochrane.cmanager.CmsException;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/16/2020
 */
public interface IStatsManager {

    void updateStats(Integer issueId, String user);

    void updateStats(int issueYear, int issueNumber, String user, boolean lastIssue) throws CmsException;

    List<IDbStats> getIssueStats(Integer issueId);

    List<IDbStats> getEntireStats(Integer issueId);

    List<IDbStats> getLastEntireStats();

    boolean isStatsUpdating();

    void generateReport(int fullIssueNumber, List<IDbStats> entireStats, List<IDbStats> issueStats);
}
