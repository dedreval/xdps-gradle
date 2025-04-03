package com.wiley.cms.cochrane.cmanager.translated;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.res.CheckList;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.converter.IConverterAdapter;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.XmlUtils;
import com.wiley.tes.util.res.Res;

/**
 * @author Sergey Trofimov
 */
@Stateless
@Local(ITranslatedAbstractsInserter.class)
public class TranslatedAbstractsInserter implements ITranslatedAbstractsInserter {

    private static final String COMPONENT_OPEN_TAG = "<component";

    @PersistenceContext
    private EntityManager manager;

    @EJB(beanName = "ConverterAdapter")
    private IConverterAdapter converter;

    @EJB
    private IActivityLogService logger;

    public TranslatedAbstractsInserter() {
    }

    @SuppressWarnings("unchecked")
    public void trackRecordsForWhenReadyPublish(Map<String, Pair<Integer, PublishEntity>> recordNames, int clDbId,
                                            int dfId, String exportType, Set<String> skippedNames) {

        PublishDestination dest = PublishProfile.PUB_PROFILE.get().getDestination();

        boolean lastPub = dest.getLastType().contains(exportType);

        List<PublishedAbstractEntity> list = DbUtils.exists(dfId) ? PublishedAbstractEntity.queryAbstractsByDf(dfId,
                recordNames.keySet(), manager).getResultList() : PublishedAbstractEntity.queryAbstractsByDb(
                clDbId, recordNames.keySet(), manager).getResultList();
        Map<String, PublishedAbstractEntity> paMap = new HashMap<>();

        Set<String> sendDois = new HashSet<>();
        if (!list.isEmpty()) {
            generateAbstractsForPublish(recordNames, list, exportType, sendDois, paMap, skippedNames, dest);
        }
        generateAbstractsForPublishFromMetadata(recordNames, lastPub, sendDois, paMap, skippedNames, dest);
    }

    private void generateAbstractsForPublish(Map<String, Pair<Integer, PublishEntity>> recordNames,
        List<PublishedAbstractEntity> list, String exportType, Set<String> sendDois,
        Map<String, PublishedAbstractEntity> paMap, Set<String> skippedNames, PublishDestination dest) {

        for (PublishedAbstractEntity ta: list) {
            if (!PublishedAbstractEntity.isNotifiedOnReceivedSuccess(ta.getNotified())) {
                continue;
            }
            String cdNumber = ta.getRecordName();
            Pair<Integer, PublishEntity> pair = recordNames.get(cdNumber);
            Integer dfId = pair.first;
            if (!dfId.equals(ta.getDeliveryId()) && !dfId.equals(ta.getInitialDeliveryId())) {
                continue;
            }

            if (ta.getSid() == null) {
                paMap.put(cdNumber, ta);
                continue;
            }

            trackPublishedAbstract(ta, RevmanMetadataHelper.buildDoi(cdNumber, ta.getPubNumber()), dfId,
                    pair.second, sendDois, skippedNames, dest);
        }
    }

    //private void logPublishedAbstract(int logEvent, String cdNumber, String language, String version,
    //                                  String exportType, PublishEntity publish) {
    //    if (language != null) {
    //        logger.info(ActivityLogEntity.EntityLevel.WR, logEvent, 0, cdNumber, exportType,
    //                String.format("lang=%s, v=%s [%s]", language, version, publish.getFileName()));
    //    } else {
    //        logger.info(ActivityLogEntity.EntityLevel.WR, logEvent, 0, cdNumber, exportType,
    //                String.format("v=%s, [%s]", version,  publish.getFileName()));
    //    }
    //}

    private boolean trackPublishedAbstract(PublishedAbstractEntity pa, String publisherId, int dfId,
        PublishEntity publish, Set<String> sendDois, Set<String> skippedNames, PublishDestination dest) {

        boolean shouldBeSkipped = skippedNames != null && skippedNames.contains(pa.getRecordName());
        if (!sendDois.contains(publisherId)) {
            if (shouldBeSkipped) {
                setPublishedAbstractAsPublishNotified(pa, publish, dest);
                return false;
            }
            sendDois.add(publisherId);
        }
        return !shouldBeSkipped;
    }

