package com.wiley.cms.cochrane.cmanager.publish.generate.literatum;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;
import com.wiley.tes.util.Pair;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 2/12/2019
 */
public class WhenReadyDependency {

    private Map<String, Pair<Integer, PublishEntity>> currentNames = new HashMap<>();
    private Set<String> skippedNames = new HashSet<>();

    public void trackRecordsForWhenReadyPublish(ITranslatedAbstractsInserter taInserter, int dbId, int dfId,
                                         String exportTypeName) {
        if (!currentNames.isEmpty()) {
            taInserter.trackRecordsForWhenReadyPublish(currentNames, dbId, dfId, exportTypeName, skippedNames);
        }
        currentNames.clear();
        skippedNames.clear();
    }

    public void addRecord(String cdNumber, int dfId, PublishEntity export) {
        currentNames.put(cdNumber, new Pair<>(dfId, export));
    }

    void addSkippedRecord(String cdNumber) {
        skippedNames.add(cdNumber);
    }
}
