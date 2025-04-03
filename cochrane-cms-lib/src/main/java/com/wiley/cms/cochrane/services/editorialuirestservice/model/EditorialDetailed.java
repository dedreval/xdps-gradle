package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class EditorialDetailed extends Editorial {
    private String publicationDate;
    private List<Pdf> hasRendition;

    public EditorialDetailed(String id, String name, ModelType modelType, List<Pdf> hasRendition) {
        super(id, name, modelType);
        this.hasRendition = hasRendition;
    }

    @Schema(description = "Planned Publish Date and Time")
    @JsonProperty("publicationDate")
    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    @Schema()
    @JsonProperty("hasRendition")
    public List<Pdf> getItems() {
        return hasRendition;
    }

    public void setItems(List<Pdf> hasRendition) {
        this.hasRendition = hasRendition;
    }
}
