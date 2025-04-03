package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.InputStream;

import org.jdom.Document;

import com.wiley.cms.cochrane.utils.xml.JDOMHelper;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.jdom.DocumentLoader;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 08/06/2021
 */
public class AriesMetadataFile {
    private static final String SUBMISSION_PATH = "//Submission";
    private static final String MANUSCRIPT_NUMBER_PATH = SUBMISSION_PATH + "/ManuscriptNumber";

    // confluence.wiley.com/pages/viewpage.action?spaceKey=CCP&title=Q1+22+-+Support+Concurrent+Loading+Improvements
    private static final String PUBLICATION_PRIORITY_PATH = SUBMISSION_PATH
        + "/AdditionalManuscriptMetadata/Metadata[TransmittalCustomIdentifier/Value='PUBLICATION_PRIORITY']/Value";

    private static final String HIGH_PROFILE_PUBLICATION_PATH = SUBMISSION_PATH
        + "/AdditionalManuscriptMetadata/Metadata[TransmittalCustomIdentifier/Value='HIGH_PROFILE']/Value";

    private final String fileName;
    private String manuscriptNumber;
    private boolean publicationPriority;
    private boolean highProfile;
    private String err = "";

    AriesMetadataFile(String fileName) {
        this.fileName = fileName;
        err = String.format("metadata-file %s is defined in %s, but not parsed", fileName, Extensions.GO_XML);
    }
    
    void parseMetadataFile(InputStream is, DocumentLoader dl) {
        try {
            err = null;
            Document doc = dl.load(is);

            setManuscriptNumber(doc);
            setPublicationPriority(doc);
            setHighProfile(doc);

        } catch (Throwable e) {
            err = e.getMessage();
        }
    }

    String getFileName() {
        return fileName;
    }

    boolean isHighPublicationPriority() {
        return publicationPriority;
    }

    boolean isHighProfile() {
        return highProfile;
    }

    private void setPublicationPriority(Document doc) throws Exception {
        publicationPriority = "HIGH".equalsIgnoreCase(JDOMHelper.getElementValue(doc, PUBLICATION_PRIORITY_PATH));
    }

    private void setHighProfile(Document doc) throws Exception {
        highProfile = "TRUE".equalsIgnoreCase(JDOMHelper.getElementValue(doc, HIGH_PROFILE_PUBLICATION_PATH));
    }

    public String getManuscriptNumber() {
        return manuscriptNumber;
    }

    private void setManuscriptNumber(Document doc) throws Exception {
        manuscriptNumber = JatsHelper.getMandatoryValue(doc, MANUSCRIPT_NUMBER_PATH, MANUSCRIPT_NUMBER_PATH);
    }

    public String error() {
        return err;
    }
}
