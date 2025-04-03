package com.wiley.cms.cochrane.converter.ml3g;

import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 03.04.2014
 */
public class Ml3gXmlAssets {

    private String xmlUri;
    private String xmlContent;
    private List<String> assetsUris;
    private Map<String, String> jatsTa;

    public Ml3gXmlAssets() {
    }

    public Ml3gXmlAssets(String uri) {
        xmlUri = uri;
    }

    public String getXmlUri() {
        return xmlUri;
    }

    public void setXmlUri(String xmlUri) {
        this.xmlUri = xmlUri;
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public void setXmlContent(String xmlContent) {
        this.xmlContent = xmlContent;
    }

    public List<String> getAssetsUris() {
        return assetsUris;
    }

    public void setAssetsUris(List<String> assetsUris) {
        this.assetsUris = assetsUris;
    }

    public String getSID() {
        return getXmlUri();
    }

    public void setJatsTranslations(Map<String, String> jatsTa) {
        this.jatsTa = jatsTa;
    }

    public Map<String, String> getJatsTranslations() {
        return jatsTa;
    }
}
