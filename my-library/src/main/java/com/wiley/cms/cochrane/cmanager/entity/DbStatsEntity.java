package com.wiley.cms.cochrane.cmanager.entity;

import java.util.Collection;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.IDbStats;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/16/2020
 */
@Entity
@Table(name = "COCHRANE_STATS")
@NamedNativeQueries({
        @NamedNativeQuery(
                name = DbStatsEntity.PRO_STATS_ENTIRE,
                query = "CALL statsEntire4All(?, @totalCDSR, @totalReviewCDSR, @totalProtocolCDSR,"
                        + " @totalWithdrawnReviewCDSR, @totalWithdrawnProtocolCDSR,"
                        + " @totalCENTRAL, @totalEDITORIAL, @totalCCA)"
        ),
        @NamedNativeQuery(
                name = DbStatsEntity.PRO_RESULT_STATS_ENTIRE,
                query = "SELECT @totalCDSR, @totalReviewCDSR, @totalProtocolCDSR, @totalWithdrawnReviewCDSR,"
                        + " @totalWithdrawnProtocolCDSR, @totalCENTRAL, @totalEDITORIAL, @totalCCA"
        ),
        @NamedNativeQuery(
                name = DbStatsEntity.PRO_STATS_ISSUE,
                query = "CALL statsIssue4All"
                        + "(?, @totalCENTRAL, @totalNewCENTRAL, @totalUpdatedCENTRAL, @totalEDITORIAL, @totalCCA)"
        ),
        @NamedNativeQuery(
                name = DbStatsEntity.PRO_RESULT_STATS_ISSUE,
                query = "SELECT @totalCENTRAL, @totalNewCENTRAL, @totalUpdatedCENTRAL, @totalEDITORIAL, @totalCCA"
        ),
        @NamedNativeQuery(
                name = DbStatsEntity.PRO_STATS_ISSUE_CDSR,
                query = "CALL statsCDSRIssue"
                        + "(?, @total, @reviewNew, @protocolNew, @reviewWithdrawnTotal, @protocolWithdrawnTotal,"
                        + " @protocolUpdated, @NSonly, @NSandCC, @CConly, @translations, @meshUpdated)"
        ),
        @NamedNativeQuery(
                name = DbStatsEntity.PRO_RESULT_STATS_ISSUE_CDSR,
                query = "SELECT @total, @reviewNew, @protocolNew, @reviewWithdrawnTotal, @protocolWithdrawnTotal,"
                        + " @protocolUpdated, @NSonly, @NSandCC, @CConly, @translations, @meshUpdated"
        ),
        @NamedNativeQuery(
                name = DbStatsEntity.PRO_STATS_SPD,
                query = "CALL statsSPDIssue"
                        + "(?, @total, @reviewNew, @protocolNew, @reviewWithdrawnTotal, @protocolWithdrawnTotal,"
                        + " @protocolUpdated, @NSonly, @NSandCC, @CConly)"
        ),
        @NamedNativeQuery(
                name = DbStatsEntity.PRO_RESULT_STATS_SPD,
                query = "SELECT @total, @reviewNew, @protocolNew, @reviewWithdrawnTotal, @protocolWithdrawnTotal,"
                        + " @protocolUpdated, @NSonly, @NSandCC, @CConly"
        )
        })
@NamedQueries({
        @NamedQuery(
                name = DbStatsEntity.QUERY_SELECT_BY_DB,
                query = "SELECT e FROM DbStatsEntity e WHERE e.clDbEntity.id=:db ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = DbStatsEntity.QUERY_SELECT_BY_ISSUE_AND_DB,
                query = "SELECT e FROM DbStatsEntity e WHERE e.issue=:i AND e.clDbEntity.id=:db ORDER BY e.id DESC"
        ),
        @NamedQuery(
                name = DbStatsEntity.QUERY_SELECT_BY_DBS,
                query = "SELECT e FROM DbStatsEntity e WHERE e.clDbEntity.id IN (:db) ORDER BY e.clDbEntity.id DESC"
        ),
        @NamedQuery(
                name = DbStatsEntity.QUERY_SELECT_BY_ISSUE,
                query = "SELECT e FROM DbStatsEntity e WHERE e.issue=:i ORDER BY e.clDbEntity.id DESC"
        )
        })
public class DbStatsEntity extends DbEntity implements IDbStats {
    static final String QUERY_SELECT_BY_DB = "statsByDb";
    static final String QUERY_SELECT_BY_ISSUE_AND_DB = "statsByIssueAndDb";
    static final String QUERY_SELECT_BY_DBS = "statsByDbs";
    static final String QUERY_SELECT_BY_ISSUE = "statsByIssue";

