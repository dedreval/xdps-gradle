package com.wiley.cms.converter.services;

import javax.activation.DataHandler;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 05.09.11
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "result",
        namespace = IProvideConversion.NAMESPACE
    )
public class ConversionResult {
    private boolean successful;
    private String errors;
    private DataHandler pdf;

    public ConversionResult() {
    }

    public ConversionResult(boolean successful, String errors, DataHandler pdf) {
        this.successful = successful;
        this.errors = errors;
        this.pdf = pdf;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getErrors() {
        return errors;
    }

    public DataHandler getPdf() {
        return pdf;
    }
}
