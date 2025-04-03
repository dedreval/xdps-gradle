package com.wiley.cms.cochrane.cmanager.data;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 28.12.2006
 */
@Entity
@Cacheable
@Table(name = "COCHRANE_STATUS")
public class StatusEntity implements java.io.Serializable {
    private Integer id;
    private String status;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public final boolean equalsById(int id) {
        return  this.id == id;
    }

    public String toString() {
        return "" + id;
    }
}
