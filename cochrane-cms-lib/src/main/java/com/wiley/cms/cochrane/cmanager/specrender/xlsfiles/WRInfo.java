package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 7/2/2020
 */
class WRInfo {
    int year;
    int month;
    final StatCounter pro = new StatCounter();
    final StatCounter rev = new StatCounter();
    int countPublished = 0;
    int countPublishedOthers = 0;
    int countMT = 0;
    int countTA = 0;
    int countTAOthers = 0;
    int countOnlyTA = 0;
    List<WhenReadyReportCreator.ArticleInfo> articles = new ArrayList<>();
    List<WhenReadyReportCreator.ArticleInfo> translations = new ArrayList<>();

    List<WhenReadyReportCreator.ArticleInfo> sortAll() {
        List<WhenReadyReportCreator.ArticleInfo> ret = new ArrayList<>();
        addAll(articles, ret);
        addAll(translations, ret);
        ret.sort((a1, a2) -> a2.getStart().compareTo(a1.getStart()));
        return ret;
    }

    private void addAll(List<WhenReadyReportCreator.ArticleInfo> from, List<WhenReadyReportCreator.ArticleInfo> to) {
        for (WhenReadyReportCreator.ArticleInfo ai: from) {
            if (ai.hasNoPackage()) {
                continue;
            }
            to.add(ai);
            if (ai.others == null) {
                continue;
            }
            for (WhenReadyReportCreator.ArticleInfo other: ai.others) {
                if (other.hasNoPackage()) {
                    continue;
                }
                to.add(other);
                if (other.archieNotified != null) {
                    countPublishedOthers++;
                }
                if (other.isTranslation()) {
                    countTAOthers++;
                }
            }
        }
    }

    static class StatCounter {
        final Map<Integer, Integer> statuses = new HashMap<>();
        int count = 0;
        int historyCount = 0;

        StatCounter() {
            statuses.put(RecordMetadataEntity.RevmanStatus.DELETED.dbKey, 0);
            statuses.put(RecordMetadataEntity.RevmanStatus.UPDATED.dbKey, 0);
            statuses.put(RecordMetadataEntity.RevmanStatus.WITHDRAWN.dbKey, 0);
            statuses.put(RecordMetadataEntity.RevmanStatus.NEW.dbKey, 0);
        }

        int getNew() {
            return statuses.get(RecordMetadataEntity.RevmanStatus.NEW.dbKey);
        }

        int getUpdated() {
            return statuses.get(RecordMetadataEntity.RevmanStatus.UPDATED.dbKey);
        }

        int getWithdrawn() {
            return statuses.get(RecordMetadataEntity.RevmanStatus.WITHDRAWN.dbKey);
        }

        void addCount(int val) {
            count += val;
        }

        void addHistoryCount(int val) {
            historyCount += val;
        }

        void addCountByStatus(int key, int val) {
            int newCount = getCountByStatus(key) + val;
            statuses.put(key, newCount);
        }

        int getCountByStatus(int key) {
            return statuses.get(key);
        }
    }
}

