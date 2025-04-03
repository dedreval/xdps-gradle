package com.wiley.cms.cochrane.cmanager.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Table;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.XmlUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/20/2021
 */

@Entity
@Table(name = "COCHRANE_TITLE")
public class TitleEntity extends DbEntity {

    public static final Integer EMPTY_TITLE_ID = 1;

    private static final long serialVersionUID = 1L;

    /** this configuration property cannot be changed on real-time */
    private static final int TITLE_LIMIT = CochraneCMSPropertyNames.getUnitTitleSizeLimit();
    private static final int DB_TITLE_SIZE = 512;

    private String title;
   
    public TitleEntity() {
    }

    public TitleEntity(String title) {
        setTitle(title);
    }

    public static TitleEntity checkEntity(String input, RecordEntity record, EntityManager manager) {
        if (input != null) {
            return record != null && record.getMetadata() != null
                    ? checkEntity(input, record.getMetadata().getTitleEntity(), manager) : checkEntity(input, manager);
        }
        return null;
    }

    public static TitleEntity checkEntity(String input, TitleEntity existing, EntityManager manager) {
        TitleEntity ret = null;
        if (input != null) {
            ret = checkEmptyEntity(input, manager);
            if (ret == null) {
                String title = truncateTitleLength(XmlUtils.normalize(input));
                ret = existing != null && existing.getTitle().equals(title) ? existing : createEntity(title, manager);
            }
        }
        return ret;
    }

    public static TitleEntity checkEntity(String input, EntityManager manager) {
        TitleEntity empty = checkEmptyEntity(input, manager);
        if (empty == null) {
            return createEntity(truncateTitleLength(XmlUtils.normalize(input)), manager);
        }
        return empty;
    }

    private static TitleEntity checkEmptyEntity(String input, EntityManager manager) {
        return input.trim().isEmpty() ? manager.find(TitleEntity.class, EMPTY_TITLE_ID) : null;
    }

    private static String truncateTitleLength(String title) {
        int limit = TITLE_LIMIT <= 0 || TITLE_LIMIT > DB_TITLE_SIZE ? DB_TITLE_SIZE : TITLE_LIMIT;
        if (title != null && title.length() > limit) {
            String truncateTag = CochraneCMSPropertyNames.getUnitTitleTruncatedTag();
            return title.substring(0, limit - 1 - truncateTag.length()) + truncateTag;
        }
        return title;
    }

    public static TitleEntity createEntity(String title, EntityManager manager) {
        TitleEntity ret = new TitleEntity(title);
        manager.persist(ret);
        return ret;
    }

    @Column(name = "title", length = DB_TITLE_SIZE, updatable = false)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
