package com.wiley.cms.cochrane.cmanager.publish;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.entitywrapper.DbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.generate.aries.AriesAcknowledgementDeliverGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.aries.AriesAcknowledgementPublishGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSCCAGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSCDSRGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSCDSRMonthlyGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSCentralGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSEditorialGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ds.DSMeSHGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumCCAGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumCDSRGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumCentralGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.literatum.LiteratumWRGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCDSRGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GCentralGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.ml3g.ML3GGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCCAGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCDSRGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCDSRTopicGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCentralDelGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoCentralGenerator;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.SemanticoEditorialGenerator;
import com.wiley.cms.cochrane.cmanager.publish.send.aries.AriesAcknowledgementDeliverSender;
import com.wiley.cms.cochrane.cmanager.publish.send.aries.AriesAcknowledgementPublishSender;
import com.wiley.cms.cochrane.cmanager.publish.send.cochrane.CochraneSender;
import com.wiley.cms.cochrane.cmanager.publish.send.ds.DSCCASender;
import com.wiley.cms.cochrane.cmanager.publish.send.ds.DSCDSRMonthlySender;
import com.wiley.cms.cochrane.cmanager.publish.send.ds.DSMeSHSender;
import com.wiley.cms.cochrane.cmanager.publish.send.ds.DSSender;
import com.wiley.cms.cochrane.cmanager.publish.send.literatum.LiteratumSender;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.SemanticoCCASender;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.SemanticoCentralDelSender;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.SemanticoSender;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.SemanticoTopicSender;
import com.wiley.cms.cochrane.cmanager.publish.send.udw.UdwSender;

import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.tes.util.Logger;

/**
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @author <a href='mailto:osoletskay@wiley.com'>Olga Soletskay</a>
 * @version 1.0
 */
@SuppressWarnings("deprecation")
public abstract class IssuePublisher extends PublisherFactory {
    protected static final Logger LOG = Logger.getLogger(IssuePublisher.class);

    protected static final Map<String, Class<? extends IPublish>> UNPACKERS = new HashMap<>();
    protected static final Map<String, Class<? extends IPublish>> SENDERS = new HashMap<>();
    protected static final Map<String, Class<? extends IPublish>> PUBLISHERS = new HashMap<>();

    protected ClDbVO db;

