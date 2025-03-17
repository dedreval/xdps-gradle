package com.wiley.cms.process.entity;

import java.io.Serializable;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.MappedSuperclass;
import javax.persistence.Query;
import javax.persistence.Transient;

import com.wiley.tes.util.DbUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.06.13
 */
@MappedSuperclass
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class DbEntity implements Serializable {
        
    public static final int STRING_MEDIUM_TEXT_LENGTH = 65535;
    public static final int STRING_VARCHAR_LENGTH_SMALL = 8;
    public static final int STRING_VARCHAR_LENGTH_16 = 16;
    public static final int STRING_VARCHAR_LENGTH_32 = 32;
    public static final int STRING_VARCHAR_LENGTH_64 = 64;
    public static final int STRING_VARCHAR_LENGTH_LARGE = 128;
    public static final int STRING_VARCHAR_LENGTH_FULL = 255;

    public static final int NOT_EXIST_ID = 0;

    public static final String PARAM_ID = "id";
    public static final String PARAM_IDS = "ids";

    private static final long serialVersionUID = 1L;

    private int id;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public static Query appendBatchResults(Query q, int skip, int batchSize) {
        if (skip > 0) {
            q.setFirstResult(skip);
        }
        if (batchSize > 0) {
            q.setMaxResults(batchSize);
        }
        return q;
    }

    protected static String checkSting(String str, int maxLength) {
        return str != null && str.length() > maxLength ? str.substring(0, maxLength) : str;
    }

    @Transient
    public boolean exists() {
        return DbUtils.exists(getId());
    }
}
