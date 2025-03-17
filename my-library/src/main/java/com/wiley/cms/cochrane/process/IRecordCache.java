package com.wiley.cms.cochrane.process;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.IKibanaRecord;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusVO;
import com.wiley.cms.process.ExternalProcess;
import com.wiley.tes.util.Pair;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 20.12.12
 */
public interface IRecordCache {
    Object RECORD_LOCKER = new Object();

    void addSingleProcess(String dbName, int eventType);

    void addSingleProcess(ExternalProcess ep, String dbName, int eventType);

    ExternalProcess getSingleProcess(String dbName, int eventType);

    ExternalProcess removeSingleProcess(String dbName, int eventType);


    void addProcess(ExternalProcess process);

    ExternalProcess getProcess(int id);

    ExternalProcess removeProcess(int id);

    Map<Integer, ClDbVO> getLastDatabases();

    void refreshLastDatabases();

    void addLastCDSRDb(int clDbId, IssueVO issue);


    String getTopicsSummary();

    void setTopic(String groupName, String topicsSource);


    GroupVO getCRGGroup(String sid);

    Iterator<String> getCRGGroupCodes();

    Iterator<UnitStatusVO> getUnitStatuses(boolean isCdsr);

    UnitStatusVO getUnitStatus(String name, boolean isCdsr);

    /**
     * Add a new record key to the cache or activate an existing inactive record
     * @param record a record key to check
     * @param spd    if TRUE - the record is to be checked and placed into SPD cell
     * @return TRUE either a new record key was added or an existing record was found and activate
     */
    boolean addRecord(String record, boolean spd);

    /**
     * @param record a record key to check
     * @return TRUE if this record key is existing in the cache and the related record is active or not present
     */
    boolean containsRecord(String record);

    /**
     * @param record a record key to check
     * @return TRUE if this record key is existing in the cache
     */
    boolean isRecordExists(String record);

    /**
     * Set SPD record existing in the cache as active/inactive
     * @param record       a record key to check
     * @param publisherId  [cdNumber].[pubNumber]
     * @param active       if TRUE - to set active, FALSE - to set inactive
     * @return the publisherId of another record existing in the cache that prevents this activation
     */
    String activateRecord(String record, String publisherId, boolean active);

    IKibanaRecord removeRecord(String record);

    IKibanaRecord removeRecord(String record, boolean spd);

    void removeRecords(Collection<String> records, boolean spd);


    void putKibanaRecord(String recordName, IKibanaRecord kibanaRec, boolean spd, boolean setActive);

    IKibanaRecord getKibanaRecord(String recordName);   //todo

    IKibanaRecord getKibanaRecord(String recordName, boolean spd);

    void addKibanaTransaction(String transactionId, String kibanaRecord, String cdNumber);

    Pair<List<String>, Set<String>> removeKibanaTransaction(String transactionId);

    IKibanaRecord getPreRecord(Integer dfId, String cdNumber);

    IKibanaRecord checkPreRecord(Integer dfId, String cdNumber, IKibanaRecord kr);

    IKibanaRecord checkPostRecord(String cdNumber, IKibanaRecord kr);


    RecordCache.ManuscriptValue checkAriesRecordOnReceived(String manuscriptNumber, String cdNumber, int whenReadyId);

    RecordCache.ManuscriptValue checkAriesRecordOnPublished(String manuscriptNumber, String cdNumber, int whenReadyId,
                                                            Integer acknowledgementId);

    RecordCache.ManuscriptValue getAriesRecord(String manuscriptNumber);

    void addOnConversionRecordIds(Collection<Integer> recIds);

    boolean containsOnConversionRecordId(Integer recId);

    void removeOnConversionRecordIds(Collection<Integer> recIds);

    void update();

    String printState();
}