    static {
        PUBLISHERS.put(WOL3G_DEFAULT, ML3GGenerator.class);
        PUBLISHERS.put(WOL3G + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL),
                ML3GCentralGenerator.class);
        PUBLISHERS.put(WOL3G + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV),
                ML3GCDSRGenerator.class);
        PUBLISHERS.put(DS + CochraneCMSPropertyNames.getCentralDbName(), DSCentralGenerator.class);
        PUBLISHERS.put(DS + CochraneCMSPropertyNames.getCDSRDbName(), DSCDSRGenerator.class);
        PUBLISHERS.put(DS + CochraneCMSPropertyNames.getEditorialDbName(), DSEditorialGenerator.class);
        PUBLISHERS.put(DS + CochraneCMSPropertyNames.getCcaDbName(), DSCCAGenerator.class);
        PUBLISHERS.put(PubType.TYPE_DS_MONTHLY + "#" + CochraneCMSPropertyNames.getCDSRDbName(),
                DSCDSRMonthlyGenerator.class);
        PUBLISHERS.put(PubType.TYPE_DS_MESH + "#" + CochraneCMSPropertyNames.getCcaDbName(), DSMeSHGenerator.class);
        PUBLISHERS.put(PubType.TYPE_DS_MESH + "#" + CochraneCMSPropertyNames.getCDSRDbName(), DSMeSHGenerator.class);
        PUBLISHERS.put(SEMANTICO + CochraneCMSPropertyNames.getCDSRDbName(), SemanticoCDSRGenerator.class);
        PUBLISHERS.put(SEMANTICO + CochraneCMSPropertyNames.getCentralDbName(), SemanticoCentralGenerator.class);
        PUBLISHERS.put(SEMANTICO + CochraneCMSPropertyNames.getEditorialDbName(), SemanticoEditorialGenerator.class);
        PUBLISHERS.put(SEMANTICO + CochraneCMSPropertyNames.getCcaDbName(), SemanticoCCAGenerator.class);
        PUBLISHERS.put(SEMANTICO_TOPICS + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV),
                SemanticoCDSRTopicGenerator.class);
        PUBLISHERS.put(SEMANTICO_DEL + CochraneCMSPropertyNames.getCentralDbName(), SemanticoCentralDelGenerator.class);

        PUBLISHERS.put(LITERATUM_DEFAULT, LiteratumGenerator.class);
        PUBLISHERS.put(LITERATUM + CochraneCMSPropertyNames.getCDSRDbName(), LiteratumCDSRGenerator.class);
        PUBLISHERS.put(LITERATUM + CochraneCMSPropertyNames.getEditorialDbName(), LiteratumWRGenerator.class);
        PUBLISHERS.put(LITERATUM + CochraneCMSPropertyNames.getCentralDbName(), LiteratumCentralGenerator.class);
        PUBLISHERS.put(LITERATUM + CochraneCMSPropertyNames.getCcaDbName(), LiteratumCCAGenerator.class);

        PUBLISHERS.put(PubType.TYPE_ARIES_ACK_D + "#" + CochraneCMSPropertyNames.getCDSRDbName(),
                AriesAcknowledgementDeliverGenerator.class);
        PUBLISHERS.put(PubType.TYPE_ARIES_ACK_P + "#" + CochraneCMSPropertyNames.getCDSRDbName(),
                AriesAcknowledgementPublishGenerator.class);
        PUBLISHERS.put(PubType.TYPE_ARIES_ACK_D + "#" + CochraneCMSPropertyNames.getEditorialDbName(),
                AriesAcknowledgementDeliverGenerator.class);
        PUBLISHERS.put(PubType.TYPE_ARIES_ACK_P + "#" + CochraneCMSPropertyNames.getEditorialDbName(),
                AriesAcknowledgementPublishGenerator.class);
        PUBLISHERS.put(PubType.TYPE_ARIES_ACK_D + "#" + CochraneCMSPropertyNames.getCcaDbName(),
                AriesAcknowledgementDeliverGenerator.class);
        PUBLISHERS.put(PubType.TYPE_ARIES_ACK_P + "#" + CochraneCMSPropertyNames.getCcaDbName(),
                AriesAcknowledgementPublishGenerator.class);

        SENDERS.put(DS + CochraneCMSPropertyNames.getCcaDbName(), DSCCASender.class);
        SENDERS.put(DS_DEFAULT, DSSender.class);
        SENDERS.put(PubType.TYPE_DS_MONTHLY + "#" + CochraneCMSPropertyNames.getCDSRDbName(),
                DSCDSRMonthlySender.class);
        SENDERS.put(PubType.TYPE_DS_MESH + "#" + CochraneCMSPropertyNames.getCcaDbName(), DSMeSHSender.class);
        SENDERS.put(PubType.TYPE_DS_MESH + "#" + CochraneCMSPropertyNames.getCDSRDbName(), DSMeSHSender.class);
        SENDERS.put(SEMANTICO + CochraneCMSPropertyNames.getCcaDbName(), SemanticoCCASender.class);
        SENDERS.put(SEMANTICO_DEFAULT, SemanticoSender.class);
        SENDERS.put(SEMANTICO_TOPICS_DEFAULT, SemanticoTopicSender.class);
        SENDERS.put(SEMANTICO_DEL + CochraneCMSPropertyNames.getCentralDbName(), SemanticoCentralDelSender.class);
        SENDERS.put(LITERATUM_DEFAULT, LiteratumSender.class);
        SENDERS.put(UDW_DEFAULT, UdwSender.class);

        SENDERS.put(PubType.TYPE_ARIES_ACK_D + "#" + CochraneCMSPropertyNames.getCDSRDbName(),
                AriesAcknowledgementDeliverSender.class);
        SENDERS.put(PubType.TYPE_ARIES_ACK_P + "#" + CochraneCMSPropertyNames.getCDSRDbName(),
                AriesAcknowledgementPublishSender.class);
        SENDERS.put(PubType.TYPE_ARIES_ACK_D + "#" + CochraneCMSPropertyNames.getEditorialDbName(),
                AriesAcknowledgementDeliverSender.class);
        SENDERS.put(PubType.TYPE_ARIES_ACK_P + "#" + CochraneCMSPropertyNames.getEditorialDbName(),
                AriesAcknowledgementPublishSender.class);
        SENDERS.put(PubType.TYPE_ARIES_ACK_D + "#" + CochraneCMSPropertyNames.getCcaDbName(),
                AriesAcknowledgementDeliverSender.class);
        SENDERS.put(PubType.TYPE_ARIES_ACK_P + "#" + CochraneCMSPropertyNames.getCcaDbName(),
                AriesAcknowledgementPublishSender.class);
        SENDERS.put(PubType.TYPE_COCHRANE_P + "#" + CochraneCMSPropertyNames.getCDSRDbName(),
                CochraneSender.class);
    }


    IPublish getDeleter(PublishWrapper publish) {
        return null;
    }

    IPublish getGenerator(PublishWrapper publish) {
        try {
            Class<? extends IPublish> clazz = PUBLISHERS.get(publish.getType() + "#" + db.getTitle());
            Constructor<? extends IPublish> constructor =
                    clazz == null ? PUBLISHERS.get(publish.getType() + DEFAULT_SUFFIX).getConstructor(ClDbVO.class)
                            : clazz.getConstructor(ClDbVO.class);
            return constructor.newInstance(db).setContext(rs, ps, flowLogger);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    IPublish getSender(PublishWrapper publish) {
        try {
            Class<? extends IPublish> clazz = SENDERS.get(publish.getType() + "#" + db.getTitle());
            Constructor<? extends IPublish> constructor =
                    clazz == null ? SENDERS.get(publish.getType() + DEFAULT_SUFFIX).getConstructor(ClDbVO.class)
                            : clazz.getConstructor(ClDbVO.class);
            return constructor.newInstance(db).setContext(rs, ps, flowLogger);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    IPublish getUnpacker(PublishWrapper publish) {
        try {
            Class<? extends IPublish> clazz = UNPACKERS.get(publish.getType() + "#" + db.getTitle());
            Constructor<? extends IPublish> constructor =
                    clazz == null ? UNPACKERS.get(publish.getType() + DEFAULT_SUFFIX).getConstructor(ClDbVO.class)
                            : clazz.getConstructor(ClDbVO.class);
            return constructor.newInstance(db).setContext(rs, ps, flowLogger);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    public void start(PublishWrapper publish, int dbId) {
        try {
            DbWrapper dbWrapper = new DbWrapper(dbId);
            db = new ClDbVO(dbWrapper.getEntity());
            proceed(publish);

        } catch (Throwable e) {

            publish.resetPublishForWaiting(ps);
            rs.setGenerating(publish.getId(), false, false, 0);
            rs.setUnpacking(publish.getId(), false, false);
            rs.setSending(publish.getId(), false, false, false);

            LOG.error(e.getMessage(), e);
        }
    }

    protected void proceed(final PublishWrapper publish) throws Exception {

        if (publish.isGenerate()) {
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

        if (publish.isGenerate() || publish.isSend() || publish.isUnpack()) {
            proceedNext(publish);
        } else {
            proceedLast(publish, ActivityLogEntity.EntityLevel.DB);

            if (publish.hasNext()) {
                proceedNext(publish.getNext());
            } else {
                publish.sendMessages();
            }
        }
    }
}

