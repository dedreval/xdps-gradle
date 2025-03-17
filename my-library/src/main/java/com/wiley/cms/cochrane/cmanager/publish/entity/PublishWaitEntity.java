package com.wiley.cms.cochrane.cmanager.publish.entity;

import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;

import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 12/24/2018
 */
@Entity
@Table(name = "COCHRANE_PUBLISH_WAIT")
@NamedQueries({
        @NamedQuery(
            name = PublishWaitEntity.QUERY_SELECT_PUBLISH_IDS,
            query = "SELECT pwe.publish.id FROM PublishWaitEntity pwe"
        ),
        @NamedQuery(
            name = PublishWaitEntity.QUERY_SELECT_PUBLISH_IDS_BY_FILE_NAME,
            query = "SELECT pwe.publish.id FROM PublishWaitEntity pwe WHERE pwe.publish.fileName =:fn"
        ),
        @NamedQuery(
                name = PublishWaitEntity.QUERY_SELECT_PUBLISH_BY_FILE_NAME,
                query = "SELECT pwe FROM PublishWaitEntity pwe WHERE pwe.publish.fileName =:fn"
        ),
        @NamedQuery(
            name = PublishWaitEntity.QUERY_DELETE_BY_PUBLISH_IDS,
            query = "DELETE FROM PublishWaitEntity pwe WHERE pwe.publish.id IN (:pi)"
        )
    })
public class PublishWaitEntity extends DbEntity {
    static final String QUERY_SELECT_PUBLISH_IDS = "selectPublish4WaitIds";
    static final String QUERY_SELECT_PUBLISH_IDS_BY_FILE_NAME = "selectPublish4WaitIdsByFileName";
    static final String QUERY_SELECT_PUBLISH_BY_FILE_NAME = "selectPublish4WaitByFileName";
    static final String QUERY_DELETE_BY_PUBLISH_IDS = "deletePublishWaitByPublish";

    private String fileName;
    private boolean staticContentDisabled;
    private PublishEntity publish;

    public static Query queryWaitForPublishIds(EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_PUBLISH_IDS);
    }

    public static Query queryWaitForPublishIds(String fileName, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_PUBLISH_IDS_BY_FILE_NAME).setParameter("fn", fileName);
    }

    public static Query queryWaitForPublish(String fileName, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_PUBLISH_BY_FILE_NAME).setParameter("fn", fileName);
    }

    public static Query deleteWaitForPublish(Collection<Integer> publishIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_PUBLISH_IDS).setParameter("pi", publishIds);
    }

    @Column(name = "delivery_sid", length = DbEntity.STRING_VARCHAR_LENGTH_64, nullable = false, updatable = false)
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @ManyToOne
    @JoinColumn(name = "publish_id", nullable = false, updatable = false)
    public PublishEntity getPublish() {
        return publish;
    }

    public void setPublish(PublishEntity publish) {
        this.publish = publish;
    }

    @Column(name = "disabled_static_content", nullable = false, updatable = false)
    public boolean isStaticContentDisabled() {
        return staticContentDisabled;
    }

    public void setStaticContentDisabled(boolean value) {
        this.staticContentDisabled = value;
    }
}
