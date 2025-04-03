package com.wiley.cms.cochrane.cmanager.export.data;

import java.io.Serializable;
import java.util.Date;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ExportVO implements Serializable {
    private Integer id;
    private Date date;
    private String filePath;
    private int state;
    private int itemAmount;
    private String user;
    private Integer clDbId;
    private String dbName;

    public ExportVO() {
    }

    public ExportVO(ExportVO copy, String filePath, int state) {

        setDate(copy.getDate());
        setItemAmount(copy.getItemAmount());
        setUser(copy.getUser());
        setClDbId(copy.getClDbId());
        setDbName(copy.getDbName());

        setFilePath(filePath);
        setState(state);
    }

    public ExportVO(ExportEntity entity) {
        setId(entity.getId());
        setDate(entity.getDate());
        setFilePath(entity.getFilePath());
        setState(entity.getState());
        setItemAmount(entity.getItemAmount());
        setUser(entity.getUser());
        setClDbId(entity.getClDb() != null
                ? entity.getClDb().getId()
                : null);
        setDbName(entity.getDb().getName());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean isExists() {
        return id != null && id > 0;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getItemAmount() {
        return itemAmount;
    }

    public void setItemAmount(int itemAmount) {
        this.itemAmount = itemAmount;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Integer getClDbId() {
        return clDbId;
    }

    public void setClDbId(Integer clDbId) {
        this.clDbId = clDbId;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }
}
