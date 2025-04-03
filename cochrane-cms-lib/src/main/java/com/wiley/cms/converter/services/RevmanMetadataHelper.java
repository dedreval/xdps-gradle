package com.wiley.cms.converter.services;

import java.io.File;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackageInfo;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.RevmanPackage;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.GroupVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.cochrane.cmanager.res.RevmanTransitions;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.cochrane.utils.IssueDate;
import com.wiley.tes.util.Pair;
import com.wiley.cms.cochrane.utils.xml.JDOMHelper;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.jdom.DocumentLoader;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.Settings;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11.07.12
 */
public class RevmanMetadataHelper {

    public static final String REVIEW_XPATH = "//REVIEW";
    public static final String CD_NUMBER_ATTR = "CD_NUMBER";

    public static final String STAGE_P = "P";
    public static final String STAGE_UP = "PU";
    public static final String STAGE_R = "R";

    public static final String REVIEW_EL = "REVIEW";
    public static final String REVIEW_METADATA_EL = "REVIEW_METADATA";
    public static final String ISSUE_EL = "ISSUE";
    public static final String DATE_EL = "DATE";
    public static final String CITATION_LAST_CHANGED_EL = "CITATION_LAST_CHANGED";
    public static final String VERSION_FIRST_PUBLISHED_EL = "VERSION_FIRST_PUBLISHED";
    public static final String DOI_ATTR = "DOI";
    public static final String STATUS_ATTR = "STATUS";
    public static final String STAGE_ATTR = "STAGE";
    public static final String NEW_CITATION_ATTR = "NEW_CITATION";
    public static final String VERSION_NO_ATTR = "VERSION_NO";

    private static final Logger LOG = Logger.getLogger(RevmanMetadataHelper.class);

    private static final String YEAR_XPATH = "//REVIEW_METADATA/@YEAR";
    private static final String ISSUE_NO_XPATH = "//REVIEW_METADATA/@ISSUE_NO";
    private static final String GROUP_ID_XPATH = "//REVIEW_METADATA/@ENTITY_ID";
    private static final String CD_NUMBER_XPATH = "//REVIEW/@CD_NUMBER";
    private static final String DOI_XPATH = "//REVIEW[@CD_NUMBER='%s']/@DOI";
    private static final String REVMAN_ID_XPATH = "//REVIEW[@CD_NUMBER='%s']/@REVMAN_ID";
    private static final String STATUS_XPATH = "//REVIEW[@CD_NUMBER='%s']/@STATUS";
    private static final String STAGE_XPATH = "//REVIEW[@CD_NUMBER='%s']/@STAGE";
    private static final String COMMENTED_XPATH = "//REVIEW[@CD_NUMBER='%s']/@COMMENTED";
    private static final String NEW_CITATION_XPATH = "//REVIEW[@CD_NUMBER='%s']/@NEW_CITATION";
    private static final String CITATION_YEAR_XPATH = "//REVIEW[@CD_NUMBER='%s']/CITATION_LAST_CHANGED/DATE/@YEAR";
    private static final String CITATION_MONTH_XPATH =
            "//REVIEW[@CD_NUMBER='%s']/CITATION_LAST_CHANGED/DATE/@MONTH";
    private static final String CITATION_DAY_XPATH =
            "//REVIEW[@CD_NUMBER='%s']/CITATION_LAST_CHANGED/DATE/@DAY";
    private static final String GROUP_CODE_XPATH = "/REVIEW_METADATA/@ENTITY_ID";
    private static final String GROUP_NAME_XPATH = "/REVIEW_METADATA/@ENTITY";
    private static final String TITLE_EL = "TITLE";
    private static final String SUBTITLE_EL = "SUBTITLE";
    private static final String RELATIONSHIPS_EL = "RELATIONSHIPS";
    private static final String COMMENTED_ATTR = "COMMENTED";
    private static final String NIH_FUNDED_ATTR = "NIH_FUNDED";
    private static final String APC_WAIVER_ATTR = "APC_WAIVER";
    private static final String LICENCE_TYPE_ATTR = "LICENCE_TYPE";
    private static final String REVMAN_ID_ATTR = "REVMAN_ID";
    private static final String TYPE_ATTR = "TYPE";
    private static final String SUBTYPE_ATTR = "SUBTYPE";
    private static final String ENTITY_ATTR = "ENTITY";
    private static final String ENTITY_ID_ATTR = "ENTITY_ID";

    private static final String REVMAN_PARAM_UNIT_ONLINE_DAY = "unitOnlineDate_Day";
    private static final String REVMAN_PARAM_UNIT_ONLINE_MONTH = "unitOnlineDate_Month";
    private static final String REVMAN_PARAM_UNIT_ONLINE_YEAR = "unitOnlineDate_Year";
    private static final String REVMAN_PARAM_UNIT_HISTORY_TITLE = "unitHistoryInfo_title";

