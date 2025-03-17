package com.wiley.cms.cochrane.cmanager.data.record;

import java.util.ArrayList;
import java.util.List;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class UnitStatusVO implements java.io.Serializable {
    public static final String COMMENT = "comment";
    public static final List<Integer> SYSTEM_STATUSES = new ArrayList<>();

    static final List<Integer> WITHDRAWN_STATUSES = new ArrayList<Integer>() {
        {
            add(UnitStatusEntity.UnitStatus.WITHDRAWN);
            add(UnitStatusEntity.UnitStatus.WITHDRAWN1);
            add(UnitStatusEntity.UnitStatus.WITHDRAWN_COMMENTED);
        }
    };

    private Integer id;
    private String name;
    private int priority;
    private int cdsr;
    private boolean uiShow;
    private String uiName;
    private String images;

    private List unitStatusAggregate;

    static {
        SYSTEM_STATUSES.add(UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS);
        SYSTEM_STATUSES.add(UnitStatusEntity.UnitStatus.MESHTERMS_UPDATED);
    }

    public UnitStatusVO(UnitStatusEntity entity) {
        id = entity.getId();
        name = entity.getName();
        priority = entity.getPriority();
        cdsr = entity.getCdsr();
        setUiShow(entity.isUiShow());
        uiName = entity.getUiName();
        images = entity.getImages();
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUiName() {
        return uiName;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isCdsr() {
        return cdsr > 0;
    }

    public boolean isCommented() {
        return name.contains(COMMENT);
    }

    public int getCdsr() {
        return cdsr;
    }

    public int getCdsr4Revman() {
        return cdsr == RecordMetadataEntity.RevmanStatus.NONE.dbKey
                ? RecordMetadataEntity.RevmanStatus.UNCHANGED.dbKey : cdsr;
    }

    public boolean isUiShow() {
        return uiShow;
    }

    public void setUiShow(boolean value) {
        uiShow = value;
    }

    public List getUnitStatusAggregate() {
        return unitStatusAggregate;
    }

    public void setUnitStatusAggregate(List unitStatusAggregate) {
        this.unitStatusAggregate = unitStatusAggregate;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    public boolean isWithdrawn() {
        return WITHDRAWN_STATUSES.contains(id);
    }

    public boolean isSystem() {
        return SYSTEM_STATUSES.contains(id);
    }
}
