package com.wiley.cms.cochrane.cmanager.data.record;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DbRecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.CollectionCommitter;
import com.wiley.tes.util.DbUtils;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cochrane record entity bean.
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @author <a href='mailto:sgulin@wiley.ru'>Svyatoslav Gulin</a>
 * @version 1.0
 */
@Entity
@Table(name = "COCHRANE_RECORD")
@NamedQueries({
        @NamedQuery(
                name = "recordsPath",
                query = "SELECT r.recordPath from RecordEntity r where r.deliveryFile.id=:deliveryFile "
                        + "and  r.qasSuccessful=:success"
        ),
        @NamedQuery(
                name = "recordsPathByDb",
                query = "SELECT r.recordPath from RecordEntity r where r.db.id=:db and r.qasSuccessful = true"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_PATH_BY_DF,
                query = "SELECT r.recordPath FROM RecordEntity r WHERE r.deliveryFile.id=:dfId ORDER BY r.id"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_PATH_BY_IDS,
                query = "SELECT r.id, r.recordPath FROM RecordEntity r WHERE r.id IN (:id)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_IDS_BY_DF,
                query = "SELECT r.id FROM RecordEntity r WHERE r.deliveryFile.id = :dfId"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_PATH_BY_DF_AND_QA_STATUS,
                query = "SELECT r.name, r.recordPath, r.renderingSuccessful FROM RecordEntity r "
                        + "WHERE r.deliveryFile.id=:dfId AND r.qasSuccessful=:qs"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_DELIVERY_FILE,
                query = "SELECT r from RecordEntity r where r.deliveryFile=:df"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_DELIVERY_FILE_QAS_SUCCESSFUL,
                query = "SELECT r from RecordEntity r where r.deliveryFile=:df"
                        + " and r.qasSuccessful=true and r.renderingSuccessful=false"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_DELIVERY_FILE_ID_QAS_SUCCESSFUL,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.Record(r) from RecordEntity r "
                        + " WHERE r.deliveryFile.id=:dfId AND r.qasSuccessful=true and r.renderingSuccessful=false"
        ),
        @NamedQuery(
            name = RecordEntity.QUERY_SELECT_UNFINISHED_BY_DB_AND_NAMES,
            query = "SELECT r FROM RecordEntity r where r.name IN(:names) AND r.db.id=:dbId AND r.state NOT IN(:state)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_UNFINISHED_BY_DB,
                query = "SELECT r FROM RecordEntity r where r.db.id=:dbId AND r.state NOT IN(:state)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_STATE,
                query = "SELECT r FROM RecordEntity r WHERE r.state =:state"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_IDS_BY_STATE_AND_IDS,
                query = "SELECT r.id FROM RecordEntity r WHERE r.state =:st AND r.id IN(:id) "
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_STATE_AND_DB,
                query = "SELECT r FROM RecordEntity r WHERE r.db.id=:db AND r.state =:st"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_STATE_AND_DF,
                query = "SELECT r FROM RecordEntity r WHERE r.deliveryFile.id=:df AND r.state =:st"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_NAMES_BY_IDS,
                query = "SELECT r.name FROM RecordEntity r WHERE r.id IN (:id)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_DF_AND_NAMES,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.Record(r) FROM RecordEntity r"
                        + " WHERE r.deliveryFile.id=:dfId AND r.name in(:names)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_DF_IDS,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.record.RecordVO(r) FROM RecordEntity r"
                        + " WHERE r.deliveryFile.id IN (:dfId)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_STATE_BY_RECORD_ID,
                query = "UPDATE RecordEntity r SET r.state=:ns WHERE r.id=:id"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_STATE_BY_STATE_AND_DB,
                query = "UPDATE RecordEntity r SET r.state=:ns WHERE r.db.id=:dbId AND r.state=:os"
        ),
        @NamedQuery(
            name = RecordEntity.QUERY_UPDATE_STATE_BY_STATE_AND_DF,
            query = "UPDATE RecordEntity r SET r.state=:ns WHERE r.deliveryFile.id=:dfId AND r.state=:os"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_STATE_BY_DF,
                query = "UPDATE RecordEntity r SET r.state=:ns WHERE r.deliveryFile.id=:dfId AND r.state <> :ex"
        ),
        @NamedQuery(
                 name = RecordEntity.QUERY_UPDATE_STATE_BY_SUCCESS_AND_DF,
                 query = "UPDATE RecordEntity r SET r.state=:ns WHERE r.deliveryFile.id=:dfId"
                    + " AND r.renderingCompleted=true AND r.renderingSuccessful=true AND r.state NOT IN (:ex)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_STATE_BY_STATE_AND_DF_AND_NAMES,
                query = "UPDATE RecordEntity r SET r.state=:ns WHERE"
                    + " r.deliveryFile.id=:dfId AND r.state=:os AND r.name IN (:names)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_STATE_BY_STATES_AND_DB_AND_NAMES,
                query = "UPDATE RecordEntity r SET r.state=:ns WHERE"
                    + " r.db.id=:dbId AND r.state IN (:os) AND r.name IN (:names)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_STATE_BY_DB_AND_NAMES_EXCEPT,
                query = "UPDATE RecordEntity r SET r.state=:ns WHERE r.db.id=:db AND r.name IN (:na) AND r.state <> :ex"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_STATE_BY_DB_AND_NAMES,
                query = "UPDATE RecordEntity r SET r.state=:ns WHERE r.db.id=:db AND r.name IN (:na)"
        ),
        @NamedQuery(
            name = RecordEntity.QUERY_UPDATE_STATE_BY_STATE_AND_DB_AND_NAMES,
            query = "UPDATE RecordEntity r SET r.state=:ns WHERE r.db.id=:dbId AND r.state=:os AND r.name IN (:names)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SET_STATE_FAILED_BY_DB_AND_NAMES,
                query = "UPDATE RecordEntity r SET r.renderingSuccessful=:rs, r.renderingCompleted=:rc,"
                        + " r.qasSuccessful=:qs, r.qasCompleted=:qc, r.state=" + RecordEntity.STATE_WR_ERROR
                        + " WHERE r.db.id=:dbId AND r.state IN (:os) AND r.name IN (:names)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_RENDERING_STATE_QA_SUCCESS_BY_DF_IDS,
                query = "UPDATE RecordEntity r SET r.renderingSuccessful = :state"
                        + " WHERE r.qasSuccessful = true AND r.deliveryFile.id IN (:dfId)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_UPDATE_RENDERING_STATE_BY_IDS,
                query = "UPDATE RecordEntity r SET r.renderingSuccessful=:state, r.renderingCompleted=true"
                        + " WHERE r.id IN (:ids)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_RESET_RENDERING_STATE_BY_IDS,
                query = "UPDATE RecordEntity r SET r.renderingSuccessful=false, r.renderingCompleted=false"
                               + " WHERE r.id IN (:ids)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_DB_AND_NAME,
                query = "SELECT r FROM RecordEntity r WHERE r.db.id=:db AND r.name=:recordName"
        ),
        @NamedQuery(
                name = "recordByDbAndNamesWithUnitStatus",
                query = "select r from RecordEntity r where r.db=:db "
                        + "and r.unitStatus.id=:unitStatus and r.name in (:recordNames)"
        ),
        @NamedQuery(
                name = "recordByIds",
                query = "SELECT r FROM RecordEntity r WHERE r.id IN (:ids)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_DB_AND_NAMES,
                query = "select r from RecordEntity r where r.db.id=:dbId and r.name IN (:names)"
        ),
        @NamedQuery(
                name = "approvedRecordCount",
                query = "select count(r) from RecordEntity r where r.db=:db and r.approved=:a"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_RECORD_COUNT,
                query = "select count(r) from RecordEntity r where r.db=:db"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_RECORD_COUNT_BY_NAME,
                query = "select count(r) from RecordEntity r where r.db=:db and r.name=:recordName"
        ),
        @NamedQuery(
            name = RecordEntity.Q_RECORD_COUNT_RENDERING_FAILED_BY_DF_ID,
            query = "SELECT COUNT(r) FROM RecordEntity r WHERE r.deliveryFile.id=:dfId AND r.renderingSuccessful=false"
        ),
        @NamedQuery(
                name = RecordEntity.Q_RECORD_COUNT_RENDERING_FAILED_QA_SUCCESS_BY_DF_ID,
                query = "SELECT COUNT(r) FROM RecordEntity r WHERE r.deliveryFile.id = :dfId"
                        + " AND r.renderingSuccessful = false AND r.qasSuccessful=true"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_RECORD_COUNT_QA_BY_DF_ID_AND_SUCCESS,
                query = "SELECT COUNT(r) FROM RecordEntity r WHERE r.deliveryFile.id=:dfId AND r.qasSuccessful=:state"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_QAS_UNCOMPLETED_BY_DF_ID,
                query = "SELECT r FROM RecordEntity r WHERE r.deliveryFile.id=:df AND r.qasCompleted=false"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_RECORD_COUNT_RENDER_BY_DF_ID_AND_SUCCESS,
                query = "SELECT COUNT(r) FROM RecordEntity r WHERE r.deliveryFile.id=:df AND r.renderingSuccessful=:rs"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_BY_DB,
                query = "select r from RecordEntity r where r.db.id=:dbId"
        ),
        @NamedQuery(
                name = "rejectedRecordCount",
                query = "select count(r) from RecordEntity r where r.db=:db and r.rejected=:r"
        ),
        @NamedQuery(
                name = "recordCountByDFile",
                query = "select count(r) from RecordEntity r where r.deliveryFile.id=:df"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_RECORD_COUNT_RENDER_BY_DF_ID,
                query = "select count(r) from RecordEntity r where r.deliveryFile.id=:dfId "
                        + " and (r.qasSuccessful=true or r.qasCompleted=false)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_RECORD_COUNT_QAS_COMPLETED_BY_DF_ID,
                query = "SELECT COUNT(r) from RecordEntity r WHERE r.deliveryFile.id=:dfId AND r.qasCompleted=:state"
        ),
        @NamedQuery(
                name = "recordCountByDFileAndRndCompleted",
                query = "select count(r) from RecordEntity r where r.deliveryFile=:df and r.renderingCompleted=true"
        ),
        @NamedQuery(
                name = "recordCountByDFileRndSuccessful",
                query = "select count(r) from RecordEntity r where r.deliveryFile=:df "
                        + "and r.renderingCompleted=true and r.renderingSuccessful=true"
        ),
        @NamedQuery(
            name = RecordEntity.QUERY_RECORD_COUNT_RENDER_COMPLETED_BY_DF_ID,
            query = "SELECT COUNT(r) FROM RecordEntity r WHERE r.deliveryFile.id=:dfId AND (r.renderingCompleted=true"
                    + " OR r.state IN (" + RecordEntity.STATE_WR_ERROR + "," + RecordEntity.STATE_WR_ERROR_FINAL + "))"
        ),
        @NamedQuery(
                name = "recordGroupNameFromClabout",
                query = "select r from RecordEntity r where r.db=:db"
        ),
        @NamedQuery(
                name = "deleteRecordByDb",
                query = "delete from RecordEntity r where r.db=:db"
        ),
        @NamedQuery(
                name = "recordNewReviews",
                query = "SELECT r.name FROM RecordEntity r where r.db.id=:dbId and ("
                        + "r.unitStatus.id=" + UnitStatusEntity.UnitStatus.NEW
                        + " or  r.unitStatus.id=" + UnitStatusEntity.UnitStatus.NEW1
                        + " or  r.unitStatus.id=" + UnitStatusEntity.UnitStatus.NEW_COMMENTED + ")"
                        + "and r.renderingSuccessful=true and r.productSubtitle.id=1"
        ),
        @NamedQuery(
                name = "recordNewProtocols",
                query = "SELECT r.name FROM RecordEntity r where r.db.id=:dbId and ("
                        + "r.unitStatus.id=" + UnitStatusEntity.UnitStatus.NEW
                        + " or  r.unitStatus.id=" + UnitStatusEntity.UnitStatus.NEW1
                        + " or  r.unitStatus.id=" + UnitStatusEntity.UnitStatus.NEW_COMMENTED + ")"
                        + "and r.renderingSuccessful=true and (r.productSubtitle.id=2 or r.productSubtitle.id=11) "
        ),
        @NamedQuery(
                name = "recordUpdatedReviews",
                query = "SELECT r.name FROM RecordEntity r"
                        + " where r.db.id=:dbId and ("
                        + " r.unitStatus.id=" + UnitStatusEntity.UnitStatus.UPDATED
                        + " or r.unitStatus.id=" + UnitStatusEntity.UnitStatus.COMMENTED_AND_UPDATED
                        + " or r.unitStatus.id=" + UnitStatusEntity.UnitStatus.UPDATED_CON
                        + " or r.unitStatus.id=" + UnitStatusEntity.UnitStatus.UPDATED_NOT_CON
                        + " or r.unitStatus.id=" + UnitStatusEntity.UnitStatus.UPDATED_CON_COMMENTED
                        + " or r.unitStatus.id=" + UnitStatusEntity.UnitStatus.UPDATED_NOT_CON_COMMENTED
                        + " or r.unitStatus.id=" + UnitStatusEntity.UnitStatus.EDITED_CON
                        + " or r.unitStatus.id=" + UnitStatusEntity.UnitStatus.EDITED_CON_COMMENTED
                        + ") and r.renderingSuccessful=true and r.productSubtitle.id=1 "
        ),
        @NamedQuery(
                name = "getWithdrawnRecords",
                query = "SELECT r.name FROM RecordEntity r, ClDbEntity c"
                        + " WHERE (r.unitStatus.id = 5 OR r.unitStatus.id = 15 OR r.unitStatus.id = 25)"
                        + " AND r.db.database.name = :dbName AND c.issue.id = :issueId"
                        + " AND r.db.id = c.id ORDER BY r.name DESC "
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_SELECT_WITHDRAWN_RECORD_NAMES_BY_DF,
                query = "SELECT r.name FROM RecordEntity r WHERE r.db.id=:db AND r.deliveryFile.id=:df"
                        + " AND (r.unitStatus.id=5 OR r.unitStatus.id=15 OR r.unitStatus.id=25) ORDER BY r.name DESC"
        ),
        @NamedQuery(
                name = "recordUpdatedProtocols",
                query = "SELECT r.name FROM RecordEntity r where r.db.id=:dbId and ("
                        + " r.unitStatus.id=" + UnitStatusEntity.UnitStatus.MAJOR_CHANGE
                        + " or r.unitStatus.id=" + UnitStatusEntity.UnitStatus.MAJOR_CHANGE_COMMENTED
                        + ") and r.renderingSuccessful=true and (r.productSubtitle.id=2 or r.productSubtitle.id=11) "
        ),
        @NamedQuery(
                name = "cdsrRecords4Report",
                query = "SELECT R FROM RecordEntity R"
                        + " WHERE R.db.id = :dbId"
                        + " AND R.qasSuccessful = true"
                        + " AND R.renderingSuccessful = true"
                        + " AND R.unitStatus IS NOT NULL"
                        + " AND R.unitStatus.id NOT IN (:ignoredStatuses)"
        ),
        @NamedQuery(
                name = "recordByDbAndNames",
                query = "select r from RecordEntity r where r.db=:db and r.name in (:recordNames)"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_EDITORIAL_RECORDS_BY_ISSUE,
                query = "select r from RecordEntity r, ClDbEntity d "
                                + "where r.db.id=d.id and d.issue.id=:issueId and d.database.id=9 "
                                + "and r.qasSuccessful=true and r.renderingSuccessful=true order by r.id"
        ),
        @NamedQuery(
                name = RecordEntity.QUERY_EDITORIAL_RECORD_BY_ID,
                query = "select r from RecordEntity r, ClDbEntity d "
                                + "where r.db.id=d.id and r.id=:recordId and d.database.id=9 "
                                + "and r.qasSuccessful=true and r.renderingSuccessful=true"
        ),
        @NamedQuery(
            name = RecordEntity.QUERY_SELECT_TINY_VO_BY_IDS,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.data.record.TinyRecordVO(r.id, r.name,"
                            + "r.metadata.pubNumber, r.recordPath, r.productSubtitle.id, r.unitStatus.id,"
                            + "r.rawDataExists, r.deliveryFile.id, r.metadata.status,"
                            + "r.metadata.version.cochraneVersion, r.metadata.version.historyNumber,"
                            + "r.metadata.publishedIssue, r.metadata.publishedOnlineFinalForm, "
                            + "r.metadata.citationIssue, r.metadata.publishedOnlineCitation, r.metadata.firstOnline,"
                            + "r.metadata.protocolFirstIssue, r.metadata.reviewFirstIssue,r.metadata.selfCitationIssue,"
                            + "r.metadata.metaType) FROM RecordEntity r WHERE r.id IN (:id)"
        ),
        @NamedQuery(
            name = RecordEntity.QUERY_SELECT_TINY_VO_BY_DF_AND_NAMES,
            query = "SELECT new com.wiley.cms.cochrane.cmanager.data.record.TinyRecordVO(r.id, r.name,"
                            + "r.metadata.pubNumber, r.recordPath, r.productSubtitle.id, r.unitStatus.id,"
                            + "r.rawDataExists, r.deliveryFile.id, r.metadata.status,"
                            + "r.metadata.version.cochraneVersion, r.metadata.version.historyNumber,"
                            + "r.metadata.publishedIssue, r.metadata.publishedOnlineFinalForm, "
                            + "r.metadata.citationIssue, r.metadata.publishedOnlineCitation, r.metadata.firstOnline,"
                            + "r.metadata.protocolFirstIssue, r.metadata.reviewFirstIssue,r.metadata.selfCitationIssue,"
                            + "r.metadata.metaType) "
                            + "FROM RecordEntity r WHERE r.deliveryFile.id=:df AND r.name IN (:na)"
        )
    })

public class RecordEntity implements java.io.Serializable, IRecord {
    public static final int VERSION_INTERMEDIATE = -2;
    public static final int VERSION_LAST = 0;
    public static final int VERSION_SHADOW  = -1;
    public static final int STATE_UNDEFINED = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_WR_PUBLISHING = 2;
    public static final int STATE_WAIT_WR_PUBLISHED_NOTIFICATION = 3;
    public static final int STATE_WR_PUBLISHED = 4;
    public static final int STATE_CCH_PUBLISHING = 5;
    public static final int STATE_CCH_PUBLISHED = 6;
    public static final int STATE_WR_ERROR = 7;
    public static final int STATE_WR_ERROR_FINAL = 8;
    public static final int STATE_HW_PUBLISHING = 10;
    public static final int STATE_DS_PUBLISHING = 11;
    public static final int STATE_DS_PUBLISHED = 12;
    public static final int STATE_WAIT_WR_CANCELLED_NOTIFICATION = 13;
    public static final int STATE_HW_PUBLISHING_ERR = 14;

    public static final Set<Integer> UNPUBLISHED_STATES = new HashSet<>();
    public static final Set<Integer> PUBLISHED_STATES = new HashSet<>();
    public static final Set<Integer> AWAITING_PUB_EVENTS_STATES = new HashSet<>();
    public static final Set<Integer> FINAL_STATES = new HashSet<>();

    public static final String QUERY_SELECT_BY_DB = "recordsByDb";
    public static final String QUERY_SELECT_BY_DB_AND_NAME = "recordByDbAndName";
    public static final String QUERY_SELECT_BY_DELIVERY_FILE = "recordsByDeliveryFileAll";
    public static final String QUERY_SELECT_BY_DELIVERY_FILE_QAS_SUCCESSFUL = "recordsByDeliveryFileQasSuccessful";
    public static final String QUERY_SELECT_BY_DELIVERY_FILE_ID_QAS_SUCCESSFUL = "recordsByDeliveryFileIdQasSuccessful";
    public static final String QUERY_SELECT_UNFINISHED_BY_DB = "recordsUnfinishedByDb";
    public static final String QUERY_SELECT_UNFINISHED_BY_DB_AND_NAMES = "recordsUnfinishedByDbAndNames";
    public static final String QUERY_RECORD_COUNT = "recordCount";
    public static final String QUERY_RECORD_COUNT_BY_NAME = "recordCountByName";
    public static final String QUERY_EDITORIAL_RECORDS_BY_ISSUE = "editorialRecordsByIssue";
    public static final String QUERY_EDITORIAL_RECORD_BY_ID = "editorialRecordById";
    public static final String PARAM_DB_ID = "dbId";
    public static final String PARAM_DF_ID = "dfId";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_NAME  = "recordName";
    public static final String PARAM_NAMES = "names";
    static final String QUERY_SELECT_PATH_BY_DF = "recordsPathByDf";
    static final String QUERY_SELECT_PATH_BY_IDS = "recordsPathByIds";
    static final String QUERY_SELECT_IDS_BY_DF = "recordIdsByDf";
    static final String QUERY_SELECT_BY_STATE = "recordsByState";
    static final String QUERY_SELECT_IDS_BY_STATE_AND_IDS = "recordIdsByStateAndIds";
    static final String QUERY_SELECT_BY_STATE_AND_DB = "recordsByStateAndDb";
    static final String QUERY_SELECT_BY_STATE_AND_DF = "recordsByStateAndDf";
    static final String QUERY_SELECT_NAMES_BY_IDS = "recordNamesByIds";
    static final String QUERY_SELECT_PATH_BY_DF_AND_QA_STATUS = "recordPathsByDfAndQaStatus";
    static final String QUERY_SELECT_BY_DF_AND_NAMES = "recordsByDfAndNames";
    static final String QUERY_SELECT_BY_DB_AND_NAMES = "recordsByDbAndNames";
    static final String QUERY_SELECT_BY_DF_IDS = "recordsByDfIds";
    static final String QUERY_SELECT_WITHDRAWN_RECORD_NAMES_BY_DF = "withdrawnRecordNamesByDf";
    static final String QUERY_SELECT_QAS_UNCOMPLETED_BY_DF_ID = "recordsQasUncompletedByDfId";
    static final String Q_RECORD_COUNT_RENDERING_FAILED_BY_DF_ID = "recordCountRenderingFailedByDfId";
    static final String Q_RECORD_COUNT_RENDERING_FAILED_QA_SUCCESS_BY_DF_ID =
            "recordCountRenderingFailedQaSuccessByDfId";
    static final String QUERY_RECORD_COUNT_RENDER_COMPLETED_BY_DF_ID = "recordCountRenderCompletedByDfId";
    static final String QUERY_RECORD_COUNT_RENDER_BY_DF_ID_AND_SUCCESS = "recordCountRenderByDfIdAndSuccess";
    static final String QUERY_RECORD_COUNT_QA_BY_DF_ID_AND_SUCCESS = "recordCountQaByDfIdAndSuccess";
    static final String QUERY_RECORD_COUNT_QAS_COMPLETED_BY_DF_ID = "recordCountQasCompletedByDfId";
    static final String QUERY_RECORD_COUNT_RENDER_BY_DF_ID = "recordCountRenderByDfId";
    static final String QUERY_UPDATE_STATE_BY_RECORD_ID = "updateRecordStateByRecordId";
    static final String QUERY_UPDATE_STATE_BY_DF = "updateRecordStateByDf";
    static final String QUERY_UPDATE_STATE_BY_STATE_AND_DB = "updateRecordStateByStateAndDb";
    static final String QUERY_UPDATE_STATE_BY_STATE_AND_DF = "updateRecordStateByStateAndDf";
    static final String QUERY_UPDATE_STATE_BY_STATE_AND_DB_AND_NAMES = "updateRecordStateByStateAndDbAndNames";
    static final String QUERY_UPDATE_STATE_BY_DB_AND_NAMES = "updateRecordStateByStateAndNames";
    static final String QUERY_UPDATE_STATE_BY_DB_AND_NAMES_EXCEPT = "updateRecordStateByStateAndNamesExcept";
    static final String QUERY_UPDATE_STATE_BY_STATES_AND_DB_AND_NAMES = "updateRecordStateByStatesAndDbAndNames";
    static final String QUERY_UPDATE_STATE_BY_STATE_AND_DF_AND_NAMES = "updateRecordStateByStateAndDfAndNames";
    static final String QUERY_UPDATE_STATE_BY_SUCCESS_AND_DF = "updateRecordStateBySuccessAndDf";
    static final String QUERY_UPDATE_RENDERING_STATE_QA_SUCCESS_BY_DF_IDS = "updateRenderingStateQaSuccessByDfIds";
    static final String QUERY_UPDATE_RENDERING_STATE_BY_IDS = "updateRecordRenderStateByIds";
    static final String QUERY_RESET_RENDERING_STATE_BY_IDS = "resetRecordRenderStateByIds";
    static final String QUERY_SET_STATE_FAILED_BY_DB_AND_NAMES = "setStateFailedByDbAndNames";
    static final String QUERY_SELECT_TINY_VO_BY_IDS = "tinyRecordsVOByIds";
    static final String QUERY_SELECT_TINY_VO_BY_DF_AND_NAMES = "tinyRecordsVOByDfAndNames";

    private static final int STRING_MEDIUM_TEXT_LENGTH = 2097152;

    private Integer id;
    private String name;
    private TitleEntity titleEntity;
    private boolean qasCompleted;
    private boolean qasSuccessful;
    private boolean renderingCompleted;
    private boolean renderingSuccessful;
    private ClDbEntity db;
    private DeliveryFileEntity deliveryFile;
    private boolean approved;
    private boolean rejected;
    private boolean disabled;
    private boolean isRawDataExists;
    private String stateDescription;
    private String notes;
    private String recordPath;
    private boolean edited;
    private UnitStatusEntity unitStatus;
    private ProductSubtitleEntity productSubtitle;
    private DbRecordPublishEntity recordPublishEntity;
    private int state = STATE_UNDEFINED;
    private RecordMetadataEntity metadata;

    static {
        FINAL_STATES.add(STATE_UNDEFINED);
        FINAL_STATES.add(STATE_WR_PUBLISHED);
        FINAL_STATES.add(STATE_CCH_PUBLISHED);
        FINAL_STATES.add(STATE_WR_ERROR_FINAL);
        FINAL_STATES.add(STATE_CCH_PUBLISHING);
        FINAL_STATES.add(STATE_DS_PUBLISHED);
        FINAL_STATES.add(STATE_DS_PUBLISHING);
        FINAL_STATES.add(STATE_HW_PUBLISHING_ERR);

        PUBLISHED_STATES.add(STATE_WR_PUBLISHED);
        PUBLISHED_STATES.add(STATE_CCH_PUBLISHING);
        PUBLISHED_STATES.add(STATE_CCH_PUBLISHED);
        PUBLISHED_STATES.add(STATE_DS_PUBLISHED);
        PUBLISHED_STATES.add(STATE_DS_PUBLISHING);

        UNPUBLISHED_STATES.add(STATE_UNDEFINED);
        UNPUBLISHED_STATES.add(STATE_PROCESSING);
        UNPUBLISHED_STATES.add(STATE_WR_ERROR);

        AWAITING_PUB_EVENTS_STATES.add(STATE_WAIT_WR_PUBLISHED_NOTIFICATION);
        AWAITING_PUB_EVENTS_STATES.add(STATE_WR_PUBLISHING);
        AWAITING_PUB_EVENTS_STATES.add(STATE_WAIT_WR_CANCELLED_NOTIFICATION);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public static Query queryRecord(int dbId, String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAME).setParameter("db", dbId).setParameter(
                PARAM_NAME, cdNumber).setMaxResults(1);
    }

    public static Query queryEditorialRecordById(int recordId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_EDITORIAL_RECORD_BY_ID).setParameter("recordId", recordId);
    }

    public static Query queryEditorialRecordsByIssue(int issueId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_EDITORIAL_RECORDS_BY_ISSUE).setParameter("issueId", issueId);
    }

    public static Query queryRecordCountRenderCompletedByDfId(int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_RECORD_COUNT_RENDER_COMPLETED_BY_DF_ID).setParameter(PARAM_DF_ID, dfId);
    }

    public static Query queryRecordCountSuccessfulByDfId(int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_RECORD_COUNT_RENDER_BY_DF_ID_AND_SUCCESS).setParameter(
            "df", dfId).setParameter("rs", true);
    }

    public static Query queryRecordCountRenderByDfId(int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_RECORD_COUNT_RENDER_BY_DF_ID).setParameter(PARAM_DF_ID, dfId);
    }

    public static Query queryRecordPaths(int dfId, boolean qasSuccess, int offset, int limit, EntityManager manager) {
        Query q = manager.createNamedQuery(QUERY_SELECT_PATH_BY_DF_AND_QA_STATUS).setParameter(
                PARAM_DF_ID, dfId).setParameter("qs", qasSuccess);
        return DbEntity.appendBatchResults(q, offset, limit);          
    }

    public static Query qRecordCountRenderingFailedByDfId(int dfId, EntityManager manager) {
        return manager.createNamedQuery(Q_RECORD_COUNT_RENDERING_FAILED_BY_DF_ID).setParameter(PARAM_DF_ID, dfId);
    }

    public static Query qRecordCountRenderingFailedQaSuccessByDfId(int dfId, EntityManager manager) {
        return manager.createNamedQuery(Q_RECORD_COUNT_RENDERING_FAILED_QA_SUCCESS_BY_DF_ID)
                .setParameter(PARAM_DF_ID, dfId);
    }

    public static Query queryRecordCountQaByDfId(int dfId, boolean successful, EntityManager manager) {
        return manager.createNamedQuery(QUERY_RECORD_COUNT_QA_BY_DF_ID_AND_SUCCESS).setParameter(
                PARAM_DF_ID, dfId).setParameter(PARAM_STATE, successful);
    }

    public static Query queryRecordCountQasCompletedByDfId(int dfId, boolean completed, EntityManager manager) {
        return manager.createNamedQuery(QUERY_RECORD_COUNT_QAS_COMPLETED_BY_DF_ID).setParameter(
                PARAM_DF_ID, dfId).setParameter(PARAM_STATE, completed);
    }

    public static Query queryRecordsQasUncompleted(int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_QAS_UNCOMPLETED_BY_DF_ID).setParameter("df", dfId);
    }

    public static Query queryRecordsUnfinished(List<String> names, int dbId, EntityManager manager) {
        return (names != null && !names.isEmpty()) ? manager.createNamedQuery(
            QUERY_SELECT_UNFINISHED_BY_DB_AND_NAMES).setParameter(PARAM_NAMES, names).setParameter(
                PARAM_DB_ID, dbId).setParameter(PARAM_STATE, FINAL_STATES)
            : manager.createNamedQuery(QUERY_SELECT_UNFINISHED_BY_DB).setParameter(
                PARAM_DB_ID, dbId).setParameter(PARAM_STATE, FINAL_STATES);
    }

    public static Query queryRecords(int dbId, Collection<String> names, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAMES).setParameter(PARAM_DB_ID, dbId).setParameter(
            PARAM_NAMES, names);
    }

    public static Query queryRecordIds(Collection<Integer> ids, int state, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_IDS_BY_STATE_AND_IDS).setParameter("id", ids).setParameter(
                "st", state);
    }

    public static Query queryRecords(int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB).setParameter(PARAM_DB_ID, dbId);
    }

    public static Query queryRecordNamesByIds(List<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_NAMES_BY_IDS).setParameter("id", ids);
    }

    public static Query queryRecords(Collection<Integer> dfIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DF_IDS).setParameter(PARAM_DF_ID, dfIds);
    }

    public static Query queryRecordsByDf(int dfId, Collection<String> names, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DF_AND_NAMES).setParameter(
                PARAM_DF_ID, dfId).setParameter(PARAM_NAMES, names);
    }

    public static Query queryRecordsByDf(Integer dfId, int state, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_STATE_AND_DF).setParameter(
                "df", dfId).setParameter("st", state);
    }

    public static Query queryRecords(int dbId, String name, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAME).setParameter("db", dbId).setParameter(
                PARAM_NAME, name);
    }

    public static Query queryRecordPath(int dfId, int start, int partSize, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_PATH_BY_DF).setParameter(
                PARAM_DF_ID, dfId).setFirstResult(start).setMaxResults(partSize);
    }

    public static Query queryRecordPath(Collection<Integer> recIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_PATH_BY_IDS).setParameter("id", recIds);
    }

    public static Query queryRecordIdsByDf(int dfId, int start, int partSize, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_IDS_BY_DF)
                .setParameter(PARAM_DF_ID, dfId).setFirstResult(start).setMaxResults(partSize);
    }

    public static Query queryWithdrawnRecordNamesByDf(int dbId, int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_WITHDRAWN_RECORD_NAMES_BY_DF).setParameter(
                "db", dbId).setParameter("df", dfId);
    }

    public static Query querySetRecordsStateByDb(int oldState, int newState, int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE_BY_STATE_AND_DB).setParameter(
            "os", oldState).setParameter("ns", newState).setParameter(PARAM_DB_ID, dbId);
    }

    public static Query querySetRecordsStateByDb(Collection<Integer> oldStates, int newState, int dbId,
                                                 Collection<String> names, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE_BY_STATES_AND_DB_AND_NAMES).setParameter(
            "os", oldStates).setParameter("ns", newState).setParameter(
                PARAM_DB_ID, dbId).setParameter(PARAM_NAMES, names);
    }

    private static Query querySetRecordsStateByDb(int newState, int dbId, Collection<String> names, int exceptState,
                                                  EntityManager manager) {
        return exceptState > 0 ? manager.createNamedQuery(QUERY_UPDATE_STATE_BY_DB_AND_NAMES_EXCEPT).setParameter(
                    "ns", newState).setParameter("db", dbId).setParameter("na", names).setParameter("ex", exceptState)
                : manager.createNamedQuery(QUERY_UPDATE_STATE_BY_DB_AND_NAMES).setParameter(
                    "ns", newState).setParameter("db", dbId).setParameter("na", names);
    }

    public static int setRecordsStateByDb(int newState, int dbId, Collection<String> names, int exceptState,
                                          EntityManager manager) {
        if (DbUtils.isOneCommit(names.size())) {
            return querySetRecordsStateByDb(newState, dbId, names, exceptState, manager).executeUpdate();
        }
        int[] ret = {0};
        CollectionCommitter<String> committer = new CollectionCommitter<String>() {
            @Override
            public void commit(Collection<String> list) {
                ret[0] += querySetRecordsStateByDb(newState, dbId, list, exceptState, manager).executeUpdate();
            }
        };
        committer.commitAll(names);
        return ret[0];
    }

    public static Query querySetRecordsStateByDb(int oldState, int newState, int dbId,
                                                 Collection<String> names, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE_BY_STATE_AND_DB_AND_NAMES).setParameter(
            "os", oldState).setParameter("ns", newState).setParameter(
                    PARAM_DB_ID, dbId).setParameter(PARAM_NAMES, names);
    }

    public static int setRecordsStateFailedByDb(Collection<Integer> oldStates, int dbId,
        Collection<String> names, boolean qasCompleted, boolean qas, boolean rendCompleted, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SET_STATE_FAILED_BY_DB_AND_NAMES).setParameter("qc", qasCompleted)
            .setParameter("qs", qas).setParameter("rc", rendCompleted).setParameter("rs", false).setParameter(
                    PARAM_DB_ID, dbId).setParameter("os", oldStates).setParameter(PARAM_NAMES, names).executeUpdate();
    }

    public static Query querySetRecordsStateByDf(int oldState, int newState, int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE_BY_STATE_AND_DF).setParameter(
            "os", oldState).setParameter("ns", newState).setParameter(PARAM_DF_ID, dfId);
    }

    public static int setRecordsStateByDf(int oldState, int newState, int dfId, Collection<String> names,
        EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE_BY_STATE_AND_DF_AND_NAMES).setParameter(
            "os", oldState).setParameter("ns", newState).setParameter(
                    PARAM_DF_ID, dfId).setParameter(PARAM_NAMES, names).executeUpdate();
    }

    public static Query querySetRecordsStateByDf(int newState, int dfId, boolean successful, Set<Integer> exceptState,
                                                 EntityManager manager) {
        return successful ? manager.createNamedQuery(QUERY_UPDATE_STATE_BY_SUCCESS_AND_DF).setParameter(
            "ns", newState).setParameter(PARAM_DF_ID, dfId).setParameter("ex", exceptState)
                : manager.createNamedQuery(QUERY_UPDATE_STATE_BY_DF).setParameter(
                    "ns", newState).setParameter(PARAM_DF_ID, dfId).setParameter("ex", exceptState);
    }

    public static Query querySetRecordsStateByRecord(int newState, int recordId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE_BY_RECORD_ID).setParameter(
            "ns", newState).setParameter("id", recordId);
    }

    public static Query querySetRenderingStateQaSuccessByDfIds(List<Integer> dfIds, boolean state,
                                                               EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_RENDERING_STATE_QA_SUCCESS_BY_DF_IDS).setParameter(
                PARAM_STATE, state).setParameter(PARAM_DF_ID, dfIds);
    }

    public static Query querySetRenderingStateByIds(Collection<Integer> ids, boolean state, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_RENDERING_STATE_BY_IDS).setParameter(
               PARAM_STATE, state).setParameter(DbEntity.PARAM_IDS, ids);
    }

    public static Query queryResetRenderingStateByIds(Collection<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_RESET_RENDERING_STATE_BY_IDS).setParameter(DbEntity.PARAM_IDS, ids);
    }

    public static Query queryTinyRecords(Collection<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_TINY_VO_BY_IDS).setParameter("id", ids);
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Transient
    public String getUnitTitle() {
        return titleEntity == null ? null : getTitleEntity().getTitle();
    }

    @Transient
    public String getUnitTitleNormalized() {
        return CmsUtils.unescapeEntities(getUnitTitle());
    }

    @Transient
    public String getTitle() {
        return getUnitTitle();
    }

    @ManyToOne
    @JoinColumn(name = "title_id")
    public TitleEntity getTitleEntity() {
        return titleEntity;
    }

    public void setTitleEntity(TitleEntity title) {
        titleEntity = title;
    }

    @ManyToOne
    @JoinColumn(name = "db_id")
    public ClDbEntity getDb() {
        return db;
    }

    public void setDb(ClDbEntity db) {
        this.db = db;
    }

    @ManyToOne
    @JoinColumn(name = "delivery_file_id")
    public DeliveryFileEntity getDeliveryFile() {
        return deliveryFile;
    }

    public void setDeliveryFile(DeliveryFileEntity deliveryFile) {
        this.deliveryFile = deliveryFile;
    }

    public boolean getApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public boolean getRejected() {
        return rejected;
    }

    public void setRejected(boolean rejected) {
        this.rejected = rejected;
    }

    public String getStateDescription() {
        return stateDescription;
    }

    public void setStateDescription(String stateDescription) {
        this.stateDescription = stateDescription;
    }

    @Column(length = STRING_MEDIUM_TEXT_LENGTH)
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isQasCompleted() {
        return qasCompleted;
    }

    public void setQasCompleted(boolean qasCompleted) {
        this.qasCompleted = qasCompleted;
    }

    public boolean isQasSuccessful() {
        return qasSuccessful;
    }

    public void setQasSuccessful(boolean qasSuccessful) {
        this.qasSuccessful = qasSuccessful;
    }

    public boolean isRenderingCompleted() {
        return renderingCompleted;
    }

    public void setRenderingCompleted(boolean renderingCompleted) {
        this.renderingCompleted = renderingCompleted;
    }

    public boolean isRenderingSuccessful() {
        return renderingSuccessful;
    }

    public void setRenderingSuccessful(boolean renderingSuccessful) {
        this.renderingSuccessful = renderingSuccessful;
    }

    @Column(nullable = false)
    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getRecordPath() {
        return recordPath;
    }

    public void setRecordPath(String recordPath) {
        this.recordPath = recordPath;
    }

    public boolean isRawDataExists() {
        return isRawDataExists;
    }

    public void setRawDataExists(boolean rawDataExists) {
        isRawDataExists = rawDataExists;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    @ManyToOne
    @JoinColumn(name = "unit_status")
    public UnitStatusEntity getUnitStatus() {
        return unitStatus;
    }

    @Override
    @Transient
    public Integer getUnitStatusId() {
        return unitStatus != null ? unitStatus.getId() : null;
    }

    public void setUnitStatus(UnitStatusEntity unitStatus) {
        this.unitStatus = unitStatus;
    }

    @ManyToOne
    @JoinColumn(name = "product_subtitle")
    public ProductSubtitleEntity getProductSubtitle() {
        return productSubtitle;
    }

    public void setProductSubtitle(ProductSubtitleEntity productSubtitle) {
        this.productSubtitle = productSubtitle;
    }

    @Override
    @Transient
    public Integer getSubTitle() {
        return productSubtitle != null ? productSubtitle.getId() : null;
    }

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "record")
    public DbRecordPublishEntity getRecordPublishEntity() {
        return recordPublishEntity;
    }

    public void setRecordPublishEntity(DbRecordPublishEntity recordPublishEntity) {
        this.recordPublishEntity = recordPublishEntity;
    }

    @Column(nullable = false)
    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    @ManyToOne()
    @JoinColumn(name = "metadata_id")
    public RecordMetadataEntity getMetadata() {
        return metadata;
    }

    public void setMetadata(RecordMetadataEntity metadata) {
        this.metadata = metadata;
    }

    @Transient
    public String getGroupSid() {
        return metadata == null ? null : metadata.getGroupSid();
    }

    @Transient
    public boolean isProcessedError() {
        return isProcessedError(state);
    }

    @Transient
    public boolean isProcessed() {
        return isProcessed(state);
    }

    @Transient
    public boolean isPublishingCancelled() {
        return state == STATE_WAIT_WR_CANCELLED_NOTIFICATION;
    }

    @Transient
    public boolean isAwaitingPublication() {
        return AWAITING_PUB_EVENTS_STATES.contains(state);
    }

    @Transient
    @Override
    public Integer getDeliveryFileId() {
        return getDeliveryFile().getId();
    }

    @Override
    @Transient
    public boolean isUnchanged() {
        return unitStatus != null && UnitStatusEntity.isUnchanged(unitStatus.getId());
    }

    @Override
    public String toString() {
        return getName();
    }

    public static boolean isProcessed(int state) {
        return !FINAL_STATES.contains(state);
    }

    public static boolean isProcessedError(int state) {
        return STATE_WR_ERROR == state || STATE_WR_ERROR_FINAL == state;
    }

    public static boolean isPublished(int state) {
        return PUBLISHED_STATES.contains(state);
    }

    public static boolean isPublishing(int state) {
        return state == STATE_WR_PUBLISHING || state == STATE_DS_PUBLISHING
                || state == STATE_CCH_PUBLISHING || state == STATE_HW_PUBLISHING;
    }
}