    private static final Res<RevmanTransitions> STATUS_TRANSITIONS = RevmanTransitions.get();
    private static final Res<Settings> RM_TYPES = CmsResourceInitializer.getRevmanTypesMapping();

    final DocumentLoader dl;
    final IResultsStorage rs;
    private IRecordManager rm = null;

    public RevmanMetadataHelper() {
        this(null, new DocumentLoader());
    }

    public RevmanMetadataHelper(IRecordManager rm, IResultsStorage rs) {
        this(rs, new DocumentLoader());
        this.rm = rm;
    }

    public RevmanMetadataHelper(IResultsStorage rs) {
        this(rs, new DocumentLoader());
    }

    public RevmanMetadataHelper(IResultsStorage rs, DocumentLoader dl) {
        this.rs = rs;
        this.dl = dl;
    }

    static String getUnitName(Document xmlDoc) throws Exception {
        Element name = (Element) XPath.selectSingleNode(xmlDoc, "//component/header/unitMeta/unitInfo/fileId");
        if (name == null) {
            throw new Exception("it doesn't contain unitInfo");
        }
        return name.getTextTrim();
    }

    static boolean containsMappedType(String source) {
        return RM_TYPES.get().getStrSetting(source) != null;
    }

    public static String getMappedType(String source, boolean subtype) {
        String ret = RM_TYPES.get().getStrSetting(source);
        if (ret == null) {
            ret = subtype ? "" : RM_TYPES.get().getStrSetting("");
        }
        return ret;
    }

    private static String getInvertMappedType(String source) {
        String ret = RM_TYPES.get().getStrSetting(source);
        if (ret == null) {
            ret = RM_TYPES.get().getStrSetting("");
        } else {
            ret = RM_TYPES.get().getStrSetting(ret); // UMBRELLA: U -> REVMAN51UMB -> UMBRELLA
        }
        return ret;
    }

    public static void prepareError(String srcPath, ErrorInfo.Type type, String errMsg, List<ErrorInfo> err) {
        String errDetails = String.format("%s [%s\"]:\n%s", type, srcPath, errMsg);
        ArchieEntry re = new ArchieEntry(RepositoryUtils.getRecordNameByPath(srcPath).split(
                Constants.NAME_SPLITTER)[0]);
        re.setPath(srcPath);
        err.add(new ErrorInfo<>(re, type, errDetails, errMsg));
    }

    static void prepareLastError(String path, List<ErrorInfo> err) {
        if (err.isEmpty()) {
            return;
        }
        ErrorInfo ei = err.get(err.size() - 1);
        Object entity = ei.getErrorEntity();
        ArchieEntry rel = (entity instanceof ArchieEntry) ? (ArchieEntry) entity : null;
        if (rel != null) {
            rel.setPath(path);
        }
    }

    String getReviewType(String xml) throws Exception {
        Document sourceXmlDoc = dl.load(xml);
        String xpath = "//COCHRANE_REVIEW/@TYPE";
        Attribute el = (Attribute) XPath.selectSingleNode(sourceXmlDoc, xpath);
        return el.getValue();
    }

    /** for PDF service */
    String[] parseMetadata(String metadataXml) throws Exception {

        Document metadataXmlDoc = dl.load(metadataXml);

        String groupId = JDOMHelper.getAttributeValue(metadataXmlDoc, GROUP_ID_XPATH);
        String groupName = JDOMHelper.getAttributeValue(metadataXmlDoc, GROUP_NAME_XPATH);
        String cdNumber = JDOMHelper.getAttributeValue(metadataXmlDoc, CD_NUMBER_XPATH);
        int pub = parsePubNumber(parseDoi(cdNumber, metadataXmlDoc));
        int status = RecordMetadataEntity.RevmanStatus.getStatus(JDOMHelper.getAttributeValue(
                metadataXmlDoc, String.format(STATUS_XPATH, cdNumber)));

        CDSRMetaVO meta = new CDSRMetaVO(cdNumber, pub, groupId, JDOMHelper.getAttributeValue(
                metadataXmlDoc, String.format(STAGE_XPATH, cdNumber), null), status, ArchieEntry.NONE_VERSION);

        meta.setGroupTitle(groupName);

        int year = Integer.parseInt(JDOMHelper.getAttributeValue(metadataXmlDoc, YEAR_XPATH));
        int number = Integer.parseInt(JDOMHelper.getAttributeValue(metadataXmlDoc, ISSUE_NO_XPATH));

        int citYear = Integer.parseInt(JDOMHelper.getAttributeValue(metadataXmlDoc,
                String.format(CITATION_YEAR_XPATH, cdNumber)));
        int citNumber = Integer.parseInt(JDOMHelper.getAttributeValue(metadataXmlDoc,
                String.format(CITATION_MONTH_XPATH, cdNumber)));
        int citDay = Integer.parseInt(JDOMHelper.getAttributeValue(metadataXmlDoc,
                String.format(CITATION_DAY_XPATH, cdNumber)));

        meta.setIssue(CmsUtils.getIssueNumber(year, number), CmsUtils.getDate(year, number, 1));
        meta.setPubDates(meta.getIssue(), meta.getDate(),
                citYear == year && citNumber == number ? meta.getIssue() : CmsUtils.getIssueNumber(year, number),
                CmsUtils.getDate(citYear, citNumber, citDay), 0, RecordMetadataEntity.getRevmanBoolean(
                        JDOMHelper.getAttributeValue(metadataXmlDoc, String.format(NEW_CITATION_XPATH, cdNumber))));

        meta.setRevmanId(JDOMHelper.getAttributeValue(metadataXmlDoc, String.format(REVMAN_ID_XPATH, cdNumber)));

        meta.setFlags(RecordMetadataEntity.getRevmanBoolean(
                JDOMHelper.getAttributeValue(metadataXmlDoc, String.format(COMMENTED_XPATH, cdNumber))), false, false);
        if (!meta.isFirstPub()) {
            meta.setHistory(rs.findPreviousMetadata(meta.getIssue(), meta.getCdNumber(), pub - 1));
        }
        return parseMetadata(meta);
    }

