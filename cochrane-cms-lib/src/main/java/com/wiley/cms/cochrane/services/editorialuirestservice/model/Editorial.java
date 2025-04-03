package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonValue;

import javax.validation.constraints.NotNull;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class Editorial {
    private String id;
    private String name;
    private ModelType modelType;

    public Editorial(String id, String name, ModelType modelType) {
        this.id = id;
        this.name = name;
        this.modelType = modelType;
    }

    @Schema(required = true, description = "Editorial ID")
    @JsonProperty("id")
    @NotNull
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Schema(description = "Editorial Main Title")
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

    /**
     * ModelType
     */
    public enum ModelType {
        REFERENCE_ARTICLE("ReferenceArticle");
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
}
