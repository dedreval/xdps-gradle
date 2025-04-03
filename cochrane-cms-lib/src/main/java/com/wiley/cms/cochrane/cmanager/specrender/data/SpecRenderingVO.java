package com.wiley.cms.cochrane.cmanager.specrender.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class SpecRenderingVO implements Serializable {
    private Integer id;
    private int dbId;

    private Date date;

    private boolean isCompleted;
    private boolean isSuccessful;

    private List<SpecRenderingFileVO> files = new ArrayList<SpecRenderingFileVO>();

    public SpecRenderingVO() {
    }

    public SpecRenderingVO(SpecRenderingEntity entity) {
        setId(entity.getId());
        setDbId(entity.getDb().getId());
        setDate(entity.getDate());
        setCompleted(entity.isCompleted());
        setSuccessful(entity.isSuccessful());

        for (SpecRenderingFileEntity file : entity.getFiles()) {
            files.add(new SpecRenderingFileVO(file));
        }
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getDbId() {
        return dbId;
    }

    public void setDbId(int dbId) {
        this.dbId = dbId;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
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

    public List<SpecRenderingFileVO> getFiles() {
        return files;
    }

    public void setFiles(List<SpecRenderingFileVO> files) {
        this.files = files;
    }
}