    String getCdNumberValue(String[] params) {
        return params[1];
    }

    private String[] parseMetadata(ICDSRMeta meta) throws Exception {

        String id = meta.getCdNumber();
        List<String> ret = new ArrayList<>();

        ret.add("id");
        ret.add(id);

        ret.add("idSuffix");
        ret.add(meta.isFirstPub() ? "" : String.valueOf(meta.getPubNumber()));

        ret.add("g_stage");
        ret.add(meta.getStage());

        addIssueParams(meta, ret);

        ret.add("status");
        ret.add(RecordMetadataEntity.RevmanStatus.getStatusName(meta.getStatus()));

        ret.add("revmanId");
        ret.add(meta.getRevmanId());

        ret.add("commented");
        ret.add(meta.getCommentedAsString());

        addCitationParams(meta, ret);
        addGroupParams(meta, ret);
        addOpenAccessParams(meta, ret);

        return ret.toArray(new String[ret.size()]);
    }

    public ICDSRMeta createMetadata4Upload(String cdNumber, Element el, Document doc,
                                    Map<String, Pair<Element, String[]>> metadata, int issueNumber) throws Exception {
        CDSRMetaVO ret = createMetadata(cdNumber, issueNumber, el, doc);
        Element content = JDOMHelper.copyElementWithoutChildren(doc.getRootElement());
        content.addContent(el.detach());
        if (!ret.isFirstPub()) {
            ret.setHistory(rs.findPreviousMetadata(issueNumber, cdNumber, ret.getPubNumber() - 1));
        }
        String[] params = parseMetadata(ret);
        metadata.put(cdNumber, new Pair<>(content, params));

        return ret;
    }

    /* for upload or export **/
    private CDSRMetaVO createMetadata(String cdNumber, int issueNumber, Element el, Document doc) throws Exception {

        String version = getVersion(el);
        int pub = parsePubNumber(getDoi(cdNumber, version, el));
        String groupSid = JDOMHelper.getAttributeValue(doc.getRootElement(), ENTITY_ID_ATTR);
        String groupTitle = doc.getRootElement().getAttributeValue(ENTITY_ATTR);
        if (groupSid.equalsIgnoreCase("STD")) {
            groupSid = "STI"; // for compatibility with old metadata
        }

        CDSRMetaVO ret = new CDSRMetaVO(cdNumber, pub, groupSid, el.getAttributeValue(STAGE_ATTR),
                RecordMetadataEntity.RevmanStatus.getStatus(JDOMHelper.getAttributeValue(el, STATUS_ATTR)), version);
        ret.setRevmanId(JDOMHelper.getAttributeValue(el, REVMAN_ID_ATTR));
        ret.setGroupTitle(groupTitle);

        String st = el.getAttributeValue(SUBTYPE_ATTR);
        if (st != null) {
            st = getMappedType(st, true);
        }
        ret.setType(el.getAttributeValue(TYPE_ATTR).substring(0, 1), st);

        int firstPublishIssue = JDOMHelper.getRevmanIssueAsNumber(JDOMHelper.getElement(
                el, ".//VERSION_FIRST_PUBLISHED/ISSUE"));
        Date firstPublishDate = JDOMHelper.getRevmanDate(JDOMHelper.getElement(
                el, ".//VERSION_FIRST_PUBLISHED/DATE"));

        if (issueNumber != firstPublishIssue) {
            if (CochraneCMSPropertyNames.getUploadCDSRIssue() == Constants.UNDEF) {
                LOG.warn(String.format(
                    "%s: version-first-published issue is %d but current is %d -> last issue will be used",
                        cdNumber, firstPublishIssue, issueNumber));
                ZonedDateTime zdt = CmsUtils.getCochraneDownloaderDateTime();
                IssueDate iDate = new IssueDate(zdt);
                firstPublishIssue = CmsUtils.getIssueNumber(iDate.issueYear, iDate.issueMonth);
                firstPublishDate = Date.from(zdt.toInstant());
            }
        }
        ret.setIssue(issueNumber, new Date());
        ret.setFlags(RecordMetadataEntity.getRevmanBoolean(el.getAttributeValue(COMMENTED_ATTR)),
                RecordMetadataEntity.getRevmanBoolean(el.getAttributeValue(NIH_FUNDED_ATTR)),
                RecordMetadataEntity.getRevmanBoolean(el.getAttributeValue(APC_WAIVER_ATTR)));

        ret.setPubDates(firstPublishIssue, firstPublishDate,
            JDOMHelper.getRevmanIssueAsNumber(JDOMHelper.getElement(el, ".//CITATION_LAST_CHANGED/ISSUE")),
            JDOMHelper.getRevmanDate(JDOMHelper.getElement(el, ".//CITATION_LAST_CHANGED/DATE")),
                0, RecordMetadataEntity.getRevmanBoolean(el.getAttributeValue(NEW_CITATION_ATTR)));

        ret.setAccessTypeMetadata(el.getAttributeValue(LICENCE_TYPE_ATTR));

        ret.setTitle(JDOMHelper.getText(el.getChild(TITLE_EL)));

        return ret;
    }

