package com.wiley.cms.cochrane.cmanager.data.record;

import java.util.Arrays;
import java.util.List;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Entity
@Cacheable
@Table(name = "COCHRANE_UNIT_STATUS")
@NamedQueries({
        @NamedQuery(
                name = UnitStatusEntity.QUERY_SELECT_ALL,
                query = "SELECT s from UnitStatusEntity s ORDER BY s.priority"
        )
    })
public class UnitStatusEntity implements java.io.Serializable {

    public static final List<Integer> CCA_AVAILABLE_STATUSES = Arrays.asList(
                UnitStatus.NEW, UnitStatus.UNDER_REVIEW, UnitStatus.UPDATED, UnitStatus.ARCHIVED);

    static final String QUERY_SELECT_ALL = "unitStatusesAll";

    private Integer id;

    private String name;

    private int priority;

    private int cdsr;

    private boolean uiShow;

    private String uiName;

    private String images;

    public static Query queryAll(EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_ALL);
    }

    @Transient
    public UnitStatusVO getVO() {
        return new UnitStatusVO(this);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getUiName() {
        return uiName;
    }

    public void setUiName(String uiName) {
        this.uiName = uiName;
    }

    public boolean isUiShow() {
        return uiShow;
    }

    public void setUiShow(boolean uiShow) {
        this.uiShow = uiShow;
    }

    public int getCdsr() {
        return cdsr;
    }

    @Transient
    public boolean is4Cdsr() {
        return cdsr > 0;
    }

    public void setCdsr(int cdsr) {
        this.cdsr = cdsr;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    @Transient
    public boolean isMeshtermUpdated() {
        return isMeshtermUpdated(getId());
    }

    @Transient
    public boolean isTranslationUpdated() {
        return isTranslationUpdated(getId());
    }

    @Transient
    public boolean isWithdrawn() {
        return isWithdrawn(getId());
    }

    @Override
    public String toString() {
        return String.format("%s [%d]", getName(), getId());
    }

    public static boolean isWithdrawn(Integer statusId) {
        return UnitStatusVO.WITHDRAWN_STATUSES.contains(statusId);
    }

    public static boolean isTranslationUpdated(int status) {
        return UnitStatus.TRANSLATED_ABSTRACTS == status;
    }

    public static boolean isMeshtermUpdated(int status) {
        return UnitStatus.MESHTERMS_UPDATED == status;
    }

    public static boolean isUnchanged(int status) {
        return UnitStatus.UNCHANGED == status || UnitStatus.UNCHANGED_COMMENTED == status || isMeshtermUpdated(status);
    }

    public static boolean isSystem(Integer statusId) {
        return UnitStatusVO.SYSTEM_STATUSES.contains(statusId);
    }

    /**
     * UnitStatus enum with fixed ids
     */
    public interface UnitStatus {
        int NEW = 1;
        int COMMENTED = 2;
        int UPDATED = 3;
        int COMMENTED_AND_UPDATED = 4;
        int WITHDRAWN = 5;
        int DELETED = 6;
        int NEW1 = 7;
        int EDITED = 8;
        int MAJOR_CHANGE = 9;
        int EDITED_CON = 10;
        int UPDATED_NOT_CON = 11;
        int UPDATED_CON = 12;
        int UNCHANGED = 13;
        int STABLE = 14;
        int WITHDRAWN1 = 15;
        int DELETED1 = 16;

        int NEW_COMMENTED = 17;
        int EDITED_COMMENTED = 18;
        int MAJOR_CHANGE_COMMENTED = 19;
        int EDITED_CON_COMMENTED = 20;
        int UPDATED_NOT_CON_COMMENTED = 21;
        int UPDATED_CON_COMMENTED = 22;
        int UNCHANGED_COMMENTED = 23;
        int STABLE_COMMENTED = 24;
        int WITHDRAWN_COMMENTED = 25;
        int MESHTERMS_UPDATED = 26;

        int TRANSLATED_ABSTRACTS = 27;

        int UNDER_REVIEW = 28;
        int REVIEWED = 29;
        int ARCHIVED = 30;
        int EDITED1 = 31;

    }

}
