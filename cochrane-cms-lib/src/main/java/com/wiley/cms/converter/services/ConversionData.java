package com.wiley.cms.converter.services;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.tes.util.Extensions;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 05.09.11
 */
public class ConversionData {
    private final String dir;
    private String contentDir;
    private String sourceXml;
    private String metadataXml;
    private String pubName;
    private String sourceContainerXml;
    private String resultContainerXml;
    private String wml21Xml;
    private String wml3gXml;
    private String name;
    private String reviewType;
    private String[] params;
    private List<File> assets;
    private boolean jatsAries;
    private boolean isRawDataExists;
    private boolean jatsStatsPresent;
    private boolean convertWml3g = false;
    private boolean convertRevman = false;
    private boolean convertJatsToWml3g = false;
   
    public ConversionData() {
        dir = generateTempDirPath();
    }

    public ConversionData(String baseDirPath) {
        dir = FilenameUtils.separatorsToUnix(baseDirPath);
    }

    public void setConvertToWml3g(boolean value) {
        convertWml3g = value;
    }

    public boolean convertToWml3g() {
        return convertWml3g;
    }

    public void setConvertToRevman(boolean value) {
        convertRevman = value;
    }

    public boolean convertToRevman() {
        return convertRevman;
    }

    public void setConvertJatsToWml3g(boolean value) {
        convertJatsToWml3g = value;
    }

    public boolean convertJatsToWml3g() {
        return convertJatsToWml3g;
    }

    public String getPubName() {
        return pubName;
    }

    public void setPubName(String pubName) {
        this.pubName = pubName;
    }

    public List<File> getAssets() {
        return assets;
    }

    public void setAssets(List<File> assets) {
        this.assets = assets;
    }

    public boolean isJatsAries() {
        return jatsAries;
    }

    public void setJatsAries(boolean jatsAries) {
        this.jatsAries = jatsAries;
    }

    public boolean isJatsStatsPresent() {
        return jatsStatsPresent;
    }

    public void setJatsStatsPresent(boolean jatsStatsPresent) {
        this.jatsStatsPresent = jatsStatsPresent;
    }

    public String getSourceXml() {
        return sourceXml;
    }

    public void setSourceXml(String sourceXml) {
        this.sourceXml = sourceXml;
    }

    public String getMetadataXml() {
        return metadataXml;
    }

    public void setMetadataXml(String metadataXml) {
        this.metadataXml = metadataXml;
    }

    public String getResultContainerXml() {
        return resultContainerXml;
    }

    public void setResultContainerXml(String containerXml) {
        this.resultContainerXml = containerXml;
    }

    public String getWml21Xml() {
        return wml21Xml;
    }

    public void setWml21Xml(String wml21Xml) {
        this.wml21Xml = wml21Xml;
    }

    public String getWml3gXml() {
        return wml3gXml;
    }

    public void setWml3gXml(String wml3gXml) {
        this.wml3gXml = wml3gXml;
    }

    public String getTempDir() {
        return dir;
    }

    public String getName() {
        return name;
    }

    public String getDbName() {
        return name != null && name.startsWith("ED") ? CochraneCMSPropertyNames.getEditorialDbName()
                : CochraneCMSPropertyNames.getCDSRDbName();
    }

    public String getWml21Name() {
        return convertWml3g && convertRevman ? "wml21" : getName();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentDir() {
        if (contentDir == null) {
            contentDir = dir + "/" + name;
        }
        return contentDir;
    }

    public boolean isRawDataExists() {
        return isRawDataExists;
    }

    public void setRawDataExists(boolean rawDataExists) {
        isRawDataExists = rawDataExists;
    }

    public String getJatsXmlPath() {
        return getContentDir() + Extensions.XML;
    }

    public URI getWml21XmlUri() throws Exception {
        return FilePathCreator.getUri(dir + "/" + getWml21Name() + Extensions.XML);
    }

    public URI getWml3GXmlUri() throws Exception {
        return FilePathCreator.getUri(dir + "/" + getName() + Extensions.XML);
    }

    public String getSourceContainerXml() {
        return sourceContainerXml;
    }

    public void setSourceContainerXml(String sourceContainerXml) {
        this.sourceContainerXml = sourceContainerXml;
    }

    public String getReviewType() {
        return reviewType;
    }

    public void setReviewType(String reviewType) {
        this.reviewType = reviewType;
    }

    public String[] getParams() {
        return params;
    }

    public void setParams(String[] params) {
        this.params = params;
    }

    public static String generateTempDirPath() {
        String timestamp = String.valueOf(new Date().getTime());
        String sysRoot = StringUtils.substringAfter(RepositoryUtils.getSystemRoot(), "file://");
        return (sysRoot.contains(":") ? sysRoot.substring(1) : sysRoot) + "/"
                 + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/temp/" + timestamp;
    }
}
