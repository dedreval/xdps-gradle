package com.wiley.cms.cochrane.cmanager.data;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.MappedSuperclass;

import com.wiley.cms.process.entity.DbEntity;

/**
 * Base cochrane record entity.
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 */

@MappedSuperclass
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class RecordAbstract extends DbEntity {

    private int pubNumber = 0;
    private Integer dfId = null;
    private Date date;
    private int number;

    public RecordAbstract() {
    }

    public Date getDate() {
        return date;
    }

    public void setDate(final Date date) {
        this.date = date;
    }

    @Column(name = "pub", nullable = false)
    public int getPubNumber() {
        return pubNumber;
    }

    public void setPubNumber(int pubNumber) {
        this.pubNumber = pubNumber;
    }

    @Column(name = "df_id")
    public Integer getDeliveryId() {
        return dfId;
    }

    public void setDeliveryId(Integer dfId) {
        this.dfId = dfId;
    }

    @Column(name = "number", nullable = false, updatable = false)
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }
}
