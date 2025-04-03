package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonValue;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class Pdf {
    private String id;
    private String fileName;
    private ModelType modelType;
    private PdfFormat format;

    public Pdf(String id, String fileName, ModelType modelType, PdfFormat format) {
        this.id = id;
        this.fileName = fileName;
        this.modelType = modelType;
        this.format = format;
    }

    @Schema(description = "Rendition ID")
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Schema(description = "File Name")
    @JsonProperty("fileName")
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Schema(description = "ModelType")
    @JsonProperty("modelType")
    public ModelType getModelType() {
        return modelType;
    }

    public void setModelType(ModelType modelType) {
        this.modelType = modelType;
    }

    @Schema()
    @JsonProperty("format")
    public PdfFormat getFormat() {
        return format;
    }

    public void setFormat(PdfFormat format) {
        this.format = format;
    }

    /**
     * ModelType
     */
    public enum ModelType {
        RENDITION("Rendition");

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
