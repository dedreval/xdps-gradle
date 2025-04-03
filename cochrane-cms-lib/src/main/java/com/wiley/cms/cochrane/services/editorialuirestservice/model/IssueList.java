package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class IssueList {

    private List<Issue> items;

    public IssueList(List<Issue> items) {
        this.items = items;
    }

    @Schema()
    @JsonProperty("items")
    public List<Issue> getItems() {
        return items;
    }

    public void setItems(List<Issue> items) {
        this.items = items;
    }
}