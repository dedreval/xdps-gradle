package com.wiley.cms.cochrane.medlinedownloader;

import java.io.Serializable;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.10.2009
 */
public interface IMedlineRequest extends Serializable {
    MedlineDownloaderPlan getPlan();

    MedlineDownloaderParameters getParameters();

    void setParameters(MedlineDownloaderParameters parameters);

    IMedlineCallback getCallback();
}
