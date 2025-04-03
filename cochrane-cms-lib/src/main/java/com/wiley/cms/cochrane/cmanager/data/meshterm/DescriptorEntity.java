package com.wiley.cms.cochrane.cmanager.data.meshterm;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 30.10.2009
 */

@Entity
@Table(name = "COCHRANE_MESHTERM_DESCRIPTORS",
        uniqueConstraints = @UniqueConstraint(columnNames = {"descriptor", "majorTopic"}))
@NamedQueries(
        @NamedQuery(
                name = "selectDescriptorId",
                query = "SELECT id FROM DescriptorEntity WHERE descriptor=:descriptor AND majorTopic=:majorTopic"
        )
    )
public class DescriptorEntity implements Serializable {
    private Integer id;
    private String descriptor;
    private Boolean majorTopic;

    public String toString() {
        return "<DescriptorName MajorTopicYN=\"" + (majorTopic ? "Y" : "N") + "\">" + descriptor + "</DescriptorName>";
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

    @Column(nullable = false)
    public Boolean getMajorTopic() {
        return majorTopic;
    }

    public void setMajorTopic(Boolean majorTopic) {
        this.majorTopic = majorTopic;
    }
}
