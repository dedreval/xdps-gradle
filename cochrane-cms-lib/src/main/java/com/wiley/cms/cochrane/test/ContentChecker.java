package com.wiley.cms.cochrane.test;

import com.wiley.cms.cochrane.cmanager.res.TransAbs;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.jdom.DocumentLoader;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/31/2020
 */
public class ContentChecker {
    public static final String TAG_FIRST_ONLINE = "firstOnline";
    public static final String TAG_ONLINE_FINAL_FORM = "publishedOnlineFinalForm";
    public static final String TAG_ONLINE_CITATION_ISSUE = "publishedOnlineCitationIssue";
    public static final String TAG_ACCESSION_ID = "accessionId";

    public static final String SUPPLEMENTARY_MATERIALS_PATH_FOR_NRF =
            "//*[local-name()='supplementary-material']/@xlink:href";

    protected static final String SUPPLEMENTARY_MATERIALS_PATHS =
            SUPPLEMENTARY_MATERIALS_PATH_FOR_NRF + "| //ext-link[@ext-link-type='revman-statistics']/@xlink:href";

    protected static final String WML3G_PUBLICATION_UNIT_PATH = "//*[local-name()='publicationMeta'][@level='unit']";

    protected static final String WML3G_PUBLICATION_PART_PATH = "//*[local-name()='publicationMeta'][@level='part']"
            + "/*[local-name()='numberingGroup']/*[local-name()='numbering']";

    protected static final String WML3G_PUBLICATION_CITATION_PATH = WML3G_PUBLICATION_UNIT_PATH
            + "/*[local-name()='selfCitationGroup']/*[local-name()='citation'][@type='self']";
    protected static final String WML3G_ACCESSION_ID_PATH = WML3G_PUBLICATION_UNIT_PATH
            + "/*[local-name()='selfCitationGroup']/*[local-name()='citation']/*[local-name()='accessionId']";
    protected static final String WML3G_ACCESSION_ID_REF_PATH = WML3G_ACCESSION_ID_PATH + "/@ref";

    protected static final String WML3G_CONTENT_META_PATH = "//*[local-name()='contentMeta']";

    protected static final String WML3G_TITLE_PATH = WML3G_CONTENT_META_PATH
            + "/*[local-name()='titleGroup']/*[local-name()='title'][@type='main']";
    protected static final String WML3G_STATUS_PATH = WML3G_CONTENT_META_PATH
            + "/*[local-name()='titleGroup']/*[local-name()='title'][@type='clStatus']";

    protected static final String WML3G_PDF_LINKS_PATH = WML3G_PUBLICATION_UNIT_PATH
            + "/*[local-name()='linkGroup']/*[local-name()='link']";

    protected static final String WML3G_FIRST_ONLINE_PATH = WML3G_PUBLICATION_UNIT_PATH
            + "/*[local-name()='eventGroup']/*[local-name()='event'][@type='firstOnline']/@date";
    protected static final String WML3G_FINAL_ONLINE_FORM_PATH = WML3G_PUBLICATION_UNIT_PATH
            + "/*[local-name()='eventGroup']/*[local-name()='event'][@type='publishedOnlineFinalForm']/@date";
    protected static final String WML3G_CITATION_ONLINE_ISSUE_PATH = WML3G_PUBLICATION_UNIT_PATH
            + "/*[local-name()='eventGroup']/*[local-name()='event'][@type='publishedOnlineCitationIssue']/@date";

    protected static final String WML3G_JOURNAL_VOLUME_PATH = WML3G_PUBLICATION_PART_PATH + "[@type='journalVolume']";
    protected static final String WML3G_JOURNAL_ISSUE_PATH  = WML3G_PUBLICATION_PART_PATH + "[@type='journalIssue']";

    protected static final String WML3G_SELF_CITATION_VOLUME_PATH = WML3G_PUBLICATION_CITATION_PATH
            + "/*[local-name()='vol']";
    protected static final String WML3G_SELF_CITATION_ISSUE_PATH  = WML3G_PUBLICATION_CITATION_PATH
            + "/*[local-name()='issue']";

    protected static final String WML3G_PUBLICATION_TYPE_PATH = WML3G_PUBLICATION_UNIT_PATH + "/@type";
    protected static final String WML3G_DOI_PATH = WML3G_PUBLICATION_UNIT_PATH
            + "/*[local-name()='doi']";
    protected static final String WML3G_LICENSE_PATH = WML3G_PUBLICATION_UNIT_PATH + "/@accessType";



    protected static final String GRAPHIC_PATH = "//*[local-name()='fig']/graphic/@*[local-name()='href'] "
            + "| //*[local-name()='mediaResource'][@mimeType]/@href";

    protected final DocumentLoader documentLoader;

    //private static final HashMap<String, String> XPATH_MESSAGES = new HashMap<>();

