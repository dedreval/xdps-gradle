package com.wiley.cms.cochrane.cmanager.data;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Entity
@Table(name = "COCHRANE_PUBLISH_TYPE")
@NamedQueries(
        @NamedQuery(
                name = "findPublishTypeByName",
                query = "SELECT pbt from PublishTypeEntity pbt WHERE pbt.name=:name"
        )
    )
public class PublishTypeEntity implements java.io.Serializable {
    public static final Map<String, Integer> ENTITIES = new HashMap<String, Integer>();

    static final String QUERY_SELECT_BY_NAME = "findPublishTypeByName";

    private Integer id;

    private String name;

    public static Query queryPublishType(String name, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_NAME).setParameter("name", name);
    }

    public static Integer getNamedEntityId(String name) {
        Integer id = ENTITIES.get(name);
        if (id == null) {
            id = ResultStorageFactory.getFactory().getInstance().getPublishType(name).getId();
            ENTITIES.put(name, id);
        }
        return id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
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
    public boolean isWhenReadyType() {
        return PublishProfile.isWhenReadyType(name);
    }
}