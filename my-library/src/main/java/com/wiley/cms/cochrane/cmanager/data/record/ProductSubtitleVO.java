package com.wiley.cms.cochrane.cmanager.data.record;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ProductSubtitleVO implements java.io.Serializable {
    private Integer id;
    private String name;

    public ProductSubtitleVO(ProductSubtitleEntity entity) {
        id = entity.getId();
        name = entity.getName();
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
