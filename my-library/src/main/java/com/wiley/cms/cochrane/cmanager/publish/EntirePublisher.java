package com.wiley.cms.cochrane.cmanager.publish;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSCCAGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSCentralGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSEditorialGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumCCAGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumCentralGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCentralGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCCAGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCDSRGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCDSRTopicGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCentralGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoEditorialGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.udw.UdwGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.send.ds.DSCCASender;
import com.wiley.cms.cochrane.cmanager.publish.send.ds.DSSenderEntire;
import com.wiley.cms.cochrane.cmanager.publish.send.literatum.LiteratumSenderEntire;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.SemanticoSenderEntire;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.SemanticoTopicSenderEntire;
import com.wiley.cms.cochrane.cmanager.publish.send.udw.UdwSenderEntire;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.01.2010
 */

@SuppressWarnings("deprecation")
public abstract class EntirePublisher extends PublisherFactory  {
    private static final Logger LOG = Logger.getLogger(EntirePublisher.class);

    private static final Map<String, Class<? extends IPublish>> PUBLISHERS = new HashMap<>();
    private static final Map<String, Class<? extends IPublish>> SENDERS = new HashMap<>();
    private static final Map<String, Class<? extends IPublish>> UNPACKERS = new HashMap<>();
    private static final Map<String, Class<? extends IPublish>> DELETERS = new HashMap<>();

