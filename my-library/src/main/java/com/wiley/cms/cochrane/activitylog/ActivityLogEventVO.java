package com.wiley.cms.cochrane.activitylog;

import java.io.Serializable;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 03.06.2015
 */
public class ActivityLogEventVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;

    public ActivityLogEventVO(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
