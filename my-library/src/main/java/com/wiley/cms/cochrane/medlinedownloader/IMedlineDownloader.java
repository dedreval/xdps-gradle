package com.wiley.cms.cochrane.medlinedownloader;

import java.util.Map;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 22.10.2009
 */
public interface IMedlineDownloader {
    void download(Map<String, String> params) throws MedlineDownloaderException;
}
