package com.wiley.cms.cochrane.cmanager.export.process;

import com.wiley.cms.cochrane.cmanager.ebch.process.GenProcessorParameters;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ExportParameters extends GenProcessorParameters {

    private boolean renderedText;
    private boolean renderedGraphics;
    private boolean renderedPdf;
    private boolean sourceGraphics;
    private boolean xmlSource;
    private boolean ml3gForIpad;
    private boolean isDvd;
    private boolean isRevManForIPackage;
    private boolean isJatsForIPackage;
    private boolean isWml21ForIPackage;
    private boolean isTaForIPackage;
    private boolean isWRForIPackage;

    public ExportParameters(String dbName, String userName) {
        super(dbName, userName);
    }

    public ExportParameters(int dbId, String userName) {
        super(dbId, userName);
    }

    public boolean isRenderedText() {
        return renderedText;
    }

    public void setRenderedText(boolean renderedText) {
        this.renderedText = renderedText;
    }

    public boolean isRenderedGraphics() {
        return renderedGraphics;
    }

    public void setRenderedGraphics(boolean renderedGraphics) {
        this.renderedGraphics = renderedGraphics;
    }

    public boolean isRenderedPdf() {
        return renderedPdf;
    }

    public void setRenderedPdf(boolean renderedPdf) {
        this.renderedPdf = renderedPdf;
    }

    public boolean isSourceGraphics() {
        return sourceGraphics;
    }

    public void setSourceGraphics(boolean sourceGraphics) {
        this.sourceGraphics = sourceGraphics;
    }

    public boolean isXmlSource() {
        return xmlSource;
    }

    public void setXmlSource(boolean xmlSource) {
        this.xmlSource = xmlSource;
    }

    public boolean isMl3gForIpad() {
        return ml3gForIpad;
    }

    public void setMl3gForIpad(boolean ml3gForIpad) {
        this.ml3gForIpad = ml3gForIpad;
    }

    public boolean isDvd() {
        return isDvd;
    }

    public void setDvd(boolean dvd) {
        isDvd = dvd;
    }

    public boolean isRevManForIPackage() {
        return isRevManForIPackage;
    }

    public void setRevManForIPackage(boolean value) {
        isRevManForIPackage = value;
    }

    public boolean isJatsForIPackage() {
        return isJatsForIPackage;
    }

    public void setJatsForIPackage(boolean value) {
        isJatsForIPackage = value;
    }

    public boolean isWml21ForIPackage() {
        return isWml21ForIPackage;
    }

    public void setWml21ForIPackage(boolean value) {
        isWml21ForIPackage = value;
    }

    public boolean isTaForIPackage() {
        return isTaForIPackage;
    }

    public void setTaForIPackage(boolean value) {
        isTaForIPackage = value;
    }

    public boolean isWRIPackage() {
        return isWRForIPackage;
    }

    public void setWRForIPackage(boolean value) {
        isWRForIPackage = value;
    }
}
