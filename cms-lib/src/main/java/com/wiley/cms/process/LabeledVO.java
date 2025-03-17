package com.wiley.cms.process;

import java.io.Serializable;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 7/16/2018
 */
public class LabeledVO extends CmsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String label;

    public LabeledVO(String label) {
        this.label = label;
    }

    public LabeledVO(int id, String label) {
        setLabel(label);
        setId(id);
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return String.format("%s [%d]", getLabel(), getId());
    }
}
