package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Resource;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 5/10/2021
 */
@XmlRootElement(name = "scheduled-publications")
public class SPDManifest extends Resource<String> {
    private static final long serialVersionUID = 1L;

    private static final JaxbResourceFactory<SPDManifest> FACTORY = JaxbResourceFactory.create(SPDManifest.class,
            SPDSubmission.class).enableFormatter();

    @XmlElement(name = "submission")
    private List<SPDSubmission> submissions;

    private String deliveryId;

    public SPDManifest() {
    }

    public SPDManifest(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public static SPDManifest create(String xml) throws CmsException {
        try {
            return FACTORY.createResource(xml);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
            throw new CmsException(e);
        }
    }

    @Override
    protected String getIdFromString(String id) {
        return "";
    }

    public void addSubmission(@NotNull String doi, String targetOnlineDate, boolean publishingCanceled) {
        addSubmission(doi, targetOnlineDate, deliveryId, publishingCanceled);
    }

    public void addSubmission(@NotNull String doi, String targetOnlineDate, @NotNull String deliveryId,
                              boolean publishingCanceled) {
        if (targetOnlineDate != null) {
            if (submissions == null) {
                submissions = new ArrayList<>();
            }
            submissions.add(publishingCanceled ? new SPDSubmission(doi)
                    : new SPDSubmission(doi, deliveryId, targetOnlineDate));
        }
    }

    public String asXmlString() {
        return asString(FACTORY);
    }

    public List<SPDSubmission> getSubmissions() {
        return submissions;
    }

    /**
     *
     */
    public static class SPDSubmission implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlAttribute(name = "doi")
        private String doi;

        @XmlAttribute(name = "package-id")
        private String deliveryId;

        @XmlElement(name = "target-online-pub-date")
        private String targetOnlineDate;

        @XmlAttribute(name = "cancelled")
        private Boolean cancelled;
        
        private SPDSubmission() {
        }

        private SPDSubmission(String doi) {
            this.doi = doi;
            cancelled = true;
        }

        private SPDSubmission(String doi, String deliveryId, String targetOnlineDate) {
            this.doi = doi;
            this.deliveryId = deliveryId;
            this.targetOnlineDate = targetOnlineDate;
        }

        public String getTargetOnlineDate() {
            return targetOnlineDate;
        }

        public String getDeliveryId() {
            return deliveryId;
        }

        public String getDoi() {
            return doi;
        }

        public Boolean getCancelled() {
            return cancelled;
        }
    }
}
