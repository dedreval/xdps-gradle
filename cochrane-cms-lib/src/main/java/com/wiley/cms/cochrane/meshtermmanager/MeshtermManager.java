package com.wiley.cms.cochrane.meshtermmanager;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang.StringUtils;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.issue.IIssueStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.meshterm.IMeshtermStorage;
import com.wiley.cms.cochrane.cmanager.data.meshterm.MeshtermStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.packagegenerator.IPackageGenerator;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.medlinedownloader.meshtermdownloader.MeshtermDownloaderCallback;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 23.10.2009
 */

@MessageDriven(
    activationConfig =
        {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/meshterm-manager-service"),
            @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
        }, name = "MeshtermQueue")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class MeshtermManager implements MessageListener {

    private static final Logger LOG = Logger.getLogger(MeshtermManager.class);
    private static final int MAX_FILES_ALLOWED = 10000;
    private static final String MESH_HEADING_OPEN_TAG = "<MeshHeading>";
    private static final String MESH_HEADING_CLOSE_TAG = "</MeshHeading>";
    private static final String QUALIFIER_NAME_OPEN_TAG = "<QualifierName";
    private static final String QUALIFIER_NAME_CLOSE_TAG = "</QualifierName>";

    private IMeshtermStorage meshtermStorage;
    private boolean updated;

    private IIssueStorage issueStorage = IssueStorageFactory.getFactory().getInstance();
    private IResultsStorage rs = AbstractManager.getResultStorage();

    @Resource(mappedName = MeshtermDownloaderCallback.QUEUE_NAME)
    private Queue queue;

    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    private ConnectionFactory connectionFactory;

    public void onMessage(Message message) {
        LOG.trace("onMessage starts");
        try {
            if (!(message instanceof ObjectMessage)) {
                throw new JMSException("ObjectMessage expected!");
            }
            Serializable obj = ((ObjectMessage) message).getObject();
            if (!(obj instanceof MeshtermManagerParameters)) {
                throw new JMSException("MeshtermManagerParameters expected!");
            }
            process((MeshtermManagerParameters) obj);
        } catch (Exception e) {
            LOG.error(e, e);
            throw new EJBException();
        }
    }

    private void process(final MeshtermManagerParameters parameters) throws Exception {
        File[] files;
        int it;
        try {
            meshtermStorage = MeshtermStorageFactory.getFactory().getInstance();

            int count = 0;
            it = parameters.getIterator();
            files = parameters.getFiles();
            while (it < files.length && count < MAX_FILES_ALLOWED) {
                count++;
                File file = files[it];
                String recordName = getRecordName(file);
                updateMeshterms(file, recordName, parameters.getIssue());
                it++;
            }
        } catch (Exception e) {
            LOG.error(e, e);
            issueStorage.setIssueMeshtermsDownloaded(parameters.getIssueId(), false);
            issueStorage.setIssueMeshtermsDownloading(parameters.getIssueId(), false);
            return;
        }

        if (it < files.length) {
            parameters.setIterator(it);
            JMSSender.send(connectionFactory, queue, new JMSSender.MessageCreator() {
                public Message createMessage(Session session) throws JMSException {
                    ObjectMessage message = session.createObjectMessage();
                    message.setObject(parameters);
                    return message;
                }
            });
        } else {
            finish(parameters);
        }
    }

    private void finish(MeshtermManagerParameters parameters) {

        String theLogUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);
        IActivityLogService logService = AbstractManager.getActivityLogService();
        logService
            .info(ActivityLogEntity.EntityLevel.ISSUE, ILogEvent.MESHTERMS_DOWNLOADED, parameters.getIssueId(),
                parameters.getTitle(), theLogUser, "Meshterms downloaded");

        issueStorage.setIssueMeshtermsDownloaded(parameters.getIssueId(), true);
        try {
            deliverInitialPackageForDS(parameters.getIssueId());
            findUpdatedRecords(parameters.getIssue(), parameters.getIssueId());

        } catch (Exception e) {
            issueStorage.setIssueMeshtermsDownloading(parameters.getIssueId(), false);
            LOG.error("Initial package delivering failed!", e);
        }
    }

    private void deliverInitialPackageForDS(int issueId) {
        String dbName = CochraneCMSPropertyNames.getCcaDbName();
        int dbId = ResultStorageFactory.getFactory().getInstance().findDb(issueId, dbName);
        PublishWrapper pw = PublishWrapper.createIssuePublishWrapper(PubType.TYPE_DS_MESH, dbName, dbId, false,
                false, new Date());
        if (pw != null) {
            pw.setGenerate(true);
            pw.setSend(true);
            List<PublishWrapper> list = new ArrayList<>();
            list.add(pw);
            CochraneCMSBeans.getPublishService().publishDb(dbId, list);
        } else {
            LOG.warn("cannot create a publish wrapper to send monthly meshterms to ds");
        }
    }

    private void findUpdatedRecords(int issue, int issueId) throws Exception {
        LOG.debug("Creating initial package...");
        List<String> recordNames = meshtermStorage.findUpdatedRecords(issue);
        if (recordNames.isEmpty()) {
            throw new Exception("no records with meshterms");
        }
        //List<String> recNamesWithRevmanMeta = new ArrayList<>();
        //List<String> recNames = new ArrayList<>();
        //for (String recordName : recordNames) {
        //    ICDSRMeta meta = rs.findLatestMetadata(recordName, false);
        //    if (meta.isJats()) {
        //        recNamesWithJatsMeta.add(recordName);
        //    } else {
        //        recNamesWithRevmanMeta.add(recordName);
        //    }
        //}
        IPackageGenerator ipg = CochraneCMSPropertyNames.lookup("MonthlyMeshTermsPackageGenerator",
                                                                IPackageGenerator.class);
        String dbName = CochraneCMSPropertyNames.getCDSRDbName();
        //ipg.generateAndUpload(recNamesWithRevmanMeta, issueId, dbName, "");
        ipg.generateAndUpload(recordNames, issueId, dbName, PackageChecker.WML3G_POSTFIX);
    }

    private String getRecordName(File file) {
        return StringUtils.substringBefore(file.getName(), ".");
    }

    private List<String> parseMeshHeadings(String xml) {

        List<String> result = new ArrayList<>();
        int cur;
        int ind1 = xml.indexOf(MESH_HEADING_OPEN_TAG);
        while (ind1 != -1) {
            StringBuilder meshHeading = new StringBuilder();
            int ind2 = xml.indexOf(MESH_HEADING_CLOSE_TAG, ind1);
            meshHeading.append(xml.substring(ind1, ind2 + MESH_HEADING_CLOSE_TAG.length()));
            result.add(meshHeading.toString());
            cur = ind2;
            ind1 = xml.indexOf(MESH_HEADING_OPEN_TAG, cur);
        }
        return result;
    }

    private String parseDescriptor(String xml) {
        int ind1 = xml.indexOf("<DescriptorName ");
        if (ind1 == -1) {
            return null;
        }
        int ind2 = xml.indexOf("</DescriptorName");
        return xml.substring(ind1, ind2 + "</DescriptorName>".length());
    }

    private List<String> parseQualifiers(String xml) {
        List<String> result = new ArrayList<>();
        int cur;
        int ind1 = xml.indexOf(QUALIFIER_NAME_OPEN_TAG);
        while (ind1 != -1) {
            StringBuilder qualifier = new StringBuilder();
            int ind2 = xml.indexOf(QUALIFIER_NAME_CLOSE_TAG, ind1);
            qualifier.append(xml.substring(ind1, ind2 + QUALIFIER_NAME_CLOSE_TAG.length()));
            result.add(qualifier.toString());
            cur = ind2;
            ind1 = xml.indexOf(QUALIFIER_NAME_OPEN_TAG, cur);
        }
        return result;
    }

    private String parseName(String xml) {
        int ind1 = xml.indexOf(">");
        int ind2 = xml.indexOf("<", ind1);
        return xml.substring(ind1 + 1, ind2).trim();
    }

    private boolean parseMajorTopic(String xml) {
        return StringUtils.substringBetween(xml, "MajorTopicYN=\"", "\"").equals("Y");
    }

    private Set<Integer> parseMeshterms(File file, String recordName) throws Exception {
        Set<Integer> meshterms = new HashSet<>();
        String xml = FileUtils.readFileToString(file, CharEncoding.UTF_8);
        List<String> meshHeadings = parseMeshHeadings(xml);

        for (String meshHeading : meshHeadings) {
            String descriptor = parseDescriptor(meshHeading);
            if (descriptor == null) {
                continue;
            }

            String descriptorName = parseName(descriptor);
            boolean majorTopic = parseMajorTopic(descriptor);
            int descriptorId = findDescriptorId(descriptorName, majorTopic);

            List<String> qualifiers = parseQualifiers(meshHeading);
            if (qualifiers.size() == 0) {
                String qualifierName = "";
                majorTopic = false;
                int qualifierId = findQualifierId(qualifierName, majorTopic);
                meshterms.add(findMeshtermRecordId(recordName, descriptorId, qualifierId));
            } else {
                for (String qualifier : qualifiers) {
                    String qualifierName = parseName(qualifier);
                    majorTopic = parseMajorTopic(qualifier);
                    int qualifierId = findQualifierId(qualifierName, majorTopic);
                    meshterms.add(findMeshtermRecordId(recordName, descriptorId, qualifierId));
                }
            }
        }

        return meshterms;
    }

    @SuppressWarnings("unchecked")
    private void updateMeshterms(File file, String recordName, Integer issue) throws Exception {
        if (!recordNameExists(recordName)) {
            createRecordDate(recordName, issue);
        }

        updated = false;
        Set<Integer> meshterms = parseMeshterms(file, recordName);

        List<Integer> meshtermRecordIds = getMeshtermRecordIds(recordName);
        meshtermRecordIds.removeAll(meshterms);

        if (meshtermRecordIds.size() > 0) {
            deleteMeshterms(meshtermRecordIds);
        }

        if (updated) {
            updateRecordDate(recordName, issue);
        }
    }

    private List<Integer> getMeshtermRecordIds(String recordName) {
        return meshtermStorage.getMeshtermRecordIds(recordName);
    }

    private void deleteMeshterms(List<Integer> meshtermRecordIds) {
        for (Integer meshtermRecordId : meshtermRecordIds) {
            meshtermStorage.deleteMeshtermRecord(meshtermRecordId);
        }
    }

    private void updateRecordDate(String recordName, int issue) {
        meshtermStorage.setIssue(recordName, issue);
    }

    private int findMeshtermRecordId(String recordName, Integer descriptorId, Integer qualifierId) {
        int meshtermRecordId = meshtermStorage.findMeshtermRecordId(recordName, descriptorId, qualifierId);
        if (meshtermRecordId == -1) {
            meshtermRecordId = meshtermStorage.createMeshtermRecord(recordName, descriptorId, qualifierId);
            updated = true;
        }
        return meshtermRecordId;
    }

    private void createRecordDate(String recordName, Integer issue) {
        meshtermStorage.createRecordDate(recordName, issue);
    }

    private boolean recordNameExists(String recordName) {
        return meshtermStorage.recordNameExists(recordName);
    }

    private int findQualifierId(String qualifier, Boolean majorTopic) {
        int qualifierId = meshtermStorage.findQualifierId(qualifier, majorTopic);
        if (qualifierId == -1) {
            qualifierId = meshtermStorage.createQualifier(qualifier, majorTopic);
            updated = true;
        }
        return qualifierId;
    }

    private int findDescriptorId(String descriptor, Boolean majorTopic) {
        int descriptorId = meshtermStorage.findDescriptorId(descriptor, majorTopic);
        if (descriptorId == -1) {
            descriptorId = meshtermStorage.createDescriptor(descriptor, majorTopic);
            updated = true;
        }
        return descriptorId;
    }
}