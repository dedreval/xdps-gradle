package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.Iterator;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import org.apache.commons.io.IOUtils;
import org.jdom.Document;
import org.jdom.Namespace;
import org.jdom.transform.JDOMSource;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.xml.JDOMHelper;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.TransformerObjectPool;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.Settings;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/5/2019
 */
public class JatsHelper extends ContentHelper {
    public static final Namespace XLINK_NS = Namespace.getNamespace("http://www.w3.org/1999/xlink");
    public static final String META_PATH = "//article-meta";
    public static final String FLOATS_GROUP = "//floats-group";
    public static final String EXT_LINK_PATH = META_PATH + "/ext-link[@ext-link-type='revman-statistics']";

    private static final Logger LOG = Logger.getLogger(JatsHelper.class);

    private static final String NOTES_PATH = "//notes";
    private static final String DOI_PATH = META_PATH
            + "/article-id[@pub-id-type='doi']";
    private static final String PUBLISHER_ID_PATH = META_PATH
            + "/article-id[@pub-id-type='publisher-id']";
    private static final String VERSION_PATH = META_PATH
            + "/article-id[@specific-use='cochrane-version-number']";
    private static final String TA_VERSION_PATH = META_PATH
            + "/article-id[@specific-use='cochrane-translation-version-number']";
    private static final String SID = META_PATH
            + "/article-id[@specific-use='cochrane-review-id']";
    private static final String TA_SID = META_PATH
            + "/article-id[@specific-use='cochrane-review-translation-id']";
    private static final String TITLE_PATH = META_PATH
            + "/title-group/article-title";
    private static final String TA_EN_TITLE_PATH = META_PATH
            + "/title-group/trans-title-group/trans-title[@xml:lang='en']";
    private static final String GROUP_SID_PATH = META_PATH
            + "/contrib-group/contrib[@contrib-type='editor']/contrib-id[@contrib-id-type='cl-review-group-code']";
    private static final String GROUP_TITLE_PATH = META_PATH
            + "/contrib-group/contrib[@contrib-type='editor']/collab[@collab-type='editors']";
    private static final String LICENSE_PATH = META_PATH
            + "/permissions/license/@license-type";
    //private static final String LICENSE_PATH2 = META_PATH
    //        + "/permissions/license/license-p";

    private static final String ISSUE_PATH = META_PATH + "/issue";
    private static final String VOLUME_PATH = META_PATH + "/volume";
    private static final String PUB_DATE_PATH = META_PATH + "/pub-date[@pub-type='epub']";
    private static final String PUB_DATE_PATH_NOT_AVAILABLE = META_PATH + "/pub-date-not-available";

    private static final String VERSION_FIRST_PUBLISHED_DAY_PATH = PUB_DATE_PATH + "/day";
    private static final String VERSION_FIRST_PUBLISHED_MONTH_PATH = PUB_DATE_PATH + "/month";
    private static final String VERSION_FIRST_PUBLISHED_YEAR_PATH = PUB_DATE_PATH + "/year";

    private static final String CITATION_LAST_CHANGED_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-date-citation-last-changed']/meta-value";
    private static final String CITATION_LAST_CHANGED_ISSUE_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-issue-citation-last-changed']/meta-value";
    private static final String CITATION_LAST_CHANGED_YEAR_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-volume-citation-last-changed']/meta-value";
    private static final String REVIEW_FIRST_PUBLISHED_ISSUE_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-issue-review-first-published']/meta-value";
    private static final String REVIEW_FIRST_PUBLISHED_YEAR_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-volume-review-first-published']/meta-value";
    private static final String PROTOCOL_FIRST_PUBLISHED_ISSUE_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-issue-protocol-first-published']/meta-value";
    private static final String PROTOCOL_FIRST_PUBLISHED_YEAR_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-volume-protocol-first-published']/meta-value";
    private static final String SELF_CITATION_ISSUE_PATH = NOTES_PATH
            + "/sec/p[@content-type='self-citation']/mixed-citation[@publication-type='self-citation']/issue";
    private static final String SELF_CITATION_YEAR_PATH = NOTES_PATH
            + "/sec/p[@content-type='self-citation']/mixed-citation[@publication-type='self-citation']/volume";
    private static final String ARIES_SELF_CITATION_ISSUE_PATH = NOTES_PATH
          + "/sec/ref-list[@specific-use='self-citation']/ref/mixed-citation[@publication-type='self-citation']/issue";
    private static final String ARIES_SELF_CITATION_YEAR_PATH = NOTES_PATH
          + "/sec/ref-list[@specific-use='self-citation']/ref/mixed-citation[@publication-type='self-citation']/volume";

