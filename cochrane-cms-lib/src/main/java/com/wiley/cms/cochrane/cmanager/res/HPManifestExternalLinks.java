package com.wiley.cms.cochrane.cmanager.res;

import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.DomHandler;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 06.03.2015
 */
@XmlRootElement(name = HPManifestExternalLinks.ELEMENT_NAME)
@Deprecated
public class HPManifestExternalLinks {

    public static final String ELEMENT_NAME = "hpmanifestExternalLinks";

    private String extLinksStub;
    private StringBuilder links = new StringBuilder();

    @XmlAttribute(name = "cdsr-image-file")
    private String cdsrImageName = "cochrane-cdsr-wol.jpg";

    @XmlAttribute(name = "cdsr-image-type")
    private String cdsrImageType = "image/jpeg";

    public String getExtLinksStub() {
        return extLinksStub;
    }

    @XmlAttribute
    public void setExtLinksStub(String extLinksStub) {
        this.extLinksStub = extLinksStub;
    }

    public String getCdsrImageName() {
        return cdsrImageName;
    }

    public String replaceImageNameAndType(String content) {
        String ret = content.replace("{cdsr-image-file}", cdsrImageName);
        ret = ret.replace("{cdsr-image-type}", cdsrImageType);
        return ret;
    }

    public String getLinks() {
        return links.toString();
    }

    @XmlAnyElement(Handler.class)
    public void setLinks(String links) {
        this.links.append(links).append('\n');
    }

    /**
     *
     */
    private static class Handler implements DomHandler<String, StreamResult> {

        private static final String OPEN_TAG = "<Page";

        private StringWriter writer = new StringWriter();

        public StreamResult createUnmarshaller(ValidationEventHandler validationEventHandler) {
            writer.getBuffer().setLength(0);
            return new StreamResult(writer);
        }

        public String getElement(StreamResult streamResult) {
            String xml = streamResult.getWriter().toString();
            return xml.substring(xml.indexOf(OPEN_TAG));
        }

        public Source marshal(String s, ValidationEventHandler validationEventHandler) {
            return new StreamSource(new StringReader(s));
        }
    }
}