    static final String PRO_STATS_ENTIRE = "statsEntire4All";
    static final String PRO_RESULT_STATS_ENTIRE = "statsEntire4All_result";
    static final String PRO_STATS_ISSUE_CDSR = "statsCDSRIssue";
    static final String PRO_RESULT_STATS_ISSUE_CDSR = "statsCDSRIssue_result";
    static final String PRO_STATS_ISSUE = "statsIssue4All";
    static final String PRO_RESULT_STATS_ISSUE = "statsIssue4All_result";
    static final String PRO_STATS_SPD = "statsSPDIssue";
    static final String PRO_RESULT_STATS_SPD = "statsSPDIssue_result";

    private ClDbEntity dbEntity;
    private Date generationDate;
    private int issue;

    private int total = -1;
    private int reviews = -1;
    private int protocols = -1;
    private int totalNew = -1;
    private int reviewsNew = -1;
    private int protocolsNew = -1;
    private int totalUpdated = -1;
    private int reviewsUpdated = -1;
    private int protocolsUpdated = -1;
    private int reviewsWithdrawn = -1;
    private int protocolsWithdrawn = -1;

    private int onlyNewSearch = -1;
    private int newSearchAndCC = -1;
    private int onlyCC = -1;
    private int translations = -1;
    private int meshUpdated = -1;

    public DbStatsEntity() {
    }

    public DbStatsEntity(int fullIssueNumber, ClDbEntity dbEntity, Date date) {
        setGenerationDate(date);
        setClDbEntity(dbEntity);
        setIssue(fullIssueNumber);
    }

