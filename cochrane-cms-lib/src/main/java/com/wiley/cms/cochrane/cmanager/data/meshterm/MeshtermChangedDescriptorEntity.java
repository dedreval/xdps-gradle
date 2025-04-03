package com.wiley.cms.cochrane.cmanager.data.meshterm;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 22.02.2013
 */
@Entity
@Table(name = "COCHRANE_MESHTERM_CHANGED_DESCRIPTORS")
@NamedQueries({
        @NamedQuery(
                name = "meshtermChangedDescriptorCount",
                query = "SELECT COUNT(d.id) FROM MeshtermChangedDescriptorEntity d"
        ),
        @NamedQuery(
                name = "deleteMeshtermChangedDescriptor",
                query = "DELETE FROM MeshtermChangedDescriptorEntity"
        )
    })
public class MeshtermChangedDescriptorEntity {

    private Integer id;
    private String descriptor;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(unique = true, nullable = false)
    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

}
