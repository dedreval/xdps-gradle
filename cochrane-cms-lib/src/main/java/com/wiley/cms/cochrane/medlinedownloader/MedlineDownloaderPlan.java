package com.wiley.cms.cochrane.medlinedownloader;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 22.10.2009
 */
public enum MedlineDownloaderPlan {
    MESHTERM_DOWNLOAD("com.wiley.cms.cochrane.medlinedownloader.meshtermdownloader.MeshtermDownloader");

    private String code;

    MedlineDownloaderPlan(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
