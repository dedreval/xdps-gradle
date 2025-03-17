package com.wiley.cms.cochrane.cmanager.entity;

import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.entity.DbEntity;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/17/2020
 */
@Entity
@Table(name = "COCHRANE_VERSION")
@NamedQueries({
        @NamedQuery(
                name = VersionEntity.QUERY_SELECT_VOS_BY_CD_NUMBER,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.PrevVO("
                        + "r.cdNumber, r.group.sid, r.version.pubNumber, r.version.futureHistoryNumber)"
                        + " FROM RecordMetadataEntity r WHERE r.cdNumber =:nu"
                        + " AND r.issue > " + Constants.SPD_ISSUE_NUMBER
                        + " AND r.version.historyNumber > " + RecordEntity.VERSION_SHADOW
                        + " ORDER BY r.version.pubNumber DESC, r.issue DESC, r.id DESC"
        ),                                                
        @NamedQuery(
                name = VersionEntity.QUERY_SELECT_VOS_BY_CD_NUMBERS,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.PrevVO("
                        + "r.cdNumber, r.group.sid, r.version.pubNumber, r.version.futureHistoryNumber)"
                        + " FROM RecordMetadataEntity r WHERE r.cdNumber IN (:nu)"
                        + " AND r.issue > " + Constants.SPD_ISSUE_NUMBER
                        + " AND r.version.historyNumber >" + RecordEntity.VERSION_SHADOW
                        + " ORDER BY r.version.pubNumber DESC, r.issue DESC, r.id DESC"
        ),
        @NamedQuery(
                name = VersionEntity.QUERY_SELECT_VOS_ON_ISSUE_AND_CD_NUMBER,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.PrevVO("
                        + "r.cdNumber, r.group.sid, r.version.pubNumber, r.version.futureHistoryNumber)"
                        + " FROM RecordMetadataEntity r WHERE r.cdNumber =:nu"
                        + " AND r.issue <=:i AND r.issue > " + Constants.SPD_ISSUE_NUMBER
                        + " AND r.version.historyNumber > " + RecordEntity.VERSION_SHADOW
                        + " ORDER BY r.version.pubNumber DESC, r.issue DESC, r.id DESC"
        ),
        @NamedQuery(
                name = VersionEntity.QUERY_SELECT_VOS_ON_ISSUE_AND_CD_NUMBERS,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.PrevVO("
                        + "r.cdNumber, r.group.sid, r.version.pubNumber, r.version.futureHistoryNumber)"
                        + " FROM RecordMetadataEntity r WHERE r.cdNumber IN (:nu)"
                        + " AND r.issue <=:i AND r.issue > " + Constants.SPD_ISSUE_NUMBER
                        + " AND r.version.historyNumber > "  + RecordEntity.VERSION_SHADOW
                        + " ORDER BY r.version.pubNumber DESC, r.issue DESC, r.id DESC"
        ),
        @NamedQuery(
                name = VersionEntity.QUERY_SELECT_VO_BY_CD_NUMBER_AND_VERSION,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.PrevVO("
                        + "r.cdNumber, r.group.sid, r.version.pubNumber, r.version.futureHistoryNumber)"
                        + " FROM RecordMetadataEntity r WHERE r.cdNumber =:nu AND r.version.futureHistoryNumber =:fv"
                        + " AND r.issue > " + Constants.SPD_ISSUE_NUMBER
                        + " ORDER BY r.issue DESC, r.id DESC"
        ),
        @NamedQuery(
                name = VersionEntity.QUERY_SELECT_VOS_BY_ISSUE_AND_CD_NUMBER,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.PrevVO("
                        + "r.cdNumber, r.group.sid, r.version.pubNumber, r.version.futureHistoryNumber)"
                        + " FROM RecordMetadataEntity r"
                        + " WHERE r.issue =:i AND r.cdNumber =:nu AND r.version.historyNumber > "
                        + RecordEntity.VERSION_SHADOW + " ORDER BY r.version.pubNumber DESC, r.id DESC"
        )
    })

public class VersionEntity extends DbEntity {
    static final String QUERY_SELECT_VO_BY_CD_NUMBER_AND_VERSION = "versionVOByCdNumberAndVersion";
    static final String QUERY_SELECT_VOS_BY_ISSUE_AND_CD_NUMBER = "versionVOByIssueAndCdNumber";
    static final String QUERY_SELECT_VOS_BY_CD_NUMBER = "versionsVOByCdNumber";
    static final String QUERY_SELECT_VOS_BY_CD_NUMBERS = "versionsVOByCdNumbers";
    static final String QUERY_SELECT_VOS_ON_ISSUE_AND_CD_NUMBER = "versionsVOOnIssueAndCdNumber";
    static final String QUERY_SELECT_VOS_ON_ISSUE_AND_CD_NUMBERS = "versionsVOOnIssueAndCdNumbers";

    private int recordNumber;
    private int pubNumber;
    private Integer curHistoryNumber = RecordEntity.VERSION_SHADOW;
    private Integer futureHistoryNumber = RecordEntity.VERSION_SHADOW;
    private String cochraneVersion;
    private boolean newDoi = false;

    public VersionEntity() {
    }

    public VersionEntity(int recordNumber, int pubNumber, String cochraneVersion, boolean newDoi) {
        this(recordNumber, pubNumber, RecordEntity.VERSION_SHADOW, cochraneVersion, newDoi);
    }

    public VersionEntity(int recordNumber, int pubNumber, Integer historyNumber, String cochraneVersion,
                         boolean newDoi) {
        setNumber(recordNumber);
        setPubNumber(pubNumber);
        setHistoryNumber(historyNumber);
        setCochraneVersion(cochraneVersion);
        setNewDoi(newDoi);
    }

    public static Query queryVersionsVO(String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_VOS_BY_CD_NUMBER).setParameter("nu", cdNumber);
    }

    public static Query queryVersionsVO(int fullIssueNumber, String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_VOS_ON_ISSUE_AND_CD_NUMBER).setParameter(
                "i", fullIssueNumber).setParameter("nu", cdNumber);
    }

    public static Query queryVersionsVO(int fullIssueNumber, Collection<String> cdNumbers, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_VOS_ON_ISSUE_AND_CD_NUMBERS).setParameter(
                "i", fullIssueNumber).setParameter("nu", cdNumbers);
    }

    public static Query queryVersionsVOByIssue(int fullIssueNumber, String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_VOS_BY_ISSUE_AND_CD_NUMBER).setParameter(
                "i", fullIssueNumber).setParameter("nu", cdNumber);
    }

    public static Query queryVersionVO(String cdNumber, EntityManager manager) {
        return queryVersionsVO(cdNumber, manager).setMaxResults(1);
    }

    public static Query queryVersionVO(String cdNumber, Integer version, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_VO_BY_CD_NUMBER_AND_VERSION).setParameter(
                "fv", version).setParameter("nu", cdNumber).setMaxResults(1);
    }

    //public static Query deleteVersions(int fullIssueNumber, Collection<String> cdNumbers, EntityManager manager) {
    //    return manager.createNamedQuery(QUERY_DELETE_BY_ISSUE_AND_CD_NUMBERS).setParameter(
    //            "i", fullIssueNumber).setParameter("nu", cdNumbers);
    //}

    //public static Query deleteVersions(Collection<Integer> numbers, EntityManager manager) {
    //    return manager.createNamedQuery(QUERY_DELETE_BY_NUMBERS).setParameter("nu", numbers);
    //}

    @Column(name = "number", nullable = false, updatable = false)
    public int getNumber() {
        return recordNumber;
    }

    public void setNumber(int number) {
        this.recordNumber = number;
    }

    @Column(name = "pub", nullable = false, updatable = false)
    public int getPubNumber() {
        return pubNumber;
    }

    public void setPubNumber(int pubNumber) {
        this.pubNumber = pubNumber;
    }

    @Column(name = "history", nullable = false)
    public Integer getHistoryNumber() {
        return curHistoryNumber;
    }

    public void setHistoryNumber(Integer historyNumber) {
        this.curHistoryNumber = historyNumber;
    }

    @Column(name = "future_history")
    public Integer getFutureHistoryNumber() {
        return futureHistoryNumber;
    }

    public void setFutureHistoryNumber(Integer historyNumber) {
        this.futureHistoryNumber = historyNumber;
    }

    @Column(name = "version", length = DbEntity.STRING_VARCHAR_LENGTH_SMALL, nullable = false, updatable = false)
    public String getCochraneVersion() {
        return cochraneVersion;
    }

    public void setCochraneVersion(String version) {
        cochraneVersion = version;
    }

    @Column(name = "new_doi", nullable = false)
    public boolean isNewDoi() {
        return newDoi;
    }

    public void setNewDoi(boolean value) {
        newDoi = value;
    }

    @Transient
    public boolean isVersionFinal() {
        return getHistoryNumber() >= RecordEntity.VERSION_LAST;
    }

    @Transient
    public boolean isVersionLatest() {
        return getHistoryNumber() == RecordEntity.VERSION_LAST;
    }

    @Transient
    public boolean isVersionShadow() {
        return getHistoryNumber() == RecordEntity.VERSION_SHADOW;
    }
    
    @Override
    public String toString() {
        return String.format("%d.pub%d (%s) v%d [%d]", recordNumber, pubNumber, cochraneVersion,
                curHistoryNumber, getId());
    }
}
