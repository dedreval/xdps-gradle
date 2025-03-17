package com.wiley.cms.process.entity;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11/16/2016
 *
 */
@MappedSuperclass
public class LabelEntity extends DbEntity {

    private String label;

    @Column(nullable = false, updatable = false)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
