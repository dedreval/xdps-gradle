package com.wiley.cms.cochrane.cmanager.entity;

import javax.persistence.Column;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToOne;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.process.entity.LabelEntity;

/**
 * Base cochrane record entity.
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 */

@MappedSuperclass
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class AbstractRecord extends LabelEntity {
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_DELETED             = 100;
    public static final int STATUS_RETRACTED           = 101;
    public static final int STATUS_DELETED_BY_RESTORE  = 102;
    public static final int STATUS_DELETED_BY_IMPORT   = 103;

    private int number;
    private ClDbEntity db;
    //private int type;
    //private Date date;
    //private int status = STATUS_NORMAL;

    public AbstractRecord() {
    }

    //@Column(name = "db_type", nullable = false)
    //public int getType() {
    //    return type;
    //}

    //public void setType(int dbType) {
    //    this.type = dbType;
    //}

    @Column(name = "number", nullable = false, updatable = false)
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    //@Column(name = "issue", nullable = false)
    //public int getIssue() {
    //    return issue;
    //}

    //public void setIssue(int issue) {
    //    this.issue = issue;
    //}

    //@Column(name = "status", nullable = false)
    //public int getStatus() {
    //    return status;
    //}

    //public void setStatus(int status) {
    //    this.status = status;
    //}

    @OneToOne
    @JoinColumn(name = "db_id", nullable = false, updatable = false)
    public ClDbEntity getDb() {
        return db;
    }

    public void setDb(ClDbEntity db) {
        this.db = db;
    }
}
