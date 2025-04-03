package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 5/23/2020
  *
 * @param <A> A statistic item
 */
public class Statistic<A extends Statistic.Item> {
    final String dbName;
    /** CD number -> Pub number, Item*/
    Map<String, Map<Integer, A>> all = new TreeMap<>(Comparator.comparing(String::toString));

    int[] entireCount = {0, 0};
    int[] prevCount = {0, 0};
    int[] entireTaCount = {0, 0};
    int[] prevTaCount = {0, 0};

    final Map<String, List<String>> errors = new HashMap<>();
    private Set<String> specNames;

    public Statistic(String dbName) {
        this(dbName, null);
    }

    public Statistic(String dbName, Set<String> specNames) {
        this.dbName = dbName;
        this.specNames = specNames;
    }

    Map<Integer, A> getHistoryMap(String name) {
        return specNames != null && !specNames.contains(name) ? null
                : all.computeIfAbsent(name, f -> new TreeMap<>(Comparator.comparingInt(Integer::intValue)));
    }

    void addStats(boolean last, boolean jats, boolean ta, int count) {
        addStats(last ? (ta ? entireTaCount : entireCount) : (ta ? prevTaCount : prevCount), jats, count);
    }

    public Iterator<A> getItemIterator() {
        return new Iterator<A>() {

            private Iterator<A> sourceIt = null;
            private Iterator<Map<Integer, A>> sourceMapIt = all.values().iterator();

            @Override
            public A next() {
                return sourceIt == null ? null : sourceIt.next();
            }

            @Override
            public boolean hasNext() {
                return check(false);
            }

            private boolean check(boolean nextPart) {
                if (nextPart || sourceIt == null) {
                    if (sourceMapIt.hasNext()) {
                        sourceIt = sourceMapIt.next().values().iterator();
                    } else {
                        return false;
                    }
                }
                return sourceIt.hasNext() || check(true);
            }
        };
    }

    private void addStats(int[] stats, boolean jats, int count) {
        stats[jats ? 1 : 0] += count;
    }

    public int getEntireCount() {
        return entireCount[0] + getJatsCount();
    }

    public int getPreviousCount() {
        return prevCount[0] + getPreviousJatsCount();
    }

    public int getLastTranslationsCount() {
        return entireTaCount[0] + getJatsTranslationsCount();
    }

    public int getPreviousTranslationsCount() {
        return prevTaCount[0] + getPreviousJatsTranslationsCount();
    }

    public int getJatsCount() {
        return entireCount[1];
    }

    public int getJatsTranslationsCount() {
        return entireTaCount[1];
    }

    public int getNonJatsCount() {
        return entireCount[0];
    }

    public int getNonJatsTranslationsCount() {
        return entireTaCount[0];
    }

    public int getPreviousJatsCount() {
        return prevCount[1];
    }

    public int getPreviousJatsTranslationsCount() {
        return prevTaCount[1];
    }

    public int getPreviousNonJatsCount() {
        return prevCount[0];
    }

    public int getPreviousNonJatsTranslationsCount() {
        return prevTaCount[0];
    }

    void addError(String cdNumber, String err) {
        errors.computeIfAbsent(cdNumber, f -> new ArrayList<>()).add(err);
    }

    void addError(A item, String err) {
        addError(item.cdNumber, String.format("%s - %s", item, err));
    }

    void addError(A item, String language, String err) {
        addError(item.cdNumber, String.format("%s.%s - %s", item, language, err));
    }

    public String getErrors() {
        if (errors.isEmpty()) {
            return "no errors";
        }
        StringBuilder sb = new StringBuilder("\n");
        for (Map.Entry<String, List<String>> entry: errors.entrySet()) {
            List<String> errs = entry.getValue();
            sb.append(entry.getKey()).append("\n");
            errs.forEach(f -> sb.append(f).append("\n"));
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format(
            "articles: latest %d (%d/%d), previous %d (%d/%d); translations: latest %d (%d/%d), previous: %d (%d/%d)",
                getEntireCount(), getNonJatsCount(), getJatsCount(),
                getPreviousCount(), getPreviousNonJatsCount(), getPreviousJatsCount(),
                getLastTranslationsCount(), getNonJatsTranslationsCount(), getLastTranslationsCount(),
                getPreviousTranslationsCount(), getPreviousNonJatsTranslationsCount(),
                getPreviousJatsTranslationsCount());
    }

    /**
     * A statistic item
     */
    public static class Item {
        public final String cdNumber;
        public final String doi;
        public final int pub;
        public final Integer historyVersion;
        String language = null;

        String sid = null;
        String version = null;

        Item(String cdNumber, int pub, String doi, Integer historyVersion) {
            this.cdNumber = cdNumber;
            this.doi = doi;
            this.pub = pub;
            this.historyVersion = historyVersion;
        }

        public boolean hasLanguage() {
            return language != null;
        }

        void setSid(String sid) {
            this.sid = sid;
        }

        void setVersion(String version) {
            this.version = version;
        }

        public String getSid() {
            return getValue(sid);
        }

        public String getDoi() {
            return getValue(doi);
        }

        public String getPubNameAndLanguage() {
            return toString();
        }

        public String getVersion() {
            return getVersionValue(version);
        }

        public String getLanguage() {
            return getValue(language);
        }

        boolean isHistorical() {
            return historyVersion != null && historyVersion > RecordEntity.VERSION_LAST;
        }

        static String getYesOrNo(boolean value) {
            return value ? "+" : "-";
        }

        static String getValue(String value) {
            //return Optional.ofNullable(value).orElse(Constants.NA);
            return value == null || value.trim().length() == 0 ? Constants.NA : value;
        }

        static String getVersionValue(String value) {
            return value == null || value.trim().length() == 0 || ArchieEntry.NONE_VERSION.equals(value)
                    ? Constants.NA : value;
        }

        @Override
        public String toString() {
            String pubName = RevmanMetadataHelper.buildPubName(cdNumber, pub);
            return hasLanguage() ? String.format("%s.%s", pubName, language) : pubName;
        }
    }
}
