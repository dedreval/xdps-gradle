package com.wiley.cms.cochrane.cmanager.translated;

import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.tes.util.Pair;

/**
 * @author Sergey Trofimov
 */
public interface ITranslatedAbstractsInserter {

    int MODE_NO_TA        = 0;
    int MODE_2_ONLY       = 1;     // for WOL
    int MODE_2_AND_3_AS_2 = 2;     // for WOL
    int MODE_3_AND_2_AS_3 = 3;     // for HW
    int MODE_3_ONLY       = 4;     // for HW

    int INSERT_ENTIRE = 0;
    int INSERT_ENTIRE_AND_ISSUE = 1;
    int INSERT_ISSUE = 2;
    int INSERT_EMPTY = 3;

    String getSourceForRecordWithInsertedAbstracts(IRecord record, String recordPath, int mode) throws Exception;

    String getSourceForRecordWithInsertedAbstracts(IRecord record, String recordPath, Integer issueId, Integer dfId,
        int mode, boolean hw) throws Exception;

    String getSourceForRecordWithInsertedAbstracts(IRecord recordName, String recordPath, Integer issueId, Integer dfId,
        int mode) throws Exception;

    void trackRecordsForWhenReadyPublish(Map<String, Pair<Integer, PublishEntity>> names, int clDbId, int dfId,
                                         String exportType, Set<String> namesToSkip);
}
