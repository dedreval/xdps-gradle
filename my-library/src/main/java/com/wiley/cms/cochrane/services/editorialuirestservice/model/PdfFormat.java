package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonValue;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class PdfFormat {

    private Mnemonic mnemonic;

    public PdfFormat(Mnemonic mnemonic) {
        this.mnemonic = mnemonic;
    }

    @Schema()
    @JsonProperty("mnemonic")
    public Mnemonic getMnemonic() {
        return mnemonic;
    }

    public void setMnemonic(Mnemonic mnemonic) {
        this.mnemonic = mnemonic;
    }

    /**
     * Gets or Sets mnemonic
     */
    public enum Mnemonic {
        PDF("application/pdf");

        private String value;

        Mnemonic(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }
}
