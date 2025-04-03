package com.wiley.cms.cochrane.cmanager.specrender.data;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Entity
@Table(name = "COCHRANE_SPEC_RENDERING_FILE")
@NamedQueries({
        @NamedQuery(
                name = "deleteSpecRndFilesByDb",
                query = "delete from SpecRenderingFileEntity sf "
                        + " where  sf.specRendering="
                        + "(select s from SpecRenderingEntity s where s.db.id=:dbId)"
        )
    })
public class SpecRenderingFileEntity implements java.io.Serializable {
    private Integer id;
    private SpecRenderingEntity specRendering;

    private Date date;

    private String filePathPublish;
    private String filePathLocal;

    private boolean isCompleted;
    private boolean isSuccessful;

    private int itemAmount;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @ManyToOne
    @JoinColumn(name = "specRendering_id")
    public SpecRenderingEntity getSpecRendering() {
        return specRendering;
    }

    public void setSpecRendering(SpecRenderingEntity specRendering) {
        this.specRendering = specRendering;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getFilePathPublish() {
        return filePathPublish;
    }

    public void setFilePathPublish(String filePathPublish) {
        this.filePathPublish = filePathPublish;
    }

    public String getFilePathLocal() {
        return filePathLocal;
    }

    public void setFilePathLocal(String filePathLocal) {
        this.filePathLocal = filePathLocal;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public void setSuccessful(boolean successful) {
        isSuccessful = successful;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public int getItemAmount() {
        return itemAmount;
    }

    public void setItemAmount(int itemAmount) {
        this.itemAmount = itemAmount;
    }

//    public int compareTo(Object o)
//    {
//        if(o!=null && o instanceof SpecRenderingFileEntity)
//        {
//            return filePath.compareTo(((SpecRenderingFileEntity) o).getFilePath());
//        }
//        else
//        {
//            return -1;
//        }
//    }
}
