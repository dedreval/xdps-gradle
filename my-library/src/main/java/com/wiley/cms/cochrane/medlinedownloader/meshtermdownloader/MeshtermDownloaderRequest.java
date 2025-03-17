package com.wiley.cms.cochrane.medlinedownloader.meshtermdownloader;

import com.wiley.cms.cochrane.medlinedownloader.IMedlineCallback;
import com.wiley.cms.cochrane.medlinedownloader.IMedlineRequest;
import com.wiley.cms.cochrane.medlinedownloader.MedlineDownloaderParameters;
import com.wiley.cms.cochrane.medlinedownloader.MedlineDownloaderPlan;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.10.2009
 */
public class MeshtermDownloaderRequest implements IMedlineRequest {
    private MedlineDownloaderParameters parameters;
    private MedlineDownloaderPlan plan;
    private IMedlineCallback callback;

    public MeshtermDownloaderRequest(int issue, int issueId, String title) {
        this.plan = MedlineDownloaderPlan.MESHTERM_DOWNLOAD;
        this.callback = new MeshtermDownloaderCallback(issue, issueId, title);
    }

    public MedlineDownloaderPlan getPlan() {
        return plan;
    }

    public MedlineDownloaderParameters getParameters() {
        return parameters;
    }

    public void setParameters(MedlineDownloaderParameters parameters) {
        this.parameters = parameters;
    }

    public IMedlineCallback getCallback() {
        return callback;
    }
}
