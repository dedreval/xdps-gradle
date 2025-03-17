package com.wiley.cms.cochrane.cmanager.data;

import java.io.Serializable;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 24-Jul-2007
 */
public class DelayedThread implements Serializable {
    private final int jobId;
    private final String result;

    public DelayedThread(final int jobId, final String result) {
        this.jobId = jobId;
        this.result = result;
    }

    public int getJobId() {
        return jobId;
    }

    public String getResult() {
        return result;
    }
}
