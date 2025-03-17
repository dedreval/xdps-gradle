package com.wiley.cms.cochrane.cmanager;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.repository.IRepository;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public class DeliveryPackageInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String dfName;
    private final int issueId;
    private final String dbName;
    private int dbId;
    private final int dfId;

    private SortedMap<String, List<String>> records = new TreeMap<>();
    private final SortedMap<String, String> recordPaths = new TreeMap<>();
    private final Set<String> recordsWithRawData = new HashSet<>();

    /** group -> {file name -> translation} */
    private Map<String, Map<String, TranslatedAbstractVO>> revmanAbstracts;

    private int statAll;
    private int statBeingProcessed;
    private String currentGroup;

    public DeliveryPackageInfo(int issueId, String dbName, int clDbId, int dfId, String dfName) {
        this(issueId, dbName, dfId, dfName);
        dbId = clDbId;
    }

    public DeliveryPackageInfo(int issueId, String dbName, int dfId, String dfName) {
        this.issueId = issueId;
        this.dbName = dbName;
        this.dfId = dfId;
        this.dfName = dfName;
    }

    public String getDfName() {
        return dfName;
    }

    public int getIssueId() {
        return issueId;
    }

    public int getDbId() {
        return dbId;
    }

    public int getDfId() {
        return dfId;
    }

    public void setDbId(int dbId) {
        this.dbId = dbId;
    }

    public String getDbName() {
        return dbName;
    }

    public void setRecords(SortedMap<String, List<String>> value) {
        records = value;
    }

    public SortedMap<String, List<String>> getRecords() {
        return records;
    }

    public List<String> getRecordNames() {
        List<String> list = new ArrayList<>();
        list.addAll(records.keySet());
        return list;
    }

    public void moveTranslations(DeliveryPackageInfo to) {
        if (hasTranslations()) {
            if (to.hasTranslations()) {
                to.revmanAbstracts.putAll(revmanAbstracts);
                revmanAbstracts.clear();
            } else {
                to.revmanAbstracts = revmanAbstracts;
            }
        }
        revmanAbstracts = null;
    }

    public Set<String> getTranslationGroups() {
        if (hasTranslations()) {
            return revmanAbstracts.keySet();
        }
        return null;
    }

    public Map<String, TranslatedAbstractVO> getTranslations(String group) {
        if (hasTranslations()) {
            return revmanAbstracts.get(group);
        }
        return null;
    }

    public Map<String, TranslatedAbstractVO> removeTranslations(String group) {
        if (hasTranslations()) {
            return revmanAbstracts.remove(group);
        }
        return null;
    }

    public boolean hasTranslations() {
        return revmanAbstracts != null && !revmanAbstracts.isEmpty();
    }

    public void addTranslation(String groupName, String fileName, TranslatedAbstractVO tvo) {
        if (revmanAbstracts == null) {
            revmanAbstracts = new HashMap<>();
        }
        revmanAbstracts.computeIfAbsent(groupName, f -> new HashMap<>()).put(fileName, tvo);
    }

    public void addFile(String recordName, String fileName) {
        records.computeIfAbsent(recordName, f -> new ArrayList<>()).add(fileName);
        if (FilePathBuilder.containsRawData(fileName)) {
            recordsWithRawData.add(recordName);
        }
    }

    public void removeRecord(String recordName) {
        records.remove(recordName);
    }

    public SortedMap<String, String> getRecordPaths() {
        return recordPaths;
    }

    public String getRecordPath(String name) {
        return recordPaths.get(name);
    }

    public void addRecordPath(String recordName, String recordPath) {
        recordPaths.put(recordName, recordPath);
    }

    public boolean hasRecordPath(String recordName) {
        return recordPaths.containsKey(recordName);
    }

    public boolean hasRecordPaths() {
        return !recordPaths.isEmpty();
    }

    public Set<String> getRecordsWithRawData() {
        return recordsWithRawData;
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public void cleanInRepository(IRepository rp) {
        if (getRecords() == null) {
            return;
        }
        SortedMap<String, List<String>> recs = getRecords();
        try {
            for (List<String> files : recs.values()) {
                for (String file : files) {
                    rp.deleteFile(file);
                }
            }
        } catch (IOException e) {
            ContentManager.LOG.error(e, e);
        }
    }

    public int getStatAll() {
        return statAll;
    }

    public int getStatBeingProcessed() {
        return statBeingProcessed;
    }

    public void addStatAll() {
        statAll++;
    }

    public void addStatBeingProcessed() {
        statBeingProcessed++;
    }

    public void setCurrentGroup(String group) {
        this.currentGroup = group;
    }

    public String getCurrentGroup() {
        return currentGroup;
    }
}
