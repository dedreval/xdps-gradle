package com.wiley.cms.cochrane.cmanager;

import java.io.Serializable;

import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class RecordLightVO implements Serializable {
    private int id;
    private String name;
    private boolean isSuccessful = true;
    private String errorMessage;

    public RecordLightVO(RecordEntity re) {
        this(re.getId(), re.getName(), re.isQasSuccessful() && re.isRenderingSuccessful());
    }

    public RecordLightVO(int id, String name, boolean isSuccessful) {
        this.id = id;
        this.name = name;
        this.isSuccessful = isSuccessful;
    }

    public RecordLightVO(int id, String name, boolean successful, String errorMessage) {
        this.id = id;
        this.name = name;
        isSuccessful = successful;
        this.errorMessage = errorMessage;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public void setSuccessful(boolean successful) {
        isSuccessful = successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
