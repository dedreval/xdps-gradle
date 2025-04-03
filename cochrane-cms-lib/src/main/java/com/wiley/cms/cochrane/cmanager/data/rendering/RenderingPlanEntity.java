package com.wiley.cms.cochrane.cmanager.data.rendering;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 23-Jan-2007
 */
@Entity
@Cacheable
@Table(name = "COCHRANE_RENDERING_PLAN")
@NamedQueries({
        @NamedQuery(
                name = "rndPlan",
                query = "select plan from RenderingPlanEntity plan where plan.description=:d"
        ),
        @NamedQuery(
                name = "rndPlans",
                query = "select plan from RenderingPlanEntity plan "
        ),
        @NamedQuery(
                name = "planNames",
                query = "select plan.description from RenderingPlanEntity plan "
        )
    })

public class RenderingPlanEntity implements java.io.Serializable {

    private Integer id;
    private String shortName;
    private String description;
    private byte priority;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public void setPriority(byte priority) {
        this.priority = priority;
    }

    public byte getPriority() {
        return priority;
    }

    public static int[] getPlanIds(boolean pdfCreate, boolean htmlCreate, boolean pdfFopCreate, int count) {

        int[] ret = new int[count];
        int aNumber = 0;
        if (pdfCreate) {
            ret[aNumber++] = RenderingPlan.PDF_TEX.id();
        }

        if (htmlCreate) {
            ret[aNumber++] = RenderingPlan.HTML.id();
        }

        if (pdfFopCreate) {
            ret[aNumber] = RenderingPlan.PDF_FOP.id();
        }
        return ret;
    }
}
