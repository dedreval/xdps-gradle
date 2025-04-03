package com.wiley.cms.cochrane.cmanager.contentworker;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.cochrane.cmanager.res.ContentAccessType;
import com.wiley.cms.cochrane.cmanager.res.PackageType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.test.ContentChecker;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.xml.JDOMHelper;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.Settings;
import org.apache.commons.io.IOUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static com.wiley.cms.cochrane.cmanager.res.PackageType.EntryType.IMAGE;
import static com.wiley.cms.cochrane.cmanager.res.PackageType.EntryType.STATS;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 6/8/2021
 */
public class ContentHelper extends ContentChecker {
    public static final String IMAGE_N = "image_n/";
    protected static final String ERR_PUB_DATE_PRESET_WITH_TBD =
            ", an article that contains a publication date should not include non-numerical values in %s";

    protected static final String ERR_PUB_ISSUE_PRESET_WITH_TBD =
            ", an article that contains a publication issue/volume should not include non-numerical values in %s";

    protected static final Res<Settings> ARTICLE_TYPES = CmsResourceInitializer.getContentTypesMapping();

    private static final Logger LOG = Logger.getLogger(ContentHelper.class);

    private static final Res<Property> SAVE_TMP_RESULTS = Property.get(
            "cms.cochrane.conversion.ml3g.save-tmp-results", Boolean.TRUE.toString());

    private Map<String, String> messageTags;

    public ContentHelper() {
    }

    public ContentHelper(DocumentLoader loader) {
        super(loader);
    }

    protected String getDoiPath() {
        return WML3G_DOI_PATH;
    }

    protected String getLicencePath() {
        return WML3G_LICENSE_PATH;
    }

    protected String getTypePath() {
        return WML3G_PUBLICATION_TYPE_PATH;
    }

    protected String getCochraneVersion(Document doc) throws Exception {
        return ArchieEntry.NONE_VERSION;
    }

    protected void extractMetadata(CDSRMetaVO meta, String doi, Document doc, String recordPath,
                                   int issueNumber, BaseType bt, IRecordCache cache) throws Exception {
        String cdNumber = meta.getCdNumber();
        int pub = meta.getPubNumber();
        String version = meta.getCochraneVersion();
        meta.setTitle(getMandatoryValue(cdNumber, pub, version, doc, WML3G_TITLE_PATH));
        GroupVO groupVO = null;
        String wmlType = getMandatoryAttrValue(cdNumber, pub, version, doc, getTypePath());
        if (bt.isEditorial()) {
            checkDoi(meta, doi);
            meta.setUnitStatusId(UnitStatusEntity.UnitStatus.UNCHANGED);
            groupVO = cache.getCRGGroup(GroupVO.SID_EDI);

        } else if (bt.isCCA()){
            checkCCADoi(meta, doi, CCAWorker.CCA_DOI_PATTERN, ErrorInfo.Type.WRONG_DOI);
            String accessDoi = getMandatoryValue(cdNumber, pub, version, doc, WML3G_ACCESSION_ID_PATH);
            checkCCADoi(meta, accessDoi, CCAWorker.CCA_DOI_PATTERN, ErrorInfo.Type.WRONG_ACCESSION_ID_DOI);
            accessDoi = getMandatoryAttrValue(cdNumber, pub, version, doc, WML3G_ACCESSION_ID_REF_PATH);
            checkCCADoi(meta, accessDoi, CCAWorker.CCA_DOI_ACCESSION_ID_PATTERN, ErrorInfo.Type.WRONG_ACCESSION_ID_REF);

            String clStatus = getValue(cdNumber, pub, version, doc, WML3G_STATUS_PATH, false, false);
            if (clStatus == null) {
                throw CmsException.create(meta, ErrorInfo.Type.NO_UNIT_STATUS);
            }
            UnitStatusVO statusVO = cache.getUnitStatus(clStatus, false);
            if (statusVO == null || !UnitStatusEntity.CCA_AVAILABLE_STATUSES.contains(statusVO.getId())) {
                throw CmsException.create(meta, ErrorInfo.Type.WRONG_UNIT_STATUS, clStatus);
            }
            meta.setUnitStatusId(statusVO.getId());
            groupVO = cache.getCRGGroup(GroupVO.SID_CCA);

        }

        meta.setStatus(RecordMetadataEntity.RevmanStatus.NONE.dbKey);
        if (groupVO != null) {
            meta.setGroupSid(groupVO.getName());
            meta.setGroupTitle(groupVO.getUnitTitle());
        }

        meta.setType(getSetting(meta, wmlType, ARTICLE_TYPES.get()), null);
        meta.setWMLPublicationType(wmlType);
        meta.setStage(meta.getType());
        setDates(cdNumber, pub, version, issueNumber, doc, meta, bt);

        meta.setAccessTypeMetadata(getAccessType(cdNumber, pub, doc));
        meta.setPath(recordPath);
    }

