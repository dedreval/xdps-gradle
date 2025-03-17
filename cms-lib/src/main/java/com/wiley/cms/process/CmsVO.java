package com.wiley.cms.process;

import java.io.Serializable;

import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.DbUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 7/16/2018
 */
public class CmsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id = DbEntity.NOT_EXIST_ID;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean exists() {
        return DbUtils.exists(getId());
    }
}

