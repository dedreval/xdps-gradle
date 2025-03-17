package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonValue;

import javax.validation.constraints.NotNull;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class Issue {
    private String id;
    private String name;
    private ModelType modelType;
    private State state;

    public Issue(String id, String name, ModelType modelType, State state) {
        this.id = id;
        this.name = name;
        this.modelType = modelType;
        this.state = state;
    }

    @Schema(required = true, description = "Issue ID")
    @JsonProperty("id")
    @NotNull
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Schema(description = "Main Title")
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Schema(required = true, description = "ModelType")
    @JsonProperty("modelType")
    @NotNull
    public ModelType getModelType() {
        return modelType;
    }

    public void setModelType(ModelType modelType) {
        this.modelType = modelType;
    }

    @Schema(description = "Status")
    @JsonProperty("state")
    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    /**
     * ModelType
     */
    public enum ModelType {
        ISSUE("Issue");
        private String value;

        ModelType(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }

    /**
     * Status
     */
    public enum State {
        ACTIVE(""),
        LAST_ISSUE("Last Issue");

        private String value;

        State(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }
}
