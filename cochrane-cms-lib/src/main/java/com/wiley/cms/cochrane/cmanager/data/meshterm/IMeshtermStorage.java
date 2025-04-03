package com.wiley.cms.cochrane.cmanager.data.meshterm;

import java.util.List;
import java.util.Set;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 03.11.2009
 */
public interface IMeshtermStorage {
    void deleteMeshtermRecord(Integer meshtermRecordId);

    void setIssue(String recordName, Integer issue);

    int createMeshtermRecord(String recordName, Integer descriptorId, Integer qualifierId);

    int findMeshtermRecordId(String recordName, Integer descriptorId, Integer qualifierId);

    int createQualifier(String qualifier, Boolean majorTopic);

    int createDescriptor(String descriptor, Boolean majorTopic);

    void createRecordDate(String recordName, Integer issue);

    boolean recordNameExists(String recordName);

    int findQualifierId(String qualifier, Boolean majorTopic);

    int findDescriptorId(String descriptor, Boolean majorTopic);

    List<Integer> getMeshtermRecordIds(String recordName);

    List<DescriptorEntity> getDescriptors(String recordName);

    List<QualifierEntity> getQualifiers(String recordName, DescriptorEntity descriptorEntity);

    List<String> findUpdatedRecords(Integer issue);

    List<String> findRecords(Set<String> recordNames);

    int getRecords4CheckCountByDbId(int dbId);

    void saveRecords4Check(List<MeshtermRecord4CheckEntity> record4CheckEntities, String dbName);

    List<MeshtermRecord4CheckEntity> getRecords4CheckNotCheckedByDbId(int dbId, int limit);

    List<MeshtermRecord4CheckEntity> getOutdatedRecords4Check(int dbId);

    void updateRecords4CheckStatus(List<Integer> ids, boolean checked);

    /**
     * Removes all MeshtermRecord4CheckEntities.
     */
    void deleteRecords4Check();

    /**
     * Removes all MeshtermRecord4CheckEntities associated to the specified db id.
     * @param dbId id of db.
     */
    void deleteRecords4Check(int dbId);

    long getRecordDescriptorsCountByRecordId(int id);

    void createRecordDescriptors(List<String> descs, MeshtermRecord4CheckEntity rEntity);

    /**
     * Removes all MeshtermRecordDescriptorEntities.
     */
    void deleteRecordDescriptors();

    /**
     * Removes all MeshtermRecordDescriptorEntities associated to the specified db id.
     * @param dbId id of db.
     */
    void deleteRecordDescriptors(int dbId);

    void addChangedDescriptors(List<String> descs);

    long getChangedDescriptorsCount();

    void deleteChangedDescriptors();
}