    public static Query queryStats(Integer dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB).setParameter(
                "db", dbId).setMaxResults(1);
    }

    public static Query queryStatsByIssue(int fullIssueNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_ISSUE).setParameter(
                "i", fullIssueNumber);
    }

    public static Query queryStats(Integer issueId, Integer dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_ISSUE_AND_DB).setParameter("i", issueId).setParameter(
                "db", dbId).setMaxResults(1);
    }

    public static Query queryStats(Collection<Integer> dbIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DBS).setParameter("db", dbIds);
    }

    public static void procedureEntireStats(int fullIssueNumber, DbStatsEntity cdsrStats, DbStatsEntity centralStats,
                                            DbStatsEntity edStats, DbStatsEntity ccaStats, EntityManager manager) {
        manager.createNamedQuery(PRO_STATS_ENTIRE).setParameter(1, fullIssueNumber).executeUpdate();
        Object[] res = (Object[]) manager.createNamedQuery(PRO_RESULT_STATS_ENTIRE).getSingleResult();
        int i = 0;
        cdsrStats.setTotal(castIntValue(res[i++]));
        cdsrStats.setTotalReviews(castIntValue(res[i++]));
        cdsrStats.setTotalProtocols(castIntValue(res[i++]));
        cdsrStats.setReviewsWithdrawn(castIntValue(res[i++]));
        cdsrStats.setProtocolsWithdrawn(castIntValue(res[i++]));
        centralStats.setTotal(castIntValue(res[i++]));
        edStats.setTotal(castIntValue(res[i++]));
        ccaStats.setTotal(castIntValue(res[i]));
    }

    public static void procedureIssueStats(int fullIssueNumber, DbStatsEntity centralStats, DbStatsEntity edStats,
                                           DbStatsEntity ccaStats, EntityManager manager) {
        manager.createNamedQuery(PRO_STATS_ISSUE).setParameter(1, fullIssueNumber).executeUpdate();
        Object[] res = (Object[]) manager.createNamedQuery(PRO_RESULT_STATS_ISSUE).getSingleResult();
        int i = 0;
        centralStats.setTotal(castIntValue(res[i++]));
        centralStats.setTotalNew(castIntValue(res[i++]));
        centralStats.setTotalUpdated(castIntValue(res[i++]));
        edStats.setTotal(castIntValue(res[i++]));
        ccaStats.setTotal(castIntValue(res[i]));
    }

    public static void procedureCDSRIssueStats(int issue, DbStatsEntity cdsrStats, EntityManager manager) {

        boolean spd = CmsUtils.isScheduledIssueNumber(issue);

        manager.createNamedQuery(spd ? PRO_STATS_SPD : PRO_STATS_ISSUE_CDSR).setParameter(1, issue).executeUpdate();
        Object[] res = (Object[]) manager.createNamedQuery(
                spd ? PRO_RESULT_STATS_SPD : PRO_RESULT_STATS_ISSUE_CDSR).getSingleResult();
        int i = 0;
        cdsrStats.setTotal(castIntValue(res[i++]));
        cdsrStats.setReviewsNew(castIntValue(res[i++]));
        cdsrStats.setProtocolsNew(castIntValue(res[i++]));
        cdsrStats.setReviewsWithdrawn(castIntValue(res[i++]));
        cdsrStats.setProtocolsWithdrawn(castIntValue(res[i++]));
        cdsrStats.setProtocolsUpdated(castIntValue(res[i++]));
        cdsrStats.setOnlyNS(castIntValue(res[i++]));
        cdsrStats.setNSAndCC(castIntValue(res[i++]));
        cdsrStats.setOnlyCC(castIntValue(res[i++]));
        if (!spd) {
            cdsrStats.setTranslations(castIntValue(res[i++]));
            cdsrStats.setMeshUpdated(castIntValue(res[i]));
        }
    }

    private static int castIntValue(Object res) {
        return ((Number) res).intValue();
    }

    @ManyToOne
    @JoinColumn(name = "db_id", nullable = false, updatable = false)
    public ClDbEntity getClDbEntity() {
        return dbEntity;
    }

    public void setClDbEntity(ClDbEntity clDbEntity) {
        dbEntity = clDbEntity;
    }

    @Column(name = "generation_date", nullable = false)
    public Date getGenerationDate() {
        return generationDate;
    }

    public void setGenerationDate(Date date) {
        generationDate = date;
    }

    @Column(name = "issue", nullable = false, updatable = false)
    public int getIssue() {
        return issue;
    }

    public void setIssue(int fullIssueNumber) {
        issue = fullIssueNumber;
    }

    @Column(name = "total", nullable = false)
    public int getTotal() {
        return total;
    }

    public void setTotal(int value) {
        total = value;
    }

    @Column(name = "total_reviews", nullable = false)
    public int getTotalReviews() {
        return reviews;
    }

    public void setTotalReviews(int value) {
        reviews = value;
    }

    @Column(name = "total_protocols", nullable = false)
    public int getTotalProtocols() {
        return protocols;
    }

    public void setTotalProtocols(int value) {
        protocols = value;
    }

    @Column(name = "total_new", nullable = false)
    public int getTotalNew() {
        return totalNew;
    }

    public void setTotalNew(int value) {
        totalNew = value;
    }

    @Column(name = "reviews_new", nullable = false)
    public int getReviewsNew() {
        return reviewsNew;
    }

    public void setReviewsNew(int value) {
        reviewsNew = value;
    }

    @Column(name = "protocols_new", nullable = false)
    public int getProtocolsNew() {
        return protocolsNew;
    }

    public void setProtocolsNew(int value) {
        protocolsNew = value;
    }

    @Column(name = "total_updated", nullable = false)
    public int getTotalUpdated() {
        return totalUpdated;
    }

    public void setTotalUpdated(int value) {
        totalUpdated = value;
    }

    @Column(name = "reviews_updated", nullable = false)
    public int getReviewsUpdated() {
        return reviewsUpdated;
    }

    public void setReviewsUpdated(int value) {
        reviewsUpdated = value;
    }

    @Column(name = "protocols_updated", nullable = false)
    public int getProtocolsUpdated() {
        return protocolsUpdated;
    }

    public void setProtocolsUpdated(int value) {
        protocolsUpdated = value;
    }

    @Column(name = "reviews_withdrawn", nullable = false)
    public int getReviewsWithdrawn() {
        return reviewsWithdrawn;
    }

    public void setReviewsWithdrawn(int value) {
        reviewsWithdrawn = value;
    }

    @Column(name = "protocols_withdrawn", nullable = false)
    public int getProtocolsWithdrawn() {
        return protocolsWithdrawn;
    }

    public void setProtocolsWithdrawn(int value) {
        protocolsWithdrawn = value;
    }

    @Column(name = "only_NS", nullable = false)
    public int getOnlyNS() {
        return onlyNewSearch;
    }

    public void setOnlyNS(int value) {
        onlyNewSearch = value;
    }

    @Column(name = "NS_CC", nullable = false)
    public int getNSAndCC() {
        return newSearchAndCC;
    }

    public void setNSAndCC(int value) {
        newSearchAndCC = value;
    }

    @Column(name = "only_CC", nullable = false)
    public int getOnlyCC() {
        return onlyCC;
    }

    public void setOnlyCC(int value) {
        onlyCC = value;
    }

    @Column(name = "translations", nullable = false)
    public int getTranslations() {
        return translations;
    }

    public void setTranslations(int value) {
        translations = value;
    }

    @Column(name = "mesh_updated", nullable = false)
    public int getMeshUpdated() {
        return meshUpdated;
    }

    public void setMeshUpdated(int value) {
        meshUpdated = value;
    }

    @Transient
    public String getDbName() {
        return dbEntity.getTitle();
    }
}
