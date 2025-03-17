package com.wiley.tes.util.res;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 28.03.13
 */
public abstract class ResourceStrId extends Resource<String> {

    protected static final String PHRASE_DELIMITER = ";|,";
    protected static final String DELIMITER = " |\n|;|,";

    private static final long serialVersionUID = 1L;

    @Override
    protected String getIdFromString(String id) {
        return id;
    }

    @XmlAttribute(name = TAG_ID)
    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public void setId(String id) {
        super.setId(id);
    }
}

