package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import org.codehaus.jackson.map.annotate.JsonView;


import com.wiley.tes.util.res.JacksonResourceFactory;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/2/2016
 */
public class HWResponse extends ResourceStrId {
    //static final String RES_NAME = "HW_Response";

    private static final long serialVersionUID = 1L;

    private static final JacksonResourceFactory<HWResponse> FACTORY = JacksonResourceFactory.create(HWResponse.class,
            View.class);

    private String jobId = "";

    public HWResponse() {
    }

    public HWResponse(String jobId) {
        this.jobId = jobId;
    }

    @JsonView(View.class)
    public final String getJobId() {
        return jobId;
    }

    public String asJSONString() {
        return asString(FACTORY);
    }

    public static HWResponse create(String element) {
        try {
            return FACTORY.createResource(element);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("[HW jobId: %s]", getJobId());
    }

    /**
     * View to annotate
     */
    public static class View {
    }
}
