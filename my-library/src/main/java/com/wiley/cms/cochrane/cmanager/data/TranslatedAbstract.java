package com.wiley.cms.cochrane.cmanager.data;

import javax.persistence.Column;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.MappedSuperclass;

import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;

/**
 * Base cochrane translated abstract entity.
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 */

@MappedSuperclass
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class TranslatedAbstract extends RecordAbstract {

    private String language;
    private String sid;
    private String version;

    public TranslatedAbstract() {
    }

    @Column(length = 2)
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Column(length = STRING_VARCHAR_LENGTH_32)
    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid =  sid;
    }

    @Column(length = RecordMetadataEntity.VERSION_LENGTH)
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
