package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class EditorialsListWithPagination {
    private List<Editorial> items;
    private Integer offset;
    private Integer total;

    public EditorialsListWithPagination(List<Editorial> items, Integer offset, Integer total) {
        this.items = items;
        this.offset = offset;
        this.total = total;
    }

    @Schema()
    @JsonProperty("items")
    public List<Editorial> getItems() {
        return items;
    }

    public void setItems(List<Editorial> items) {
        this.items = items;
    }

    @Schema(description = "The corrected offset")
    @JsonProperty("offset")
    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    @Schema(description = "Total amount of items with current request parameters")
    @JsonProperty("total")
    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}