    private String getDoi(String id, String version, Element element) throws Exception {
        String doi = JDOMHelper.getAttributeValue(element, DOI_ATTR);
        if (!doi.startsWith(Constants.DOI_PREFIX_CDSR)) {
            throw new CmsException(new ErrorInfo<>(new ArchieEntry(id, Constants.FIRST_PUB, version),
                    ErrorInfo.Type.METADATA, "Invalid DOI: " + doi));
        }
        return doi;
    }

    private String getVersion(Element element) {
        return element.getAttributeValue(VERSION_NO_ATTR);
    }

    public List<ErrorInfo> checkRevmanElements(int issueNumber, DeliveryPackageInfo dfInfo, String groupName,
        InputStream metadata, IRecordCache cache, Map<String, Integer[]> reviewsCdPubNumber, boolean isAut) {
        List<ErrorInfo> ret = new ArrayList<>();
        try {
            checkRevmanElements(issueNumber, dfInfo, ret, metadata, cache, reviewsCdPubNumber, isAut);

        } catch (Throwable t) {
            LOG.error(t.getMessage());
            for (ErrorInfo ei : ret) {

                Object entity = ei.getErrorEntity();
                ArchieEntry rel = (entity instanceof ArchieEntry) ? (ArchieEntry) entity : null;
                String recName = rel != null ? rel.getName() : entity.toString();
                cache.removeRecord(recName, CmsUtils.isScheduledIssueNumber(issueNumber));
            }
            ret.add(new ErrorInfo<>(groupName, ErrorInfo.Type.CRG_GROUP, t.getMessage(), "unexpected error"));

        } finally {
            IOUtils.closeQuietly(metadata);
        }
        return ret;
    }

    private void checkRevmanElements(int issueNumber, DeliveryPackageInfo dfInfo, List<ErrorInfo> ret,
        InputStream metadata, IRecordCache cache, Map<String, Integer[]> reviewsCdPubNumber, boolean isAut)
            throws Exception {
        Document doc = dl.load(metadata);
        boolean statusCheck = CochraneCMSPropertyNames.isRevmanStatusValidation()
                && !RevmanPackage.isRepeat(dfInfo.getDfName());

        for (Object obj : XPath.selectNodes(doc, REVIEW_XPATH)) {
            Element el = ((Element) obj);
            String cdNumber = el.getAttributeValue(CD_NUMBER_ATTR);
            try {
                synchronized (IRecordCache.RECORD_LOCKER) {
                    dfInfo.addStatAll();
                    if (cache.containsRecord(cdNumber)) {
                        RecordHelper.handleErrorMessage(new ErrorInfo<>(new ArchieEntry(cdNumber,
                            reviewsCdPubNumber.remove(cdNumber)[0]), ErrorInfo.Type.SYSTEM,
                                MessageSender.MSG_RECORD_PROCESSED), ret);
                        dfInfo.addStatBeingProcessed();
                        continue;
                    }
                    CDSRMetaVO meta = createMetadata(cdNumber, issueNumber, el, doc);
                    if (isAut && !checkVersion(ret, cdNumber, meta)) {
                        continue;

                    } else if (meta.isDeleted()) {
                        // add info about deleted records
                        Integer[] pubAndId = {meta.getPubNumber(), null};
                        reviewsCdPubNumber.put(meta.getName(), pubAndId);

                    } else if (!reviewsCdPubNumber.containsKey(cdNumber)) {
                        throw new CmsException(ErrorInfo.missingArticle(meta));

                    }  else if (!checkPubNumber(ret, meta, reviewsCdPubNumber.get(cdNumber))) {
                        continue;
                    }
                    String errSpd = JatsPackage.checkSPD(cdNumber, meta.getPubName(), meta.getPubNumber(), cache);
                    if (errSpd != null) {
                        RecordHelper.handleErrorMessage(
                                new ErrorInfo<>(meta, ErrorInfo.Type.VALIDATION_SPD, errSpd), ret);
                        continue;
                    }

                    Integer[] pubAndId = reviewsCdPubNumber.get(cdNumber);
                    GroupVO groupVO = GroupVO.getGroup(cdNumber, meta.getPubNumber(), meta.getCochraneVersion(),
                            meta.getGroupSid(), cache);
                    ret.add(new ErrorInfo<>(meta, false));
                    cache.addRecord(cdNumber, false);
                    if (rm == null) {
                        continue;
                    }
                    if (statusCheck)  {
                        checkStatus(meta, rs);
                    }
                    meta.setHistoryNumber(RecordEntity.VERSION_SHADOW);
                    pubAndId[1] = rm.createRecord(BaseType.getCDSR().get(), meta, groupVO, dfInfo.getDfId(),
                            false, isAut, 0);
                }

            } catch (CmsException | CmsJTException ce) {
                ErrorInfo ei = ce.hasErrorInfo() ? ce.getErrorInfo() : new ErrorInfo<>(new ArchieEntry(cdNumber),
                        ce.getMessage());
                RecordHelper.handleErrorMessage(ei, ret);

            } catch (Exception e) {
                ErrorInfo ei = new ErrorInfo<>(new ArchieEntry(cdNumber), e.getMessage());
                RecordHelper.handleErrorMessage(ei, ret);
            }
        }
    }

