package com.wiley.tes.util.res;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 28.03.13
 */
public abstract class ResourceIntId extends Resource<Integer> {

    @Override
    protected Integer getIdFromString(String id) {
        return Integer.parseInt(id);
    }

    @XmlAttribute(name = TAG_ID)
    @Override
    public Integer getId() {
        return super.getId();
    }

    @Override
    public void setId(Integer id) {
        super.setId(id);
    }
}

