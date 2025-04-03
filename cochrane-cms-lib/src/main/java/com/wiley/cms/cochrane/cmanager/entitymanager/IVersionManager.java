package com.wiley.cms.cochrane.cmanager.entitymanager;

import java.util.Collection;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.PreviousVersionException;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/25/2020
 */
public interface IVersionManager {

    /**
     * Get full list of versions for a specific record
     * @param cdNumber        CD number of the record
     * @return  Ordered by pub & version numbers list of all versions
     */
    List<PrevVO> getVersions(String cdNumber);

    /**
     * Get full list of versions for a specific record on a specified Issue or the latest ones
     * @param fullIssueNumber  The Issue number where 0 means the latest versions
     * @param cdNumber         CD number of the record
     * @return  Ordered by pub & version numbers list of all versions
     */
    List<PrevVO> getVersions(int fullIssueNumber, String cdNumber);

    /**
     * Get latest version info for a specific record
     * @param cdNumber         CD number of the record
     * @return  The record's latest version
     */
    PrevVO getLastVersion(String cdNumber);

    /**
     * Get list of latest versions for a records list on a specified Issue
     * @param fullIssueNumber  The Issue number
     * @param cdNumbers        CD numbers list
     * @return   The latest versions list
     */
    List<PrevVO> getLastVersions(int fullIssueNumber, Collection<String> cdNumbers);

    /**
     * Get a version info for a specific record by its history version number
     * @param cdNumber            CD number of the record
     * @param versionNumber       The history version number where 0 means a latest version
     * @return     The version info by the record's version number
     */
    PrevVO getVersion(String cdNumber, Integer versionNumber);

    /**
     * It assigns the latest and future history versions for the new metadata uploaded,
     * and moves the previous metadata to history if pub number of the new metadata is changed
     * @param metadata  The new metadata
     * @return  The history metadata or NULL if it is not existing
     * @throws PreviousVersionException
     */
    ICDSRMeta populateMetadataVersion(ICDSRMeta metadata) throws PreviousVersionException;

    /**
     * It clears the previous CDSR repository for a specific record when it's being cleared
     * @param fullIssueNumber  The issue number where the the record is being cleared
     * @param cdNumber         CD number of the record
     * @return    The group name of the latest record
     */
    String clearVersionFolders(int fullIssueNumber, String cdNumber);

    /**
     * It clears the previous CDSR repository for a specific record
     * @param cdNumber         CD number of the record
     */
    void clearVersionFolders(String cdNumber);
}