    private static boolean checkVersion(List<ErrorInfo> ret, String id, ArchieEntry re) {
        if (re.hasNullVersion()) {
            String msg = "metadata contains no version";
            RecordHelper.handleErrorMessage(new ErrorInfo<>(re, ErrorInfo.Type.CONTENT, msg), ret);
            LOG.warn(String.format("%s: %s", id, msg));
            return false;
        }
        return true;
    }

    private boolean checkPubNumber(List<ErrorInfo> ret, ArchieEntry ae, Integer[] ids) {
        int pubNumberFile = ids[0];
        int pubNumberMeta = ae.getPubNumber();

        if (pubNumberFile != pubNumberMeta) {
            String msg = String.format("conflicting pub numbers: the metadata contains %d, but the file name has %d",
                    ae.getPubNumber(), pubNumberFile);
            if (pubNumberFile > Constants.FIRST_PUB) {
                RecordHelper.handleErrorMessage(new ErrorInfo<>(ae, ErrorInfo.Type.CONTENT, msg), ret);
                return false;
            }
            ids[0] = pubNumberMeta;
            LOG.warn(msg);
        }
        return true;
    }

    public static String parseDoi(String id, Document metadataXmlDoc) throws JDOMException {
        return StringUtils.substringAfter(JDOMHelper.getAttributeValue(metadataXmlDoc,
                String.format(DOI_XPATH, id)), Constants.DOI_PREFIX_CDSR);
    }

    public static int parsePubNumber(String publisherIdOrDoi) {
        return publisherIdOrDoi.contains(Constants.PUB_PREFIX) ? Integer.parseInt(
                StringUtils.substringAfter(publisherIdOrDoi, Constants.PUB_PREFIX)) : Constants.FIRST_PUB;
    }

    public static String parseCdNumber(String pubName) {
        return pubName.contains(Constants.PUB_PREFIX_POINT)
                ? StringUtils.substringBefore(pubName, Constants.PUB_PREFIX_POINT) : pubName;
    }

    public ICDSRMeta getMetadata4Reconversion(String id, Map<String, Pair<Element, String[]>> metadata, IssueVO issue,
                                              Integer version, boolean ta) throws Exception {
        ICDSRMeta meta;
        if (issue == null && version == null) {
            meta = rs.findLatestMetadata(id, true);

        } else if (version != null) {
            meta = findHistoricalMetadata(id, version);

        } else {
            meta = rs.findMetadataToIssue(issue.getFullNumber(), id, !ta);
        }
        if (meta == null) {
            throw new CmsException("Metadata not found for review [" + id + "]"
                    + (issue == null ? "" : " and issue [" + issue.getFullNumber() + "]"));
        }
        Element content = getMetadataElement(meta, issue, version);
        if (metadata != null) {
            String[] params = parseMetadata(meta);
            metadata.put(id, new Pair<>(content.getParentElement(), params));
        }
        return meta;
    }

    public static String addDoiPrefix(String pubName) {
        return Constants.DOI_PREFIX_CDSR + pubName;
    }

    public static String buildDoi(String cdNumber, int pubNumber) {
        return Constants.DOI_PREFIX_CDSR + buildPubName(cdNumber, pubNumber);
    }

    public static String buildPubName(String cdNumber, int pubNumber) {
        return RecordHelper.buildPubName(cdNumber, pubNumber);
    }

    public static String parsePubName(String doi) {
        return doi.substring(Constants.DOI_PREFIX_CDSR.length());
    }

