package com.wiley.cms.cochrane.cmanager.entitymanager;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.PreviousVersionException;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/25/2018
 */
public interface IRecordManager {
    
    void setMetadataImported(Integer metaId, Integer existedMetaId, int issue, int toImport) throws CmsJTException;

    int updateSPDRecordToCancel(ArchieEntry record, int dbType, int dfId) throws CmsJTException;

    int createRecord(BaseType baseType, ArchieEntry record, int dfId, Integer taDbId, boolean statsData, boolean aut)
            throws PreviousVersionException;

    int createRecord(BaseType baseType, CDSRMetaVO meta, GroupVO group, int dfId, boolean statsData, boolean aut,
                     int highPriority) throws PreviousVersionException;

    int createCDSRRecord(TranslatedAbstractVO tvo, ICDSRMeta meta, int dfId, Integer taDbId)
            throws PreviousVersionException;

    void addDbRecord(DbRecordVO dbVO, Date date, int dbType);

    void addTranslation(TranslatedAbstractVO record, int dfId, Integer taDbId, @NotNull Integer recordId, boolean aut)
            throws PreviousVersionException;

    void updateWhenReadyOnReceived(List<ArchieEntry> list, int dfId, boolean success, boolean cancel, boolean spd);

    void updateWhenReadyOnPublished(List<ArchieEntry> list, int dfId, boolean success);

    List<PublishedAbstractEntity> getWhenReadyByDeliveryPackage(int dfId);

    List<PublishedAbstractEntity> getWhenReadyByDeliveryPackage(String name, int dfId);

    void cancelWhenReady(int recordId, String cdNumber, int recordNumber, int dfId);

    List<RecordEntity> getUnfinishedRecords(int dbId);

    int setRecordState(Collection<Integer> oldStates, int newState, int clDbId, Collection<String> names);

    int setRecordState(int oldState, int newState, int clDbId, Collection<String> names);

    int setRecordState(int newState, int clDbId, Collection<String> names);

    RecordEntity setRecordState(int newState, int clDbId, String cdNumber);

    int setRecordFailed(Collection<Integer> oldStates, int clDbId, Collection<String> names, boolean qasCompleted,
                        boolean qas, boolean rendCompleted);

    int setRecordStateByDeliveryPackage(int oldState, int newState, int dfId, Collection<String> names);

    List<DbRecordVO> getLastRecords(Collection<String> recordNames, int dbType);

    List<DbRecordVO> getLastRecords(String recordName, int dbType);

    /**
     * Move new translations to entire, remove old ones
     * @param recordName   CD number
     * @param dfId         delivery file ID
     * @param version      a number of historical version, NULL means that the latest
     * @param updated      an updated flag meaning the record itself has an updated state
     * @return  a list of updated (added or removed) translations
     */
    List<DbRecordVO> moveTranslationsToEntire(String recordName, int dfId, Integer version, boolean updated);

    List<DbRecordVO> restoreTranslationsFromBackUp(int issueId, String recordName, Set<String> langsExisted);

    List<DbRecordVO> importTranslations(int recordNumber, Integer version, boolean removeExisting,
                                        Map<Integer, DbRecordVO> existed, Map<Integer, DbRecordVO> imported);

    List<DbRecordVO> restoreTranslations(int number, Integer dfId, Integer version) throws CmsJTException;

    List<String> getLanguages(String recordName);

    List<String> getLanguages(String recordName, boolean jats);

    List<String> getLanguages(String recordName, int version, boolean jats);

    int getTranslationCount(String language);

    int getTranslationUniqueCount();

    List<DbRecordVO> getTranslations(String language, int skip, int batchSize);

    List<DbRecordVO> getTranslations(int recordNumber, int dfId);

    List<DbRecordVO> getLastTranslations(int recordNumber);

    List<DbRecordVO> getLastTranslations(int skip, int batchSize);

    List<DbRecordVO> getAllTranslationHistory(int skip, int batchSize);
             
    List<DbRecordVO> getTranslationHistory(String language, int skip, int batchSize);

    List<DbRecordVO> getTranslationHistory(int recordNumber, Integer version);

    DbRecordVO getTranslation(int recordNumber, String language);

    DbRecordVO getTranslation(int recordNumber, String language, int dbId);

    Integer findDb(int issueId, int dbType);
}
