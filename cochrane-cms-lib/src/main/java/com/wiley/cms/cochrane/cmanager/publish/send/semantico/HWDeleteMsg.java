package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonView;

import com.wiley.tes.util.res.JacksonResourceFactory;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/2/2016
 */
@XmlRootElement(name = HWDeleteMsg.RES_NAME)
public class HWDeleteMsg extends HWMsg {
    private static final long serialVersionUID = 1L;

    private static final JacksonResourceFactory<HWDeleteMsg> FACTORY = JacksonResourceFactory.create(
            HWDeleteMsg.class, View.class);

    private String[] dois;

    public HWDeleteMsg() {
    }

    public HWDeleteMsg(int publishId, String type, String freq, String[] list) {
        super(publishId, "", type, freq, false);
        //setId("" + publishId);
        setDois(list);
    }

    @JsonView(View.class)
    public final String[] getDois() {
        return dois;
    }

    public final void setDois(String[] dois) {
        this.dois = dois;
    }

    @Override
    @JsonView(HiddenView.class)
    public final String getFilePath() {
        return null;
    }

    @Override
    public final void setFilePath(String value) {
    }

    @Override
    @JsonView(HiddenView.class)
    public boolean getDisableStaticContent() {
        return false;
    }

    @Override
    public void setDisableStaticContent(boolean value) {
    }

    public static HWDeleteMsg create(String element) {
        try {
            return FACTORY.createResource(element);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
            return null;
        }
    }
}