    /** for UI & re-conversion */
    public Element getMetadataElement(ICDSRMeta meta, IssueVO issue, Integer version) {
        String path;
        IssueVO issue1;
        String groupSid = meta.getGroupSid();
        if (version != null) {
            path = FilePathBuilder.getPathToPreviousRevmanSrc(version, groupSid);
            issue1 = new IssueVO(meta.getPublishedIssue(), null);
        } else if (issue != null) {
            path = FilePathBuilder.RM.getPathToRevmanSrc(issue.getId(), groupSid);
            issue1 = issue;
        } else {
            path = FilePathBuilder.getPathToEntireRevmanSrc(groupSid);
            issue1 = new IssueVO(meta.getPublishedIssue(), null);
        }
        return extractMetadataElement(path, meta, createMetadataGroupElement(groupSid, meta.getGroupTitle(), issue1));
    }

    public static Element createMetadataGroupElement(String groupSid, String groupTitle, IssueVO issue) {
        Element reviewMetadata = new Element(REVIEW_METADATA_EL);
        reviewMetadata.setAttribute(ENTITY_ATTR, groupTitle);
        reviewMetadata.setAttribute(ENTITY_ID_ATTR, groupSid);
        reviewMetadata.setAttribute(JDOMHelper.ISSUE_NUMBER, String.valueOf(issue.getNumber()));
        reviewMetadata.setAttribute(JDOMHelper.YEAR, String.valueOf(issue.getYear()));
        return reviewMetadata;
    }

    public Element getMetadataElement4Export(ICDSRMeta mEntity, IssueVO issue) {
        String path = (issue != null && issue.exist()
                ? FilePathBuilder.RM.getPathToRevmanSrc(issue.getId(), mEntity.getGroupSid())
                : FilePathBuilder.getPathToEntireRevmanSrc(mEntity.getGroupSid()));
        return extractMetadataElement(path, mEntity, null);
    }

    public void updateMetadataElement4Export(Element metaXml) {
        Attribute attr = metaXml.getAttribute(VERSION_NO_ATTR);
        if (attr == null) {
            metaXml.setAttribute(VERSION_NO_ATTR, ArchieEntry.NONE_VERSION);
        }
        checkMetadataElement4Export(SUBTITLE_EL, metaXml);
        checkMetadataElement4Export(RELATIONSHIPS_EL, metaXml);
        checkMetadataElement4Export(VERSION_FIRST_PUBLISHED_EL, metaXml);
    }

    private void checkMetadataElement4Export(String elementName, Element metaXml) {
        Element el = metaXml.getChild(elementName);
        if (el == null) {
            metaXml.addContent(new Element(elementName));
        }
    }

    private static Element createMetadataElement(ICDSRMeta meta) {

        Element reviewEl = new Element(REVIEW_EL);
        reviewEl.setAttribute(CD_NUMBER_ATTR, meta.getCdNumber());
        reviewEl.setAttribute(COMMENTED_ATTR, meta.getCommentedAsString());
        reviewEl.setAttribute(DOI_ATTR, buildDoi(meta.getCdNumber(), meta.getPubNumber()));
        reviewEl.setAttribute(NEW_CITATION_ATTR, meta.getNewCitationAsString());
        reviewEl.setAttribute(NIH_FUNDED_ATTR, meta.getNihFundedAsString());
        reviewEl.setAttribute(APC_WAIVER_ATTR, meta.getApcWaiverAsString());
        reviewEl.setAttribute(REVMAN_ID_ATTR, meta.getRevmanId());
        reviewEl.setAttribute(STAGE_ATTR, meta.getStage());
        reviewEl.setAttribute(STATUS_ATTR, RecordMetadataEntity.RevmanStatus.getStatusName(meta.getStatus()));

        if (meta.isGoldOpenAccess()) {
            reviewEl.setAttribute(LICENCE_TYPE_ATTR, meta.getAccessTypeMetadata());
        }
        reviewEl.setAttribute(VERSION_NO_ATTR, meta.getCochraneVersion() != null
                ? meta.getCochraneVersion() : ArchieEntry.NONE_VERSION);

        reviewEl.setAttribute(TYPE_ATTR, getInvertMappedType(meta.getType()));
        String subType = meta.getSubType();
        if (subType != null && subType.length() > 0) {
            reviewEl.setAttribute(SUBTYPE_ATTR, getMappedType(meta.getSubType(), true));
        }

        Element titleEl = new Element(TITLE_EL);
        titleEl.setText(meta.getTitle());
        Element subTitleEl = new Element(SUBTITLE_EL);
        reviewEl.addContent(titleEl);
        reviewEl.addContent(subTitleEl);

        reviewEl.addContent(getElementWithIssueAndDate("ASSESSED_UP_TO_DATE", -1, null));
        reviewEl.addContent(getElementWithIssueAndDate("LAST_SEARCH", -1, null));
        reviewEl.addContent(getElementWithIssueAndDate("EDITED", -1, null));
        reviewEl.addContent(getElementWithIssueAndDate("PROTOCOL_FIRST_PUBLISHED", -1, null));
        reviewEl.addContent(getElementWithIssueAndDate("REVIEW_FIRST_PUBLISHED", -1, null));
        reviewEl.addContent(getElementWithIssueAndDate(VERSION_FIRST_PUBLISHED_EL,
                meta.getPublishedIssue(), meta.getPublishedDate()));
        reviewEl.addContent(getElementWithIssueAndDate(CITATION_LAST_CHANGED_EL,
                meta.getCitationIssue(), meta.getCitationLastChanged()));
        reviewEl.addContent(getElementWithIssueAndDate("LAST_UPDATED_VERSION", -1, null));
        reviewEl.addContent(getElementWithIssueAndDate("NEXT_STAGE_EXPECTED", -1, null));

        reviewEl.addContent(new Element(RELATIONSHIPS_EL));

        return reviewEl;
    }