    private void setPublishedAbstractAsPublishNotified(PublishedAbstractEntity pa, PublishEntity publish,
                                                       PublishDestination dest) {
        int notified = pa.getNotified();
        int resultNotified = dest.checkNotified(notified, publish.getPublishType());
        if (resultNotified > notified) {
            pa.setNotified(PublishedAbstractEntity.getPubNotified(resultNotified));
            manager.persist(pa);
        }
    }

    private StringBuilder addDoi(String doi, StringBuilder sb) {
        StringBuilder ret = sb;
        if (ret == null) {
            ret = new StringBuilder(doi.length());
        }
        return ret.append(doi).append("\n");
    }

    private void sendSpecial(IssueEntity issue, StringBuilder forNihNotify, StringBuilder forOANotify,
        StringBuilder forCLNotify) {
        if (forNihNotify != null) {
            MessageSender.sendNihMandated(issue.getYear(), issue.getNumber(), forNihNotify.toString());
        }
        if (forOANotify != null) {
            MessageSender.sendOpenAccessed(issue.getYear(), issue.getNumber(), forOANotify.toString());
        }
        if (forCLNotify != null) {
            MessageSender.sendCL(issue.getYear(), issue.getNumber(), forCLNotify.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void generateAbstractsForPublishFromMetadata(Map<String, Pair<Integer, PublishEntity>> recordNames,
        boolean mainPub, Set<String> sendDois, Map<String, PublishedAbstractEntity> paMap,
        Set<String> skippedNames, PublishDestination dest) {

        IssueEntity issue = recordNames.values().iterator().next().second.getDb().getIssue();
        int issueFullNumber = issue.getFullNumber();
        boolean spd = CmsUtils.isScheduledIssueNumber(issueFullNumber);

        List<RecordMetadataEntity> mlist = RecordMetadataEntity.queryRecordMetadataByIssue(
            issueFullNumber, recordNames.keySet(), manager).getResultList();
        // prepare list to remove old double records
        Map<String, RecordMetadataEntity> metadata = prepareMetadata(mlist);
        if (metadata.isEmpty()) {
            return;
        }

        StringBuilder forNihNotify = null;
        StringBuilder forOANotify = null;
        StringBuilder forCLNotify = null;
        Res<CheckList> res = CheckList.get();
        CheckList checkList = mainPub ? (Res.valid(res) ? res.get() : new CheckList()) : null;

        for (RecordMetadataEntity rm: metadata.values()) {

            String cdNumber = rm.getCdNumber();
            PublishedAbstractEntity pa = paMap.get(cdNumber);
            if (pa == null) {
                // skip because the record wasn't updated
                continue;
            }

            String publisherId = rm.getPublisherId();
            Pair<Integer, PublishEntity> pair = recordNames.get(cdNumber);

            trackPublishedAbstract(pa, publisherId, pair.first, pair.second, sendDois, skippedNames, dest);

            if (!mainPub || spd) {
                continue;
            }

            if (rm.isNihFunded()) {
                forNihNotify = addDoi(publisherId, forNihNotify);
            }
            if (rm.isGoldOpenAccess()) {
                forOANotify = addDoi(publisherId, forOANotify);
            }
            if ((rm.isNewCitation() && checkList.hasCdNumber(cdNumber))
                || checkList.hasCdNumber(cdNumber, rm.getStatusAsString())) {
                forCLNotify = addDoi(publisherId, forCLNotify);
            }
        }

        if (mainPub) {
            sendSpecial(issue, forNihNotify, forOANotify, forCLNotify);
        }
    }

    private Map<String, RecordMetadataEntity> prepareMetadata(List<RecordMetadataEntity> mlist) {

        Map<String, RecordMetadataEntity> metadata = new HashMap<>();
        for (RecordMetadataEntity rm: mlist) {

            if (rm.isDeleted()) {
                continue;
            }

            String version = rm.getCochraneVersion();
            if (version == null) {
                continue;
            }

            String cdNumber = rm.getCdNumber();

            RecordMetadataEntity tmp = metadata.get(cdNumber);
            if (tmp == null || RevmanMetadataHelper.isFirstLatest(rm, tmp)) {
                tmp = rm;
            }
            metadata.put(cdNumber, tmp);
        }
        return metadata;
    }

    private String prepareXMLDocumentForAbstractsXSLT(String sourceXML, List<String> parts) {
        StringBuilder xslInputBuilder = openContainer(sourceXML);

        for (String part: parts) {
            part = CmsUtils.cutXmlDeclaration(part);
            part = XmlUtils.cutDocTypeDeclaration(part);
            xslInputBuilder.append(part);
        }
        return closeContainer(xslInputBuilder);
    }

    private String prepareXMLDocumentForAbstractsXSLT(IRecord record, String sourceXML, String version, Integer issueId,
                                                      Integer dfId, List<String> parts, int mode) throws IOException {
        String name = record.getName();
        Map<String, Pair<File, File>> paths = (version == null)
            ? (dfId != null && !record.insertTaFromEntire()
                    ? TranslatedAbstractsHelper.getLegacyAbstractsFromIssue(issueId, dfId, name, record)
                    : TranslatedAbstractsHelper.getAbstractsFromEntire(name, false))
            : TranslatedAbstractsHelper.getAbstractsFromPrevious(FilePathBuilder.extractPreviousNumber(version), name,
                false);

        StringBuilder xslInputBuilder = openContainer(sourceXML);

        if (!paths.isEmpty()) {
            record.addLanguages(paths.keySet());
        }

        for (Pair<File, File> path : paths.values()) {

            if (path.second != null) {
                // version 3
                if (mode != MODE_2_ONLY) {
                    String content = InputUtils.readStreamToString(
                            (mode == MODE_2_AND_3_AS_2 ? path.first : path.second).toURI().toURL().openStream());
                    parts.add(content);
                }

            } else if (mode != MODE_3_ONLY) {
                // version < 3
                String content = InputUtils.readStreamToString(path.first.toURI().toURL().openStream());
                xslInputBuilder.append(CmsUtils.cutXmlDeclaration(content));
            }
        }
        return closeContainer(xslInputBuilder);
    }

    private String addXMLDocumentForAbstractsXSLT(String sourceXML, List<String> abstracts) throws IOException {

        StringBuilder xslInputBuilder = openContainer(sourceXML);
        for (String part : abstracts) {
            xslInputBuilder.append(CmsUtils.cutXmlDeclaration(part));
        }
        return closeContainer(xslInputBuilder);
    }

    private StringBuilder openContainer(String sourceXML) {

        StringBuilder xslInputBuilder = new StringBuilder();
        xslInputBuilder.append(sourceXML.substring(0, sourceXML.indexOf(COMPONENT_OPEN_TAG)));
        xslInputBuilder.append("<container>");
        xslInputBuilder.append(sourceXML.substring(sourceXML.indexOf(COMPONENT_OPEN_TAG), sourceXML.length()));
        return xslInputBuilder;
    }

    private String closeContainer(StringBuilder xslInputBuilder) {
        xslInputBuilder.append("</container>");
        return xslInputBuilder.toString();
    }

    public String getSourceForRecordWithInsertedAbstracts(IRecord record, String recordPath, int mode)
        throws Exception {
        return getSourceForRecordWithInsertedAbstracts(record, recordPath, null, null, mode, false);
    }

    public String getSourceForRecordWithInsertedAbstracts(IRecord record, String recordPath,
        Integer issueId, Integer dfId, int mode) throws Exception {
        return getSourceForRecordWithInsertedAbstracts(record, recordPath, issueId, dfId, mode, false);
    }

    public String getSourceForRecordWithInsertedAbstracts(IRecord record, String recordPath,
        Integer issueId, Integer dfId, int mode, boolean hw) throws Exception {

        String recordName = record.getName();
        // get file without abstracts
        String sourceFile = InputUtils.readStreamToString(RepositoryUtils.getRealFile(
                recordPath).toURI().toURL().openStream());
        String doctype = CmsUtils.getDoctypeDeclaration(sourceFile);

        String[] parts = recordPath.split("/");
        parts[parts.length - 2] = "abstracts-tmp";
        String newFile = StringUtils.join(parts, "/");

        String result = transformSource(record, sourceFile, recordPath.contains(
                FilePathCreator.PREVIOUS_DIR) ? FilePathBuilder.cutOffPreviousVersionDir(recordPath) : null,
                issueId, dfId, mode, hw);

        writeFile(doctype, newFile, result);

        String enclosure = recordPath.substring(0, recordPath.lastIndexOf("/") + 1) + recordName;
        parts[parts.length - 1] = recordName;
        String newEnclosure = StringUtils.join(parts, "/");

        File enclosureFile = RepositoryUtils.getRealFile(enclosure);
        if (enclosureFile.exists()) {
            FileUtils.copyDirectory(enclosureFile, RepositoryUtils.getRealFile(newEnclosure));
        }
        return newFile;
    }

    private String transformSource(final IRecord record, final String src, String prevVersion,
        Integer issueId, Integer dfId, int mode, boolean hw) throws Exception {

        String recordName = record.getName();
        List<String> v3Parts = new ArrayList<>();
        String ret = CmsUtils.correctDtdPath(src);

        ret = transform(recordName, hw ? TransformersPool.getTransformerHW() : TransformersPool.getTransformerWOL(),
                prepareXMLDocumentForAbstractsXSLT(record, ret, prevVersion, issueId, dfId, v3Parts, mode));

        if (!v3Parts.isEmpty()) {
            if (mode == MODE_2_AND_3_AS_2) {
                ret = transform(recordName, TransformersPool.getTransformerV3(),
                    addXMLDocumentForAbstractsXSLT(ret, v3Parts));
            } else { // It should be MODE_3_AND_2_AS_3 || MODE_3_ONLY
                ret = converter.mergeTranslations(recordName, prepareXMLDocumentForAbstractsXSLT(ret, v3Parts));
            }
        }
        return ret;
    }

    private String transform(String recordName, Transformer transformer, String content) throws Exception {

        StringWriter writer = new StringWriter();

        String ret = CmsUtils.encodeEntities(content);
        Result output = new StreamResult(writer);
        try {
            transformer.transform(new StreamSource(new StringReader(ret)), output);
        } catch (TransformerException e) {
            throw new Exception("Error in abstract source XML: " + recordName, e);
        }

        ret = writer.toString();
        return CmsUtils.decodeEntities(ret);
    }

    private void writeFile(String doctype, String newFile, String content) throws IOException {

        FileWriter fileWriter = new FileWriter(RepositoryUtils.createFile(newFile));
        fileWriter.append(XmlUtils.XML_HEAD).append("\n").append(doctype).append("\n");
        //String xml = CmsUtils.decodeEntities(content);
        String xml = content;
        fileWriter.append(xml.substring(xml.indexOf(COMPONENT_OPEN_TAG)));
        fileWriter.close();
    }

    private String getSourceAsString(String doctype, String content) throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append(XmlUtils.XML_HEAD).append("\n").append(doctype).append("\n");
        //String xml = CmsUtils.decodeEntities(content);
        String xml = content;
        sb.append(xml.substring(xml.indexOf(COMPONENT_OPEN_TAG)));

        return sb.toString();
    }
}
