package com.wiley.cms.cochrane.cmanager.data.record;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Entity
@Table(name = "COCHRANE_PRODUCT_SUBTITLE")
@Cacheable
@NamedQueries({
        @NamedQuery(
                name = "subTitles",
                query = "SELECT s from ProductSubtitleEntity s"
        )
    })
public class ProductSubtitleEntity implements java.io.Serializable {

    private Integer id;
    private String name;

    @Transient
    public ProductSubtitleVO getVO() {
        return new ProductSubtitleVO(this);
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

    /**
     * Subtitle enum with fixed ids
     */
    public interface ProductSubtitle {
        //CDSR subtitles
        int REVIEWS = 1;
        int PROTOCOLS = 2;
        int UPDATE_PROTOCOLS = 11;

        //EED subtitles
        int OTHER_ECONOMIC_STUDIES = 3;
        int ECONOMIC_EVALUATIONS = 4;
        int EDITORIALS = 12;

        //About subtitles
        int COLLABORATION = 5;
        int CRGS = 6;
        int FIELDS = 7;
        int METHODS_GROUPS = 8;
        int CENTRES = 9;
        int POSSIBLE_ENTITIES = 10;

        int NONE = 13;
    }

}
