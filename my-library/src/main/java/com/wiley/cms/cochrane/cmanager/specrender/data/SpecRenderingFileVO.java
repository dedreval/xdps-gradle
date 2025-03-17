package com.wiley.cms.cochrane.cmanager.specrender.data;

import java.io.Serializable;
import java.util.Date;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class SpecRenderingFileVO implements Serializable {


    private static final String PUBLISH_DIR = "publish/";
    private Integer id;

    private Integer specRenderingId;

    private Date date;

    private String filePathPublish;
    private String filePathLocal;

    private boolean isCompleted;
    private boolean isSuccessful;

    private int itemAmount;

    public SpecRenderingFileVO() {
    }

    public SpecRenderingFileVO(SpecRenderingFileEntity entity) {
        setId(entity.getId());
        setSpecRenderingId(entity.getSpecRendering().getId());
        setDate(entity.getDate());
        setFilePathPublish(entity.getFilePathPublish());
        setFilePathLocal(entity.getFilePathLocal());
        setCompleted(entity.isCompleted());
        setSuccessful(entity.isSuccessful());
        setItemAmount(entity.getItemAmount());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSpecRenderingId() {
        return specRenderingId;
    }

    public void setSpecRenderingId(Integer specRenderingId) {
        this.specRenderingId = specRenderingId;
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

    public String getFileName() {
        if (filePathPublish != null) {
            return filePathPublish.substring(filePathPublish.lastIndexOf(PUBLISH_DIR) + PUBLISH_DIR.length(),
                    filePathPublish.length());
        }
        return null;
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

}
