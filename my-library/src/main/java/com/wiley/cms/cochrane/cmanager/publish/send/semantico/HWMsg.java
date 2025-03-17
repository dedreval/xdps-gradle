package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import org.codehaus.jackson.map.annotate.JsonView;

import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.tes.util.res.JacksonResourceFactory;

import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceStrId;
import com.wiley.tes.util.res.Settings;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/2/2016
 */
public class HWMsg extends ResourceStrId {
    static final String RES_NAME = "HW_Transfer";

    private static final long serialVersionUID = 1L;

    private static final String CLIENT_ID = "001";
    private static final String SITE_ID = "01";

    private static final JacksonResourceFactory<HWMsg> FACTORY = JacksonResourceFactory.create(HWMsg.class, View.class);

    private static final Res<Settings> DB_MAP = CmsResourceInitializer.getHWDbMapping();

    private String clientId = CLIENT_ID;
    private String siteId = SITE_ID;
    private String jmsNameOrFilePath;
    private String type;
    private String frequency;
    private boolean staticContent;

    public HWMsg() {
    }

    public HWMsg(int publishId, String path, String type, String freq, boolean disableStaticContent) {

        setId(SemanticoMsg.buildMessageSid(publishId));

        setFilePath(path);
        setContentType(type);
        setFrequency(freq);
        setDisableStaticContent(disableStaticContent);
    }

    @JsonView(View.class)
    public final String getClientCode() {
        return clientId;
    }

    public final void setClientCode(String value) {
        clientId = value;
    }

    @JsonView(View.class)
    public final String getSiteCode() {
        return siteId;
    }

    public final void setSiteCode(String value) {
        siteId = value;
    }

    @JsonView(View.class)
    public final String getContentType() {
        return type;
    }

    public final void setContentType(String value) {
        String str = DB_MAP.get().getStrSetting(value);
        type = (str != null) ? str : value;
    }

    @JsonView(View.class)
    public String getFilePath() {
        return jmsNameOrFilePath;
    }

    public void setFilePath(String value) {
        jmsNameOrFilePath = value;
    }

    @JsonView(View.class)
    public final String getFrequency() {
        return frequency;
    }

    public final void setFrequency(String value) {
        frequency = value;
    }

    @JsonView(View.class)
    public boolean getDisableStaticContent() {
        return staticContent;
    }

    public void setDisableStaticContent(boolean value) {
        staticContent = value;
    }

    public String asJSONString() {
        return asString(FACTORY);
    }

    public static HWMsg create(String element) {
        try {
            return FACTORY.createResource(element);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return null;
        }
    }

    /**
     * View to annotate
     */
    public static class View {
    }

    /**
      * View to annotate and hidden
      */
    public static class HiddenView {
    }
}