    public static String getReport(List<ErrorInfo> errors, int procId, int issue, IActivityLogService logService) {

        StringBuilder report = new StringBuilder();
        int baseType = BaseType.getCDSR().get().getDbId();
        for (ErrorInfo err : errors) {
            if (err.isError()) {

                Object obj = err.getErrorEntity();
                ArchieEntry rel = (obj instanceof ArchieEntry) ? (ArchieEntry) obj : null;
                String recName = rel != null ? rel.getName() : obj.toString();
                String errMsg = err.getErrorDetail();

                MessageSender.addMessage(report, recName, errMsg);

                logService.logRecordError(ILogEvent.CONVERSION_REVMAN_FAILED, procId, recName, baseType, issue, errMsg);
            }
        }
        return report.toString();
    }

    public static File findRevmanRecord(String groupPath, String recName) {
        File reviewDir = new File(FilePathCreator.getDirForRevmanReviews(groupPath));
        File[] files = reviewDir.listFiles();
        if (files != null) {
            for (File fl : files) {
                if (fl.isFile() && fl.getName().startsWith(recName)
                        && !fl.getName().contains(Constants.METADATA_SOURCE_SUFFIX)) {
                    return fl;
                }
            }
        }
        return null;
    }

    public ICDSRMeta extractMetadata(String metadataPath, ICDSRMeta meta, IRepository rp) throws Exception {
        Document doc = dl.load(rp.getFile(metadataPath));
        Element parent = createMetadataGroupElement(meta.getGroupSid(), meta.getGroupTitle(), new IssueVO(
                meta.getIssue(), meta.getPublishedDate()));
        Element el = doc.detachRootElement();
        parent.addContent(el);
        doc.setRootElement(parent);
        return createMetadata(meta.getCdNumber(), meta.getIssue(), el, doc);
    }

    private Element extractMetadataElement(String path, ICDSRMeta meta, Element parent) {
        String metaPath = path + FilePathBuilder.buildMetadataRecordName(meta.getCdNumber());
        IRepository rps = RepositoryFactory.getRepository();
        Element ret = null;
        try {
            if (rps.isFileExists(metaPath)) {
                Document doc = dl.load(rps.getFile(metaPath));
                ret = doc.detachRootElement();
                meta.setRevmanId(JDOMHelper.getAttributeValue(ret, REVMAN_ID_ATTR));
                meta.setTitle(JDOMHelper.getText(ret.getChild(TITLE_EL)));
            } else {
                LOG.warn(String.format("no metadata found in %s", metaPath));
                ret = createMetadataElement(meta);
                updateMetadataElement4Export(ret);
            }
            if (parent != null) {
                parent.addContent(ret);
            }
        } catch (Exception e) {
            LOG.error(e);
        }
        return ret;
    }

    private static Element getElementWithIssueAndDate(String name, int issue, Date date) {
        Element element = new Element(name);
        if (issue > 0) {
            element.addContent(JDOMHelper.getRevmanIssueAsElement(ISSUE_EL, issue));
        }
        Element dateEl = JDOMHelper.getRevmanDate(DATE_EL, date);
        if (dateEl != null) {
            element.addContent(dateEl);
        }
        return element;
    }