    //static {
    //    xPathMsg(WM3G_FIRST_ONLINE,
    //            "[publicationMeta][@level='unit']/[eventGroup]/[event][@type='firstOnline']");
    //    xPathMsg(WM3G_FINAL_ONLINE_FORM,
    //            "[publicationMeta][@level='unit']/[eventGroup]/[event][@type='publishedOnlineFinalForm']");
    //    xPathMsg(WM3G_CITATION_ONLINE_ISSUE,
    //            "[publicationMeta][@level='unit']/[eventGroup]/[event][@type='publishedOnlineCitationIssue']");
    //}

    //protected static void xPathMsg(String xPath, String message) {
    //    XPATH_MESSAGES.put(xPath, message);
    //}

    //protected static String xPathMsg(String xPath) {
    //    String ret = XPATH_MESSAGES.get(xPath);
    //    return ret == null ? xPath : ret;
    //}

    public ContentChecker() {
        documentLoader = new DocumentLoader();
    }

    public ContentChecker(DocumentLoader loader) {
        documentLoader = loader;
    }

    public final DocumentLoader getDocumentLoader() {
        return documentLoader;
    }

    public static String getUnitPublicationType(String ml3g, DocumentLoader loader) throws Exception {
        Document doc = loader.load(ml3g);
        return getAttributeValue(XPath.selectSingleNode(doc, WML3G_PUBLICATION_TYPE_PATH));
    }

    private static String getAttributeValue(Object node) {
        return node instanceof Attribute ? ((Attribute) node).getValue() : null;
    }

    /**
     * Check if known language codes are present in ML3G content
     * @param ml3g               The content file
     * @param pubName            The publisher name: <cd number>[.<publication number>]
     * @param etalonLanguages    The set of language codes expected to be in content
     * @return                   Error or NULL
     */
    public String checkLanguageCodes4ML3G(File ml3g, String pubName, Set<String> etalonLanguages) {
        StringBuilder err = null;

        Set<String> languages = new HashSet<>(etalonLanguages);
        languages.add(Constants.ENGLISH_CODE);

        Set<String> fopLanguages = new HashSet<>(1);
        try {
            Document doc = documentLoader.load(ml3g);
            TransAbs transAbs = TransAbs.get().get();
            List nodes = XPath.selectNodes(doc, WML3G_TITLE_PATH);

            for (Object node : nodes) {
                if (node instanceof Element) {
                    String language = getLangAttributeValue((Element) node);
                    if (!languages.remove(language)) {
                        err = addError(String.format("'%s' language found in content", language), err);

                    } else if (transAbs.is4Fop(language)) {
                        fopLanguages.add(language);
                    }
                }
            }
            for (String language: languages)  {
                err = addError(String.format("'%s' language not found in content", language), err);
            }
            nodes = XPath.selectNodes(doc, WML3G_PDF_LINKS_PATH);
            boolean hasEnglishPdf = false;

            for (Object node : nodes) {
                if (node instanceof Element) {
                    String link = getAttributeValue((Element) node, "href");

                    if (!link.endsWith(Extensions.PDF)) {
                        continue;
                    }
                    String language = getLangAttributeValue((Element) node);
                    if (Constants.ENGLISH_CODE.equals(language)) {
                        hasEnglishPdf = true;
                        continue;
                    }
                    if (!link.contains(Constants.PDF_ABSTRACT_SUFFIX)) {
                        continue;
                    }
                    if (!transAbs.is4Fop(language) || !fopLanguages.remove(language)) {
                        err = addError(String.format("%s link found in content", link), err);
                    }
                }
            }
            if (!hasEnglishPdf) {
                err = addError("English FOP PDF link not found in content", err);
            }
            for (String language: fopLanguages) {
                err = addError(String.format("'%s' FOP PDF link not found in content", language), err);
            }
        } catch (Exception e) {
            err = addError(e.getMessage(), err);
        }
        return err == null ? null : String.format("%s: %s", pubName, err);
    }

    private StringBuilder addError(String msg, StringBuilder err) {
        StringBuilder ret = err;
        if (ret == null) {
            ret = new StringBuilder();
        }
        ret.append(msg).append("\n");
        return ret;
    }

    private static String getAttributeValue(Element element, String attribute) throws Exception {
        return getAttributeValue(element, attribute, Namespace.NO_NAMESPACE);
    }

    private static String getLangAttributeValue(Element element) throws Exception {
        return getXmlAttributeValue(element, "lang");
    }

    private static String getXmlAttributeValue(Element element, String attribute) throws Exception {
        return getAttributeValue(element, attribute, Namespace.XML_NAMESPACE);
    }

    private static String getAttributeValue(Element element, String attribute, Namespace namespace) throws Exception {
        String ret = element.getAttributeValue(attribute, namespace);
        if (ret == null) {
            throw new Exception(String.format("%s of %s is null", attribute, element.getName()));
        }
        return ret;
    }
}