    private static final String STAGE_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='review-stage']/meta-value";
    private static final String STATUS_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='publication-status']/meta-value";
    private static final String TYPE_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='review-type']/meta-value";
    private static final String COMMENTED_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='commented']/meta-value";

    private static final String CL_TYPE_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-article-type']/meta-value";
    private static final String CL_STATUS_PATH = META_PATH
            + "/custom-meta-group/custom-meta[meta-name='cl-status']/meta-value";

    private static final Res<Settings> ARTICLE_STAGES = CmsResourceInitializer.getJatsStagesMapping();

    private static final Res<Property> STRICT_VALIDATION = Property.get(
            "cms.cochrane.conversion.jats.strict-validation", Boolean.TRUE.toString());

    private Transformer transformer;

    public static boolean isStrictValidation() {
        return STRICT_VALIDATION.get().asBoolean();
    }

    @Override
    void checkManifest(String basePath, String manifestPath, IRepository rp) throws Exception {
        StringWriter writer = new StringWriter();
        InputStream is = rp.getFile(manifestPath);
        Result output = new StreamResult(writer);

        Document doc = documentLoader.load(is);
        Transformer tr = transformer();
        tr.setParameter("basepath", rp.getRealFilePath(basePath));
        tr.transform(new JDOMSource(doc), output);
        String ret = writer.toString().trim();
        if (!ret.isEmpty()) {
            throw new CmsException(String.format("%s contains files that were not unpacked: %s", manifestPath, ret));
        }
    }

    @Override
    public void validate(ArchieEntry meta, IRepository rp) throws Exception {
        String errors = CochraneCMSBeans.getConverter().validate(
                InputUtils.readStreamToString(rp.getFile(meta.getPath())), IConverterAdapter.JATS_XSD);
        if (errors != null && !errors.trim().isEmpty()) {
            throw new CmsException(new ErrorInfo<>(meta, ErrorInfo.Type.CONTENT, errors));
        }
    }

    @Override
    protected String getDoiPath() {
        return DOI_PATH;
    }

    @Override
    protected String getLicencePath() {
        return LICENSE_PATH;
    }

    @Override
    protected String getTypePath() {
        return TYPE_PATH;
    }

    @Override
    protected void extractMetadata(CDSRMetaVO meta, String doi, Document doc, String recordPath, int issueNumber,
                                   BaseType bt, IRecordCache cache) throws Exception {
        String cdNumber = meta.getCdNumber();
        int pub = meta.getPubNumber();
        String version = meta.getCochraneVersion();

        meta.setGroupSid(getMandatoryValue(cdNumber, pub, version, doc, GROUP_SID_PATH));
        meta.setStage(getStage(meta, doc));

        String groupTitle = getMandatoryValue(cdNumber, pub, version, doc, GROUP_TITLE_PATH);
        String rmStatus = getMandatoryValue(cdNumber, pub, version, doc, STATUS_PATH);
        String clStatus = getMandatoryValue(cdNumber, pub, version, doc, CL_STATUS_PATH);

        meta.setStatus(RecordMetadataEntity.RevmanStatus.getStatus(rmStatus));

        boolean commented = getCommented(doc);

        checkDoi(meta, doi);

        meta.setGroupTitle(groupTitle);
        meta.setUnitStatusId(getUnitStatus(doi, clStatus, rmStatus, meta, commented, cache));
        meta.setType(getType(meta, doc), null);

        setDates(cdNumber, pub, version, issueNumber, doc, meta, bt);

        meta.setFlags(commented, false, false);

        meta.setAccessTypeMetadata(getAccessType(cdNumber, pub, doc));
        meta.setTitle(getMandatoryValue(cdNumber, pub, version, doc, TITLE_PATH));
        meta.setPath(recordPath);
        meta.setRevmanId(getMandatoryValue(cdNumber, pub, version, doc, SID));
        meta.setJats(true);
    }

    @Override
    protected void setDates(String cdNumber, int pub, String version, int xdpsIssueNumber, Document doc, CDSRMetaVO ret,
                            BaseType bt) throws Exception {
        Date date = new Date();
        ret.setIssue(xdpsIssueNumber, date);

        boolean mandatory = !bt.isActualPublicationDateSupported();

        int versionIssueNumber = buildIssue(cdNumber, pub, version, doc, mandatory, VOLUME_PATH, ISSUE_PATH);
        Date versionDate = getVersionDate(cdNumber, pub, version, doc, mandatory);

        String pubDatePreset = versionDate != null ? ERR_PUB_DATE_PRESET_WITH_TBD : null;

        String citDateSrc = getValue(cdNumber, pub, version, doc, CITATION_LAST_CHANGED_PATH, mandatory, false);
        Date citDate = parseDate(citDateSrc, cdNumber, pub, version, CITATION_LAST_CHANGED_PATH, mandatory);
        if (citDate == null) {
            citDateSrc = null;
        }

        int citIssueNumber = buildIssue(cdNumber, pub, version, doc, mandatory,
                                       CITATION_LAST_CHANGED_YEAR_PATH, CITATION_LAST_CHANGED_ISSUE_PATH);
        int protocolFirstIssueNumber = buildIssue(cdNumber, pub, version, doc, false,
                                       PROTOCOL_FIRST_PUBLISHED_YEAR_PATH, PROTOCOL_FIRST_PUBLISHED_ISSUE_PATH);
        int reviewFirstIssueNumber = buildIssue(cdNumber, pub, version, doc, false,
                                       REVIEW_FIRST_PUBLISHED_YEAR_PATH, REVIEW_FIRST_PUBLISHED_ISSUE_PATH);

        int selfCitationIssueNumber = mandatory ? buildIssue(cdNumber, pub, version, doc, true,
                SELF_CITATION_YEAR_PATH, SELF_CITATION_ISSUE_PATH) : buildIssue(cdNumber,
                    pub, version, doc, pubDatePreset, SELF_CITATION_YEAR_PATH, SELF_CITATION_ISSUE_PATH);

        if (selfCitationIssueNumber == 0) {
            selfCitationIssueNumber = mandatory ? buildIssue(cdNumber, pub, version, doc, true,
                ARIES_SELF_CITATION_YEAR_PATH, ARIES_SELF_CITATION_ISSUE_PATH) : buildIssue(cdNumber,
                    pub, version, doc, pubDatePreset, ARIES_SELF_CITATION_YEAR_PATH, ARIES_SELF_CITATION_ISSUE_PATH);
        }

        ret.setPubDates(versionIssueNumber, versionDate, null, citIssueNumber, citDate, citDateSrc, citDateSrc);
        ret.setFirstIssues(protocolFirstIssueNumber, reviewFirstIssueNumber, selfCitationIssueNumber);
    }

    private static Date getVersionDate(String cdNumber, int pub, String version, Document doc, boolean mandatory)
            throws Exception {
        Date versionDate = null;
        try {
            int versionYear = getMandatoryIntValue(cdNumber, pub, version, doc, VERSION_FIRST_PUBLISHED_YEAR_PATH);
            int versionMonth = getMandatoryIntValue(cdNumber, pub, version, doc, VERSION_FIRST_PUBLISHED_MONTH_PATH);
            int versionDay = getMandatoryIntValue(cdNumber, pub, version, doc, VERSION_FIRST_PUBLISHED_DAY_PATH);
            versionDate = getDate(versionYear, versionMonth, versionDay, cdNumber, pub, version);

        } catch (Exception e)  {
            if (mandatory && JDOMHelper.getElement(doc, PUB_DATE_PATH_NOT_AVAILABLE) == null) {
                throw e;
            }
        }
        return versionDate;
    }

    public String extractMetadata(TranslatedAbstractVO tvo, String recordPath, IRepository rp)  {
        try {
            extractMetadata(tvo, recordPath, null, null, rp);
        } catch (Exception e) {
            return e.getMessage();
        }
        return null;
    }

    @Override
    void extractMetadata(TranslatedAbstractVO tvo, String recordPath, String darName, IRepository rp) throws Exception {
        extractMetadata(tvo, recordPath, darName, null, rp);
    }

    public void extractMetadata(TranslatedAbstractVO tvo, String recordPath, String darName, String[] rmIds,
                                IRepository rp) throws Exception {
        InputStream is = null;
        try {
            is = rp.getFile(recordPath);
            Document doc = documentLoader.load(is);

            String version = getTranslationVersion(doc);
            String sid = getMandatoryValue(doc, TA_SID, "cochrane-review-translation-id");
            tvo.setSid(sid);
            tvo.setCochraneVersion(version);

            String doi = getMandatoryValue(tvo, doc, getDoiPath());
            checkDoi(tvo, doi);
            checkLanguage(tvo, darName, doc);

            tvo.setDoi(doi);

            String englishTitle = getMandatoryValue(tvo, doc, TA_EN_TITLE_PATH);
            tvo.setEnglishTitle(englishTitle);
            String title = JDOMHelper.getElementValue(doc, TITLE_PATH, "");
            tvo.setTitle(title);

            tvo.setPath(recordPath);
            tvo.setJats();
            
            if (rmIds != null) {
                rmIds[0] = getMandatoryValue(tvo, doc, SID);
                rmIds[1] = getCochraneVersion(doc);
            }
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public String extractCochraneVersion(String recordPath, IRepository rp) throws Exception {
        InputStream is = null;
        try {
            is = rp.getFile(recordPath);
            return getCochraneVersion(documentLoader.load(is));

        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @Override
    public String extractPublisherId(InputStream is) throws Exception {
        return getPubName(documentLoader.load(is));
    }

    private Transformer transformer() throws Exception {
        if (transformer == null) {
            transformer = TransformerObjectPool.Factory.instance().getTransformer(
                CochraneCMSPropertyNames.getCochraneResourcesRoot() + "jats/dar_manifest.xsl");
        }
        return transformer;
    }

    public static String isFileExists(String basePath, String path) {
        return new File(basePath + FilePathCreator.SEPARATOR + path).exists() ? null : path;
    }

    private static String getPubName(Document doc) throws Exception {
        return getMandatoryValue(doc, PUBLISHER_ID_PATH, "publisher-id");
    }

    @Override
    protected String getCochraneVersion(Document doc) throws Exception {
        return getMandatoryValue(doc, VERSION_PATH, "cochrane-version-number");
    }

    private static String getTranslationVersion(Document doc) throws Exception {
        return getMandatoryValue(doc, TA_VERSION_PATH, "cochrane-translation-version-number");
    }

    private static String getStage(ArchieEntry ae, Document doc) throws Exception {
        return getSetting(ae, getMandatoryValue(ae.getName(), ae.getPubNumber(), ae.getCochraneVersion(), doc,
                STAGE_PATH), ARTICLE_STAGES.get());
    }

    private static void checkLanguage(TranslatedAbstractVO tvo, String darName, Document doc) throws CmsException {
        try {
            String lang = JDOMHelper.getXmlAttributeValue(doc.getRootElement(), "lang");
            if (tvo.isLanguageNotExist()) {
                tvo.setLanguage(lang);
            }
            ErrorInfo<TranslatedAbstractVO> err = TranslatedAbstractsPackage.validateLanguage(tvo);
            if (err != null) {
                throw new CmsException(err);
            }
            if (darName != null && !tvo.getOriginalLanguage().equals(lang) && tvo.getLanguage().equals(lang)) {
                throw new Exception(
                    String.format("a file name attributes '%s' doesn't match language '%s' in content", darName, lang));
            }
        }  catch (CmsException ce) {
            throw ce;

        } catch (Exception e) {
            throw new CmsException(new ErrorInfo<>(tvo, ErrorInfo.Type.CONTENT, e.getMessage()));
        }
    }

    private static boolean getCommented(Document doc) throws Exception {
        String ret = JDOMHelper.getElementValue(doc, COMMENTED_PATH);
        return ret == null ? false : Boolean.valueOf(ret);
    }

    private static String getMandatoryValue(TranslatedAbstractVO tvo, Document doc, String xpath) throws Exception {
        String ret = JDOMHelper.getElementValue(doc, xpath);
        if (ret == null) {
            throwNoValueException(tvo, xpath);
        }
        return ret;
    }

    private static Date getDate(int year, int month, int day, String cdNumber, int pub, String version)
            throws CmsException {
        try {
            return Now.normalizeDate(year, month, day);
        } catch (Throwable tr) {
            throwWrongValueException(new ArchieEntry(cdNumber, pub, version), PUB_DATE_PATH, tr.getMessage());
        }
        return null;
    }

    private static void checkUnitStatus(String doi, String clStatus, String rmStatus, UnitStatusVO statusVO,
                                        CDSRMetaVO meta, int rmStatusId) {
        if (statusVO.getCdsr4Revman() != rmStatusId) {
            LOG.warn(String.format("%s - unit status '%s'has id [%d], but publication status '%s' has id [%d]",
                    doi, clStatus, statusVO.getCdsr4Revman(), rmStatus, rmStatusId));
            if (rmStatusId == RecordMetadataEntity.RevmanStatus.UNCHANGED.dbKey) {
                meta.setStatus(statusVO.getCdsr4Revman());
            }
        }
    }

    private static Integer getUnitStatus(String doi, String clStatus, String rmStatus, CDSRMetaVO meta,
                                         boolean commented, IRecordCache cache) {
        int rmStatusId = meta.getStatus();
        UnitStatusVO statusVO = cache.getUnitStatus(clStatus, true);
        if (statusVO != null) {
            checkUnitStatus(doi, clStatus, rmStatus, statusVO, meta, rmStatusId);
            return statusVO.getId();
        }
        LOG.warn(String.format("%s - no unit status mapped for '%s'", doi, clStatus));

        Iterator<UnitStatusVO> it = cache.getUnitStatuses(true);
        Integer ret = null;
        while (it.hasNext()) {
            statusVO = it.next();

            if (statusVO.getCdsr4Revman() == rmStatusId) {
                ret = statusVO.getId();
                if (commented == statusVO.isCommented()) {
                    break;
                }
            }
        }
        if (ret == null) {
            //throw new CmsException(String.format("%s - no unit status found for %s", doi, clStatus));
            LOG.warn(String.format("%s - no unit status found for '%s'", doi, clStatus));
            ret = commented ? UnitStatusEntity.UnitStatus.UNCHANGED_COMMENTED : UnitStatusEntity.UnitStatus.UNCHANGED;
        }
        return ret;
    }
}
