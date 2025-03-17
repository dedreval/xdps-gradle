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
@Table(name = "COCHRANE_MESHTERM_QUALIFIERS",
        uniqueConstraints = @UniqueConstraint(columnNames = {"qualifier", "majorTopic"}))
@NamedQueries(
        @NamedQuery(
                name = "selectQualifierId",
                query = "SELECT id FROM QualifierEntity where qualifier=:qualifier AND majorTopic=:majorTopic"
        )
    )
public class QualifierEntity implements Serializable {
    private Integer id;
    private String qualifier;
    private Boolean majorTopic;

    public String toString() {
        return "<QualifierName MajorTopicYN=\"" + (majorTopic ? "Y" : "N") + "\">" + qualifier + "</QualifierName>";
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    @Column(nullable = false)
    public Boolean getMajorTopic() {
        return majorTopic;
    }

    public void setMajorTopic(Boolean majorTopic) {
        this.majorTopic = majorTopic;
    }
}