    static {

        PUBLISHERS.put(WOL3G + CochraneCMSPropertyNames.getCDSRDbName(), ML3GCDSRGeneratorEntire.class);
        PUBLISHERS.put(WOL3G + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL),
                ML3GCentralGeneratorEntire.class);
        PUBLISHERS.put(DS + CochraneCMSPropertyNames.getCDSRDbName(), DSCDSRGeneratorEntire.class);
        PUBLISHERS.put(DS + CochraneCMSPropertyNames.getCentralDbName(), DSCentralGeneratorEntire.class);
        PUBLISHERS.put(DS + CochraneCMSPropertyNames.getEditorialDbName(), DSEditorialGeneratorEntire.class);
        PUBLISHERS.put(DS + CochraneCMSPropertyNames.getCcaDbName(), DSCCAGenerator.class);
        PUBLISHERS.put(SEMANTICO + CochraneCMSPropertyNames.getCDSRDbName(),
                SemanticoCDSRGeneratorEntire.class);
        PUBLISHERS.put(SEMANTICO + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL),
                SemanticoCentralGeneratorEntire.class);
        PUBLISHERS.put(SEMANTICO + CochraneCMSPropertyNames.getEditorialDbName(),
                SemanticoEditorialGeneratorEntire.class);
        PUBLISHERS.put(SEMANTICO + CochraneCMSPropertyNames.getCcaDbName(), SemanticoCCAGenerator.class);
        PUBLISHERS.put(SEMANTICO_TOPICS + CochraneCMSPropertyNames.getCDSRDbName(),
                SemanticoCDSRTopicGeneratorEntire.class);
        PUBLISHERS.put(LITERATUM + CochraneCMSPropertyNames.getCDSRDbName(),
                LiteratumCDSRGeneratorEntire.class);
        PUBLISHERS.put(LITERATUM_DEFAULT, LiteratumGeneratorEntire.class);
        PUBLISHERS.put(LITERATUM + CochraneCMSPropertyNames.getCcaDbName(), LiteratumCCAGeneratorEntire.class);
        PUBLISHERS.put(LITERATUM + CochraneCMSPropertyNames.getCentralDbName(), LiteratumCentralGeneratorEntire.class);
        PUBLISHERS.put(UDW_DEFAULT, UdwGeneratorEntire.class);

        SENDERS.put(DS + CochraneCMSPropertyNames.getCcaDbName(), DSCCASender.class);
        SENDERS.put(DS_DEFAULT, DSSenderEntire.class);

        SENDERS.put(SEMANTICO_DEFAULT, SemanticoSenderEntire.class);
        SENDERS.put(SEMANTICO_TOPICS_DEFAULT, SemanticoTopicSenderEntire.class);
        SENDERS.put(LITERATUM_DEFAULT, LiteratumSenderEntire.class);
        SENDERS.put(UDW_DEFAULT, UdwSenderEntire.class);
    }

    protected EntireDbWrapper db;

    public void start(PublishWrapper publish, String dbName) {
        try {
            db = new EntireDbWrapper(dbName);
            proceed(publish);

        } catch (Exception e) {
            if (publish != null) {

                publish.resetPublishForWaiting(ps);
                rs.setDeleting(publish.getId(), false, false);
                rs.setGenerating(publish.getId(), false, false, 0);
                rs.setUnpacking(publish.getId(), false, false);
                rs.setSending(publish.getId(), false, false, false);
            }
            LOG.error(e, e);
        }
    }

    private void proceed(final PublishWrapper publish) throws JMSException {

        if (publish.isDelete()) {
            PublishOperation.DELETE.start(publish, this);

        } else if (publish.isGenerate()) {
            PublishOperation.GENERATE.start(publish, this);

        } else if (publish.isSend()) {
            PublishOperation.SEND.start(publish, this);

        } else if (publish.isUnpack()) {
            PublishOperation.UNPACK.start(publish, this);
        }

        if (publish.hasPublishToAwait()) {
            PublishWrapper pw = publish.checkPublishAwaitCompleted();
            if (pw != null) {
                pw.resetPublishForWaiting(ps);
            }
        }
        
        if (publish.isGenerate() || publish.isSend() || publish.isUnpack() || publish.isDelete()) {
            proceedNext(publish);
        } else {
            proceedLast(publish, ActivityLogEntity.EntityLevel.ENTIREDB);

            if (publish.hasNext()) {
                proceedNext(publish.getNext());
            } else {
                publish.sendMessages();
            }
        }
    }

    IPublish getDeleter(PublishWrapper publish) {
        try {
            Class<? extends IPublish> clazz = DELETERS.get(publish.getType() + "#" + db.getDbName());
            Constructor<? extends IPublish> constructor = clazz == null
                    ? DELETERS.get(publish.getType() + DEFAULT_SUFFIX).getConstructor(EntireDbWrapper.class)
                    : clazz.getConstructor(EntireDbWrapper.class);
            return constructor.newInstance(db).setContext(rs, ps, flowLogger);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    IPublish getGenerator(PublishWrapper publish) {
        try {
            Class<? extends IPublish> clazz = PUBLISHERS.get(publish.getType() + "#" + db.getDbName());
            Constructor<? extends IPublish> constructor = clazz == null
                    ? PUBLISHERS.get(publish.getType() + DEFAULT_SUFFIX).getConstructor(EntireDbWrapper.class)
                    : clazz.getConstructor(EntireDbWrapper.class);
            return constructor.newInstance(db).setContext(rs, ps, flowLogger);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    IPublish getSender(PublishWrapper publish) {
        try {
            return getPublishInstance(publish, SENDERS, IssuePublisher.SENDERS).setContext(rs, ps, flowLogger);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    IPublish getPublishInstance(PublishWrapper publish, Map<String, Class<? extends IPublish>> entireMap,
        Map<String, Class<? extends IPublish>> issueMap) throws Exception {

        if (publish.byRecords() && publish.getPath().getPubType().canGenerate())  {
            // in general separate records should be send not as fully (entire) package
            Class<? extends IPublish> clazz = issueMap.get(publish.getType() + "#" + db.getDbName());
            Constructor<? extends IPublish> constructor = (clazz == null)
                    ? issueMap.get(publish.getType() + DEFAULT_SUFFIX).getConstructor(ClDbVO.class)
                    : clazz.getConstructor(ClDbVO.class);

            IssueEntity issue = rs.getLastApprovedDatabaseIssue(db.getDbName());
            ClDbVO clDb = new ClDbVO(publish.getPublishEntity().getDb().getId(), db.getDbName(), new IssueVO(
                    DbEntity.NOT_EXIST_ID, issue.getYear(), issue.getNumber(), issue.getPublishDate()));
            return constructor.newInstance(clDb);
        }
        Class<? extends IPublish> clazz = entireMap.get(publish.getType() + "#" + db.getDbName());
        Constructor<? extends IPublish> constructor = (clazz == null)
                    ? entireMap.get(publish.getType() + DEFAULT_SUFFIX).getConstructor(EntireDbWrapper.class)
                    : clazz.getConstructor(EntireDbWrapper.class);
        return constructor.newInstance(db);
    }

    IPublish getUnpacker(PublishWrapper publish) {
        try {
            return getPublishInstance(publish, UNPACKERS, IssuePublisher.UNPACKERS).setContext(rs, ps, flowLogger);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }
}