    private void addIssueParams(ICDSRMeta meta, List<String> ret) throws Exception {

        IssueVO publishIssue = getPublishIssue(meta);

        String year = String.valueOf(publishIssue.getYear());
        ret.add("year");
        ret.add(year);

        String number = String.valueOf(publishIssue.getNumber());
        ret.add("issue");
        ret.add(number);

        ret.add("CLissueSchedOnlineDate_Year");
        ret.add(year);

        ret.add("CLissueSchedOnlineDate_Month");
        ret.add(CmsUtils.getIssueMonth(publishIssue.getPublishDate()));

        ret.add("CLissueSchedOnlineDate_Day");
        ret.add(CmsUtils.getIssueDay(publishIssue.getPublishDate()));

        if (!meta.isFirstPub()) {
            ICDSRMeta historyMeta = meta.getHistory();
            if (historyMeta == null) {
                LOG.debug(String.format("unitHistoryInfo for record %s not found", meta));
                return;
            }

            ret.add("unitHistoryInfo_pub");
            ret.add(String.valueOf(historyMeta.getPubNumber()));

            String title = historyMeta.getTitle();
            if (RecordMetadataEntity.isTitleValid(title))  {
                ret.add(REVMAN_PARAM_UNIT_HISTORY_TITLE);
                ret.add(title);
            } else if (RecordMetadataEntity.isTitleValid(meta.getTitle())) {
                ret.add(REVMAN_PARAM_UNIT_HISTORY_TITLE);
                ret.add(meta.getTitle());
            }

            IssueVO publishDate = getCitationIssue(historyMeta);
            ret.add("unitHistoryInfo_online_Day");
            ret.add(CmsUtils.getIssueDay(publishDate.getPublishDate()));
            ret.add("unitHistoryInfo_online_Month");
            ret.add(CmsUtils.getIssueMonth(publishDate.getPublishDate()));
            ret.add("unitHistoryInfo_online_Year");
            ret.add("" + publishDate.getYear());
        }
    }

    private IssueVO getPublishIssue(ICDSRMeta meta) {
        return new IssueVO(meta.getPublishedIssue(), meta.getPublishedDate());
    }

    private IssueVO getCitationIssue(ICDSRMeta meta) throws CmsException {
        Date date = meta.getCitationLastChanged();
        int number = meta.getCitationIssue();

        if (date == null || number == 0) {
            throw new CmsException("citation issue or date is null!");
        }
        return new IssueVO(number, date);
    }

    private void addOpenAccessParams(ICDSRMeta meta, List<String> ret) {
        if (meta.isGoldOpenAccess()) {
            ret.add("accessType");
            ret.add("open");
            ret.add("legalStatement");
            ret.add(meta.getAccessType());
        }
    }

    private void addGroupParams(ICDSRMeta meta, List<String> ret) {
        ret.add("groupId");
        ret.add(meta.getGroupSid());

        ret.add("groupCode");
        ret.add(meta.getGroupSid());

        ret.add("groupName");
        ret.add(meta.getGroupTitle());
    }

    private void addCitationParams(ICDSRMeta meta, List<String> ret)
        throws Exception {

        ret.add("new_citation");
        ret.add(meta.getNewCitationAsString());

        IssueVO publishDate = getCitationIssue(meta);
        ret.add("CLcitationIssue");
        ret.add(String.valueOf(publishDate.getNumber()));

        ret.add("CLcitationYear");
        ret.add(String.valueOf(publishDate.getYear()));

        if (meta.isNewCitation()) {
            return;
        }
        Date date = publishDate.getPublishDate();
        ret.add(REVMAN_PARAM_UNIT_ONLINE_DAY);
        ret.add(CmsUtils.getIssueDay(date));
        ret.add(REVMAN_PARAM_UNIT_ONLINE_MONTH);
        ret.add(CmsUtils.getIssueMonth(date));
        ret.add(REVMAN_PARAM_UNIT_ONLINE_YEAR);
        ret.add(CmsUtils.getIssueYear(date));
    }

    private ICDSRMeta findHistoricalMetadata(String recordName, Integer version) throws Exception {
        PrevVO pvo = CochraneCMSBeans.getVersionManager().getVersion(recordName, version);
        if (pvo == null) {
            throw new Exception(String.format("previous db version %d for %s doesn't exist", version, recordName));
        }
        return rs.findLatestMetadata(recordName, pvo.pub);
    }

    public static void checkStatus(ICDSRMeta meta, IResultsStorage rs) throws CmsException {
        ICDSRMeta prev = rs.findPreviousMetadata(meta.getIssue(), meta.getCdNumber(), meta.getPubNumber());
        boolean ignoreStatus = prev != null && prev.isStageP() && !meta.isStageP();
        if (!ignoreStatus) {
            try {
                STATUS_TRANSITIONS.get().checkTransition(meta, prev);
            } catch (Exception e) {
                throw new CmsException(e, new ErrorInfo<>(new ArchieEntry(meta.getCdNumber(), meta.getPubNumber(),
                            meta.getCochraneVersion()), ErrorInfo.Type.METADATA, e.getMessage()));
            }
        }
        if (prev != null && prev.getPubNumber() > meta.getPubNumber()) {
            LOG.warn(String.format("strange history of %s - previous pub=%s", meta.getPublisherId(),
                    prev.getPubNumber()));
        }
    }

    public static boolean isFirstLatest(ICDSRMeta meta1, ICDSRMeta meta2) {
        if (meta1.getPubNumber() > meta2.getPubNumber()) {
            return true;
        }
        int issue1 = meta1.getIssue();
        int issue2 = meta2.getIssue();
        return meta1.getPubNumber() == meta2.getPubNumber()
            && (issue1 > issue2 || (issue1 == issue2 && meta1.getId() > meta2.getId()));
    }
}