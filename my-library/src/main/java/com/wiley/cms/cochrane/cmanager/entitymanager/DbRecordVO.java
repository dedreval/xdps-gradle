package com.wiley.cms.cochrane.cmanager.entitymanager;

import java.util.Date;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entity.AbstractRecord;
import com.wiley.cms.cochrane.cmanager.entity.DbRecordEntity;
import com.wiley.cms.cochrane.cmanager.entity.LastRecordEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.LabeledVO;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 7/16/2018
 */
public class DbRecordVO extends LabeledVO {
    private static final long serialVersionUID = 1L;

    private final int number;
    private final String language;
    private final int version;
    private final int status;

    private int issue;
    private int dbId;
    private int type;

    private Integer dfId;
    private Date lastUpdate;

    public DbRecordVO(String cdNumber, int pub, String lang, int status, int v, ClDbEntity db, Integer dfId) {
        this(DbEntity.NOT_EXIST_ID, RevmanMetadataHelper.buildPubName(cdNumber, pub),
            RecordHelper.buildRecordNumber(cdNumber, 2), lang, status, v);

        initDb(db.getId(), db.getIssue().getFullNumber());
        this.dfId = dfId;
    }

    public DbRecordVO(BaseType baseType, String cdNumber, int status, int dbId, int fullIssueNumber, Integer dfId) {
        this(DbEntity.NOT_EXIST_ID, cdNumber, baseType.getProductType().buildRecordNumber(cdNumber), null, status,
                RecordEntity.VERSION_LAST);
        initDb(dbId, fullIssueNumber);
        this.dfId = dfId;
    }

    public DbRecordVO(LastRecordEntity e) {
        this(e.getRecord());
    }

    public DbRecordVO(DbRecordEntity e) {
        this(e.getId(), e.getLabel(), e.getNumber(), e.getLanguage(), e.getStatus(), e.getVersion());

        ClDbEntity db = e.getDb();
        initDb(db.getId(), db.getIssue().getFullNumber());
        dfId = e.getDeliveryId();
        type = e.getType();
        lastUpdate = e.getDate();
    }

    private DbRecordVO(int id, String label, int number, String lang, int status, int v) {
        super(id, label);

        this.number = number;
        this.language =  lang;
        this.version = v;
        this.status = status;
    }

    private void initDb(int dbId, int issue) {
        this.dbId = dbId;
        this.issue = issue;
    }

    public int getNumber() {
        return number;
    }

    public int getIssue() {
        return issue;
    }

    public int getVersion() {
        return version;
    }

    public int getStatus() {
        return status;
    }

    public boolean isHistorical() {
        return isHistorical(version);
    }

    public String getLanguage() {
        return language;
    }

    public boolean isJats() {
        return type > 0;
    }

    public int getDbId() {
        return dbId;
    }

    public Integer getDfId() {
        return dfId;
    }

    public boolean isDeleted() {
        return isDeleted(status);
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public static boolean isDeleted(int s) {
        return s >= AbstractRecord.STATUS_DELETED;
    }

    public static boolean isHistorical(int v) {
        return v > RecordEntity.VERSION_LAST;
    }

    @Override
    public String toString() {
        return String.format("%s.%s jats=%b v%d %d [%d]",
                getLabel(), getLanguage(), isJats(), getVersion(), getStatus(), getId());
    }
}