    public final CDSRMetaVO extractMetadata(String cdNumber, int pub, String recordPath, int issueNumber, BaseType bt,
                                            IRepository rp, IRecordCache cache) throws Exception {
        InputStream is = null;
        CDSRMetaVO meta = null;
        try {
            is = rp.getFile(recordPath);
            Document doc = documentLoader.load(is);
            String version = getCochraneVersion(doc);
            String doi = getMandatoryValue(cdNumber, pub, version, doc, getDoiPath());
            meta = new CDSRMetaVO(cdNumber, pub, version);
            extractMetadata(meta, doi, doc, recordPath, issueNumber, bt, cache);
            return meta;

        } catch (CmsException ce) {
            if (meta != null && ce.hasErrorInfo()) {
                ce.getErrorInfo().setErrorEntity(meta);
            }
            throw ce;
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    protected void setDates(String cdNumber, int pub, String version, int xdpsIssueNumber, Document doc, CDSRMetaVO ret,
                            BaseType baseType) throws Exception {
        Date date = new Date();
        ret.setIssue(xdpsIssueNumber, date);
        boolean cca = baseType.isCCA();
        boolean mandatory = !baseType.isActualPublicationDateSupported() && !cca;

        int versionIssueNumber = cca ? checkIssue(cdNumber, pub, version, doc,
            WML3G_JOURNAL_VOLUME_PATH, WML3G_JOURNAL_ISSUE_PATH) : buildIssue(cdNumber, pub, version, doc, mandatory,
                WML3G_JOURNAL_VOLUME_PATH, WML3G_JOURNAL_ISSUE_PATH);

        String finalDateStr = getValue(cdNumber, pub, version, doc, WML3G_FINAL_ONLINE_FORM_PATH, mandatory, true);
        Date finalDate = parseDate(finalDateStr, cdNumber, pub, version, WML3G_FINAL_ONLINE_FORM_PATH, mandatory);
        if (finalDate == null) {
            finalDateStr = null;
        }

        String firstDateStr = getValue(cdNumber, pub, version, doc, WML3G_FIRST_ONLINE_PATH, mandatory, true);
        Date firstDate = parseDate(firstDateStr, cdNumber, pub, version, WML3G_FIRST_ONLINE_PATH, mandatory);
        if (firstDate == null) {
            firstDateStr = null;
        }

        String pubDatePreset = finalDate != null ? ERR_PUB_DATE_PRESET_WITH_TBD : null;

        String citDateSrc = getValue(cdNumber, pub, version, doc, WML3G_CITATION_ONLINE_ISSUE_PATH, mandatory, true);
        Date citDate = parseDate(citDateSrc, cdNumber, pub, version, WML3G_CITATION_ONLINE_ISSUE_PATH, mandatory);
        if (citDate == null) {
            citDateSrc = null;
        }

        int selfCitationIssueNumber = mandatory ? buildIssue(cdNumber, pub, version, doc, true,
            WML3G_SELF_CITATION_VOLUME_PATH, WML3G_SELF_CITATION_ISSUE_PATH)
                : cca ? checkIssue(cdNumber, pub, version, doc,
                        WML3G_SELF_CITATION_VOLUME_PATH, WML3G_SELF_CITATION_ISSUE_PATH)
                    : buildIssue(cdNumber, pub, version, doc, pubDatePreset,
                            WML3G_SELF_CITATION_VOLUME_PATH, WML3G_SELF_CITATION_ISSUE_PATH);

        ret.setPubDates(versionIssueNumber, finalDate, finalDateStr, citDate != null ? selfCitationIssueNumber : 0,
                citDate, citDateSrc, firstDateStr);
        ret.setFirstIssues(0, 0, selfCitationIssueNumber);
    }


    /*public static String extractFirstOnlineDate(BaseType bt, String cdNumber, int pubNumber, String recordPath) {
        InputStream is = null;
        String finalDateStr = null;
        try {
            ContentChecker contentChecker = new ContentChecker();
            is = RepositoryFactory.getRepository().getFile(recordPath);
            Document doc = contentChecker.getDocumentLoader().load(is);
            finalDateStr = getValue(cdNumber, pubNumber, ArchieEntry.NONE_VERSION, doc,
                    WML3G_FINAL_ONLINE_FORM_PATH, true, true);
        } catch (Exception ce) {
            LOG.error(ce.getMessage());

        } finally {
            IOUtils.closeQuietly(is);
        }
        return finalDateStr;
    }*/
    private int checkIssue(String cdNumber, int pub, String version, Document doc, String volumePath, String issuePath)
            throws Exception {
        String value = JDOMHelper.getElementValue(doc, volumePath);
        if (value != null) {
            throwValueIsNotSupportedException(new ArchieEntry(cdNumber, pub, version), volumePath);
        }
        value = JDOMHelper.getElementValue(doc, issuePath);
        if (value != null) {
            throwValueIsNotSupportedException(new ArchieEntry(cdNumber, pub, version), issuePath);
        }
        return 0;
    }

    protected static int buildIssue(String cdNumber, int pub, String version, Document doc, boolean contentMandatory,
                                    String volumePath, String issuePath) throws Exception {
        int year = contentMandatory ? getMandatoryIntValue(cdNumber, pub, version, doc, volumePath)
                : getIntValue(cdNumber, pub, version, doc, null, volumePath);
        int issue = contentMandatory ? getMandatoryIntValue(cdNumber, pub, version, doc, issuePath)
                : getIntValue(cdNumber, pub, version, doc, null, issuePath);
        return year != 0 && issue != 0 ? CmsUtils.getIssueNumber(year, issue) : 0;
    }

    protected static int buildIssue(String cdNumber, int pub, String version, Document doc, String pubDateErrTemplate,
                                    String volumePath, String issuePath) throws Exception {
        int year = getIntValue(cdNumber, pub, version, doc, pubDateErrTemplate, volumePath);
        int issue = getIntValue(cdNumber, pub, version, doc, pubDateErrTemplate, issuePath);
        return year != 0 && issue != 0 ? CmsUtils.getIssueNumber(year, issue) : 0;
    }

    protected final ContentAccessType getAccessType(String cdNumber, int pub, Document doc) throws Exception {
        String licenceStr = JDOMHelper.getAttributeValue(doc, getLicencePath());
        Res<ContentAccessType> accessTypeRes = ContentAccessType.findByMetadata(licenceStr);
        if (!Res.valid(accessTypeRes)) {
            LOG.warn(String.format("%s - licence type %s was not specified",
                    RevmanMetadataHelper.buildPubName(cdNumber, pub), licenceStr));
            accessTypeRes = ContentAccessType.find(ContentAccessType.NOT_SPECIFIED);
        }
        return accessTypeRes.get();
    }

    protected final String getType(ArchieEntry ae, Document doc) throws Exception {
        return getSetting(ae, getMandatoryValue(ae.getName(), ae.getPubNumber(), ae.getCochraneVersion(), doc,
                getTypePath()), ARTICLE_TYPES.get());
    }

    protected static int getMandatoryIntValue(String cdNumber, int pub, String version, Document doc, String xpath)
            throws Exception {
        return parseIntValue(getMandatoryValue(cdNumber, pub, version, doc, xpath),
                cdNumber, pub, version, xpath, true, "");
    }

    protected static String getMandatoryValue(String cdNumber, int pub, String version, Document doc, String xpath)
            throws Exception {
        return checkMandatoryValue(JDOMHelper.getElementValue(doc, xpath), cdNumber, pub, version, xpath);
    }

    protected static String getMandatoryValue(Document doc, String xpath, String tag) throws Exception {
        String ret = JDOMHelper.getElementValue(doc, xpath);
        if (ret == null) {
            throw new CmsException(String.format("no '%s' tag found", tag));
        }
        return ret;
    }

    public static Set<String> getMandatoryValues(Document doc, String xpath)  {
        try {
            return JDOMHelper.getAttributeValues(doc, xpath).orElse(Collections.emptySet());
        } catch (JDOMException e) {
            return Collections.emptySet();
        }
    }

    protected static String getMandatoryAttrValue(String cdNumber, int pub, String version, Document doc, String xpath)
            throws Exception {
        return checkMandatoryValue(JDOMHelper.getAttributeValue(doc, xpath), cdNumber, pub, version, xpath);
    }

    protected static int getIntValue(String cdNumber, int pub, String version, Document doc,
                                     String pubDateErrTemplate, String xpath) throws Exception {
        String value = JDOMHelper.getElementValue(doc, xpath);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        boolean pubDatePreset = pubDateErrTemplate != null;
        return parseIntValue(getMandatoryValue(cdNumber, pub, version, doc, xpath), cdNumber, pub, version, xpath,
                pubDatePreset, pubDatePreset ? String.format(pubDateErrTemplate, xpath) : "");
    }

    protected static String getValue(String cdNumber, int pub, String version, Document doc, String xpath,
                                     boolean mandatory, boolean attr) throws CmsException {
        try {
            String ret = mandatory ? (attr ? getMandatoryAttrValue(cdNumber, pub, version, doc, xpath)
                    : getMandatoryValue(cdNumber, pub, version, doc, xpath))
                    : (attr ? JDOMHelper.getAttributeValue(doc, xpath) : JDOMHelper.getElementValue(doc, xpath));
            if (ret != null) {
                ret = ret.trim();
            }
            return ret != null && !ret.isEmpty() ? ret : null;

        } catch (CmsException ce) {
            if (mandatory) {
                throw ce;
            }
        } catch (Throwable tr) {
            throwWrongValueException(new ArchieEntry(cdNumber, pub, version), xpath, tr.getMessage());
        }
        return null;
    }

    protected static Date parseDate(String dateStr, String cdNumber, int pub, String version, String xpath,
                                    boolean mandatory) throws CmsException {
        Date date = null;
        if (dateStr != null) {
            try {
                if (dateStr.contains("T")) {
                    OffsetDateTime ofdt = OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    date = Date.from(ofdt.toInstant());
                } else {
                    LocalDate ld = LocalDate.parse(dateStr, Now.DATE_FORMATTER_LIGHT);
                    date = ld != null ? Now.normalizeDate(ld) : null;
                }
            } catch (Throwable tr) {
                if (mandatory) {
                    throwWrongValueException(new ArchieEntry(cdNumber, pub, version), xpath, tr.getMessage());
                }
                LOG.warn(String.format("%s - %s", xpath, tr.getMessage()));
            }
        }
        return date;
    }

    private static String checkMandatoryValue(String value, String cdNumber, int pub, String version, String xpath)
            throws CmsException {
        if (value == null || value.isEmpty()) {
            throwNoValueException(new ArchieEntry(cdNumber, pub, version), xpath);
        }
        return value;
    }

    private static int parseIntValue(String ret, String cdNumber, int pub, String version, String xpath,
                                     boolean mandatory, String addMsg) throws CmsException {
        try {
            return Integer.parseInt(ret);

        } catch (NumberFormatException nu) {
            String msg = String.format("cannot parse value '%s' to number by: %s - %s%s",
                    ret, xpath, nu.getMessage(), addMsg);
            if (mandatory) {
                throw CmsException.createForContent(new ArchieEntry(cdNumber, pub, version), msg);
            }
            LOG.warn(msg);
            return 0;
        }
    }

    protected static void checkDoi(ArchieEntry ae, String contentDoi) throws CmsException {
        if (!contentDoi.equals(RevmanMetadataHelper.buildDoi(ae.getName(), ae.getPubNumber()))) {
            throw CmsException.createForContent(ae, String.format(
                    "file name parts '%s' doesn't match DOI '%s' in content", ae.getPubName(), contentDoi));
        }
    }

    protected static void checkCCADoi(ArchieEntry ae, String contentDoi, Pattern p, ErrorInfo.Type errType)
            throws CmsException {
        Matcher m = p.matcher(contentDoi);
        if (m.matches()) {
            String ccaId = m.group(1).replace(".", "");
            if (!ccaId.equals(ae.getName())) {
                throw CmsException.create(ae, errType, contentDoi);
            }
        } else {
            throw CmsException.create(ae, ErrorInfo.Type.WRONG_DOI_PATTERN, contentDoi);
        }
    }

    protected static String getSetting(ArchieEntry ae, String value, Settings set) throws CmsException {
        String ret = set.getStrSetting(value);
        if (ret == null) {
            throw CmsException.createForMetadata(ae, String.format("no type mapping found for '%s'", value));
        }
        return ret;
    }

    protected static void throwWrongValueException(ArchieEntry ae, String xpath, String msg) throws CmsException {
        throw CmsException.createForContent(ae, String.format("wrong value by: %s - %s", xpath, msg));
    }

    protected static void throwNoValueException(ArchieEntry ae, String xpath) throws CmsException {
        throw CmsException.createForContent(ae, String.format("no value found by: %s", xpath));
    }

    protected static void throwValueIsNotSupportedException(ArchieEntry ae, String xpath) throws CmsException {
        throw CmsException.createForContent(ae, String.format("any values by: %s are not supported", xpath));
    }

    public CDSRMetaVO extractMetadata(String zeName, InputStream is, int issueNumber, BaseType bt, IRecordCache cache)
            throws Exception {
        Document doc = documentLoader.load(is);
        BaseType.ProductType productType = bt.getProductType();

        String doi = getMandatoryValue(doc, getDoiPath(), getDoiPath());
        String pubName = productType.parsePubName(doi);
        String cdNumber = productType.parseCdNumber(pubName);
        PackageChecker.checkCdNumber(cdNumber, zeName);
        String version = getCochraneVersion(doc);

        CDSRMetaVO meta = new CDSRMetaVO(cdNumber, productType.parsePubNumber(doi), version);
        extractMetadata(meta, doi, doc, null, issueNumber, bt, cache);
        return meta;
    }

    public String extractPublisherId(InputStream is) throws Exception {
        return null;
    }

    public void validate(ArchieEntry meta, IRepository rp) throws Exception {
    }

    public boolean isArticleEntry(ZipEntry zeLast, PackageType packageType) {
        PackageType.Entry zipEntry;
        try {
            zipEntry = PackageChecker.getPackageEntry(zeLast.getName(), packageType);
        } catch (CmsException e) {
            return false;
        }
        return PackageType.EntryType.ARTICLE.equals(zipEntry.getType());
    }

    public ByteArrayInputStream uploadACopy(InputStream inputStream) {
        ByteArrayInputStream byteArrayInputStream = null;
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            IOUtils.copy(inputStream, byteArrayOutputStream);
            byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            LOG.warn("Failed to create the org.jdom.Document for validation process");
        }
        return byteArrayInputStream;
    }

    public Document getArticleDocument(InputStream inputStream) {
        Document document = null;
        try {
            document = loadArticleDocument(inputStream);
        } catch (IOException | JDOMException e) {
            LOG.warn("Could not extract xml file for validation process");
        }
        return document;
    }

    public Document loadArticleDocument(InputStream inputStream) throws IOException, JDOMException {
        return documentLoader.load(inputStream);
    }

    public void validateXmlForImagesAndGraphics(Set<String> entryNames, Document document, PackageType packageType,
                                                CDSRMetaVO meta) throws CmsException {
        compareFilesFromXmlAndZip(entryNames, document, GRAPHIC_PATH, IMAGE, packageType, meta);
    }

    public void validateXmlForSupplementaryMaterials(Set<String> entryNames, Document document,
                                                     PackageType packageType, CDSRMetaVO meta)
            throws CmsException {
        compareFilesFromXmlAndZip(entryNames, document, SUPPLEMENTARY_MATERIALS_PATHS, STATS, packageType, meta);
    }

    public void validateZipEntryForEmptyFiles(Set<ZipEntry> zipEntries, CDSRMetaVO meta)
            throws CmsException {
        EntryValidator.validateZipEntries(zipEntries, meta);
    }

    private void compareFilesFromXmlAndZip(Set<String> entryNames, Document document, String path,
                                           PackageType.EntryType type, PackageType packageType,
                                           CDSRMetaVO meta) throws CmsException {
        Set<String> entryNamesFromXml = getMandatoryValues(document, path);
        entryNamesFromXml = replacePrefixForEditorialIfNeeded(entryNamesFromXml);
        Set<String> entryNamesFromZip = getEntryNamesByType(entryNames, type, packageType);
        if (!entryNamesFromXml.equals(entryNamesFromZip)) {
            EntryValidator.validateCollections(entryNamesFromXml, entryNamesFromZip, meta);
        }
    }

    private Set<String> getEntryNamesByType(Set<String> names, PackageType.EntryType entryType,
                                            PackageType packageType) throws CmsException {
        Set<String> entryNames = new HashSet<>();
        for (String name : names) {
            PackageType.Entry zipEntry = PackageChecker.getPackageEntry(name, packageType);
            if (entryType == zipEntry.getType()) {
                entryNames.add(name);
            }
        }
        return entryNames;
    }

    private Set<String> replacePrefixForEditorialIfNeeded(Set<String> entryNamesFromXml) {
        return entryNamesFromXml.stream()
                .map(e -> e.startsWith(IMAGE_N) ? e.replaceFirst(IMAGE_N, "") : e)
                .collect(Collectors.toSet());
    }

    void extractMetadata(TranslatedAbstractVO tvo, String recordPath, String darName, IRepository rp) throws Exception {
    }

    void checkManifest(String basePath, String manifestPath, IRepository rp) throws Exception {
    }

    public static Element getElement(Document doc, String xpath, boolean mandatory) throws Exception {
        Element ret = JDOMHelper.getElement(doc, xpath);
        if (ret == null && mandatory) {
            throw new CmsException(String.format("no element found by: '%s'", xpath));
        }
        return ret;
    }

    public static boolean saveTmpResults() {
        return SAVE_TMP_RESULTS.get().asBoolean();
    }

    public boolean initMessageTags() {
        if (messageTags == null) {
            messageTags = new HashMap<>();
            return true;
        }
        return false;
    }

    public void addMessageTag(String tagKey, String messageTag) {
        initMessageTags();
        messageTags.put(tagKey, messageTag);
    }

    public String setMessageTagReport(String messageKey) {
        if (messageTags != null) {
            messageTags.put(messageKey, null);
            return CochraneCMSProperties.getProperty(messageKey, messageTags);
        }
        return null;
    }

    public String getMessageTagReport() {
        if (messageTags != null) {
            for (Map.Entry<String, String> entry: messageTags.entrySet()) {
                if (entry.getValue() == null) {
                    return setMessageTagReport(entry.getKey());
                }
            }
        }
        return null;
    }
}
