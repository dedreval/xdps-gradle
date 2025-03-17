package com.wiley.cms.cochrane.cmanager.data.cca;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishTypeEntity;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 28.12.11
 */
@Entity
@Table(name = "COCHRANE_PUBLISH_WHEN_READY")
@NamedQueries({
        @NamedQuery(
                name = "deletePublishWhenReadyByDb",
                query = "delete from WhenReadyPublishEntity where df.id in "
                        + "(select dfe.id from DeliveryFileEntity dfe where dfe.db=:db)"
        ),
        @NamedQuery(
                name = "getLastPublishedDateByRecordNameAndPublishType",
                query = "SELECT wrp.df.date FROM WhenReadyPublishEntity wrp WHERE"
                        + " wrp.successful = true AND wrp.publishType.name = :publishType"
                        + " AND wrp.df.id IN (SELECT r.deliveryFile.id FROM RecordEntity r WHERE r.name = :recordName"
                        + " AND r.renderingSuccessful = true)"
                        + " ORDER BY wrp.df.date DESC"
        )
    })
public class WhenReadyPublishEntity implements Serializable {
    private static final int STRING_MEDIUM_TEXT_LENGTH = 65536;

    private Integer id;
    private DeliveryFileEntity df;
    private PublishTypeEntity publishType;
    private Boolean successful;
    private String message;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @ManyToOne
    @JoinColumn(name = "deliveryFile_id")
    public DeliveryFileEntity getDf() {
        return df;
    }

    public void setDf(DeliveryFileEntity df) {
        this.df = df;
    }

    @ManyToOne
    @JoinColumn(name = "publishType_id")
    public PublishTypeEntity getPublishType() {
        return publishType;
    }

    public void setPublishType(PublishTypeEntity publishType) {
        this.publishType = publishType;
    }

    @Column(name = "successful")
    public Boolean getSuccessful() {
        return successful;
    }

    public void setSuccessful(Boolean successful) {
        this.successful = successful;
    }

    @Column(name = "message", length = STRING_MEDIUM_TEXT_LENGTH)
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
