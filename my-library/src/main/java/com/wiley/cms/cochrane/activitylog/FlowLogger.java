package com.wiley.cms.cochrane.activitylog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.activitylog.kafka.IKafkaMessageProducer;
import com.wiley.cms.cochrane.activitylog.kafka.KafkaMessageProducer;
import com.wiley.cms.cochrane.activitylog.kafka.KafkaProducerQueue;
import com.wiley.cms.cochrane.activitylog.snowflake.SFEvent;
import com.wiley.cms.cochrane.activitylog.snowflake.SFType;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IKibanaRecord;
import com.wiley.cms.cochrane.cmanager.data.record.KibanaArchieRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.BitValue;
import com.wiley.tes.util.KibanaUtil;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Pair;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/15/2021
 */
@Stateless
@Local(IFlowLogger.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class FlowLogger implements IFlowLogger {
    private static final Logger LOG = Logger.getLogger(FlowLogger.class);

    private static final SFTypeSet EMPTY = new SFTypeSet(false);
    private static final SFTypeSet FLOW = new SFTypeSet(true);
    private static final SFTypeSet PRODUCT_CREATED = new SFTypeSet(false, true, false, false,
            FlowProduct.State.CREATED);
    private static final SFTypeSet PRODUCT_PUBLISHED = new SFTypeSet(true, true, false, false,
            FlowProduct.State.PUBLISHED);
    private static final SFTypeSet PRODUCT_OFFLINE = new SFTypeSet(true, true, false, false,
            FlowProduct.State.DELETED);
    private static final SFTypeSet TRANSLATION_PUBLISHED = new SFTypeSet(true, false, true, false,
            FlowProduct.State.PUBLISHED);
    private static final SFTypeSet TRANSLATION_DELETED = new SFTypeSet(false, false, true, false,
            FlowProduct.State.DELETED);
    private static final SFTypeSet TRANSLATION_RETRACTED = new SFTypeSet(true, false, true, false,
            FlowProduct.State.DELETED);
    private static final SFTypeSet PACKAGE_PRODUCT_CREATED = new SFTypeSet(true, false, false, true,
            FlowProduct.State.RECEIVED);

    @EJB(beanName = "ActivityLogService")
    private IActivityLogService activityLog;

    @EJB(beanName = "UUIDManager")
    private IUUIDManager sidManager;

    @EJB(beanName = "RecordCache")
    private IRecordCache recordCache;

    @Resource(mappedName = JMSSender.CONNECTION_LOOKUP)
    private QueueConnectionFactory connectionFactory;

    @Resource(mappedName = KafkaProducerQueue.MAP_NAME)
    private Queue kafkaQueue;

    private IKafkaMessageProducer kafkaProducer = KafkaMessageProducer.instance();
    private Res<Property> snowFlake = CochraneCMSPropertyNames.getSnowFlakeSwitch();

    private final MessageCreator jmsCreator = new MessageCreator();

    @Override
    public IActivityLog getActivityLog() {
        return activityLog;
    }

    @Override
    public IRecordCache getRecordCache() {
        return recordCache;
    }

    @Override
    public IKafkaMessageProducer getKafkaProducer() {
        return kafkaProducer;
    }

    @Override
    public IUUIDManager getUUIDManager() {
        return sidManager;
    }
    
    @Override
    public int reproduceFlowLogEvents() {
        List<FlowLogEntity> list = activityLog.getFlowLogsUncompleted(
                CochraneCMSPropertyNames.getKafkaProducerResendBatch());
        list.forEach(f -> reproduceFlowLogEvent(f.getId()));
        return list.size();
    }

    @Override
    public SFEvent[] completeFlowLogEvents() {
        SFEvent[] events = kafkaProducer.checkMessages();
        for (SFEvent sfEvent: events) {
            FlowLogEntity flowLog = activityLog.getFlowLog(sfEvent.eventId);
            if (flowLog != null) {
                activityLog.setFlowLogCompleted(sfEvent.eventId, sfEvent.type.complete(flowLog.getSendTo()));
            }
        }
        return events;
    }

    @Override
    public boolean reproduceFlowLogEvent(Long flowLogId) {
        FlowLogEntity flowLog = activityLog.getFlowLog(flowLogId);
        if (flowLog != null) {
            FlowEventProduct product = new FlowEventProduct(flowLog);
            product.setTransactionId(findTransactionId(product.getSourcePackageId()));

            int event = flowLog.getEvent().getId();
            if (event == ILogEvent.PRODUCT_CREATED || event == ILogEvent.PRODUCT_SAVED
                    || event == ILogEvent.PRODUCT_NOTIFIED_ON_RECEIVED) {
                setTitleForReproduce(product, flowLog.getEntityId());
            }
            int sendTo = flowLog.getSendTo();
            SFTypeSet to = new SFTypeSet(SFType.FLOW.has(sendTo), SFType.PRODUCT.has(sendTo),
                    SFType.TRANSLATION.has(sendTo), SFType.PACKAGE_PRODUCT.has(sendTo), product.getState());
            send(flowLog, product.getPackageName(), flowLog.getDate(), product.getTransactionId(), product.getError(),
                    product, to);
            return true;
        }
        return false;
    }

    private void setTitleForReproduce(FlowEventProduct product, Integer entityId) {

        IPublishStorage ps = CochraneCMSBeans.getPublishStorage();
        Integer titleId = product.getTitleId();
        if (titleId != null && !TitleEntity.EMPTY_TITLE_ID.equals(titleId)) {
            TitleEntity te = ps.find(TitleEntity.class, product.getTitleId());
            if (te != null) {
                product.setTitle(CmsUtils.unescapeEntities(te.getTitle()));
                return;
            }
        }
        if (product.getLanguage() == null) {
            // supporting an old approach
            PublishedAbstractEntity pae = ps.find(PublishedAbstractEntity.class, entityId);
            if (pae != null && pae.getRecordId() != null) {
                RecordEntity re = ps.find(RecordEntity.class, pae.getRecordId());
                RecordMetadataEntity rme = re == null ? null : re.getMetadata();
                if (rme != null && product.getPubCode().equals(
                        RevmanMetadataHelper.buildPubName(rme.getCdNumber(), rme.getPubNumber()))) {
                    product.setTitle(CmsUtils.unescapeEntities(rme.getTitle()));
                }
            }
        }
    }

    @Override
    public void onPackageReceived(BaseType bt, String packageName, Integer packageId, String vendor,
                                  String errorMessage) {
        String uuid = getTransactionId(ILogEvent.PRODUCT_RECEIVED, packageId);
        if (errorMessage != null) {
            onPackageFlowEventErr(ILogEvent.PRODUCT_RECEIVED, packageName, new Date(), new FlowProductPackage(
                    packageId, packageName, bt, vendor, uuid), errorMessage, toFlow(bt.hasSFLogging()));
        } else {
            onPackageFlowEvent(ILogEvent.PRODUCT_RECEIVED, packageName, new Date(), new FlowProductPackage(
                    packageId, packageName, bt, vendor, uuid), toFlow(bt.hasSFLogging()));
        }
    }

    @Override
    public void onPackageUnpacked(BaseType bt, String packageName, Integer packageId, String vendor, String title,
                                  Integer recordsCount, String errorMessage) {
        IFlowProduct product = new FlowProductPackage(packageId, packageName, bt, vendor, findTransactionId(packageId),
                recordsCount, recordsCount);
        product.setTitle(title);
        if (errorMessage == null) {
            //getTransactionId(ILogEvent.PRODUCT_UNPACKED, packageId);
            onPackageFlowEvent(ILogEvent.PRODUCT_UNPACKED, packageName, new Date(), product,
                    PACKAGE_PRODUCT_CREATED.check(send(bt.hasSFLogging())));
        } else {
            onPackageFlowEventErr(ILogEvent.PRODUCT_UNPACKED, packageName, new Date(), product,
                errorMessage, PACKAGE_PRODUCT_CREATED.check(send(bt.hasSFLogging())));
        }
    }

    @Override
    public void onPackagePublishingStarted(BaseType bt, String packageName, Integer packageId, String vendor) {
        onPackageFlowEvent(ILogEvent.PRODUCT_PUBLISHING_STARTED, packageName, new Date(), new FlowProductPackage(
            packageId, packageName, bt, vendor, findTransactionId(packageId)), toFlow(bt.hasSFLogging()));
    }

    @Override
    public void onPackageFlowEvent(int event, BaseType bt, String packageName, Integer packageId, String vendor,
                                   String transactionId, Integer... counters) {
        onPackageFlowEvent(event, packageName, new Date(), new FlowProductPackage(packageId, packageName, bt,
            vendor, transactionId == null ? findTransactionId(packageId) : transactionId, counters),
                toFlow(bt.hasSFLogging()));
    }

    @Override
    public void onPackageFlowEventError(int event, BaseType bt, String packageName, Integer packageId, String vendor,
                                        String transactionId, String errorMessage, Integer... counters) {
        IFlowProduct product = new FlowProductPackage(packageId, packageName, bt, vendor,
                transactionId == null ? findTransactionId(packageId) : transactionId);
        onPackageFlowEventErr(event, packageName, new Date(), new FlowProductPackage(packageId, packageName, bt,
            vendor, transactionId == null ? findTransactionId(packageId) : transactionId, counters),
                errorMessage, toFlow(bt.hasSFLogging()));
    }

    @Override
    public void receiveProduct(String packageName, Integer packageId, ArchieEntry entry,
                               String vendor, String spdDate, int hp) {
        if (entry != null) {
            String cdNumber = entry.getName();
            if (recordCache.getPreRecord(packageId, cdNumber) == null) {
                IKibanaRecord kr = createRecord(packageName, packageId, cdNumber, entry.getPubNumber(), entry,
                        vendor, spdDate);
                updateRecord(kr, entry, entry.getStage(), entry.getStatus(), entry.getManuscriptNumber(), spdDate, hp);
                recordCache.checkPreRecord(packageId, cdNumber, kr);
            }
        }
    }

    @Override
    public void onProductReceived(String packageName, Integer packageId, String cdNumber, int pubNumber, String vendor,
                                  String spdDate, boolean dashboard) {
        onReceived(createOnReceived(packageName, packageId, cdNumber, pubNumber, null, vendor, spdDate),
                packageName, null, null, dashboard, false);
    }

    @Override
    public void onProductReceived(String packageName, Integer packageId, ArchieEntry entry, String vendor,
                                  String spdDate, boolean dashboard) {
        onReceived(createOnReceived(packageName, packageId, entry.getName(), entry.getPubNumber(), entry, vendor,
                spdDate), packageName, entry.getLanguage(), entry, dashboard, false);
    }

    @Override
    public void onProductCanceled(String packageName, Integer packageId, ArchieEntry entry, String vendor,
                                  boolean dashboard) {
        onReceived(createOnCanceled(packageName, packageId, entry.getName(), entry.getPubNumber(), entry, vendor),
                packageName, entry.getLanguage(), entry, dashboard, true);
    }

    @Override
    public void onProductValidated(String cdNumber, boolean dashboard, boolean spd) {
        onProductEvent(KibanaUtil.Event.VALIDATED, cdNumber, dashboard, spd);
    }

    @Override
    public void onProductValidated(ArchieEntry entry, boolean dashboard, boolean spd) {
        IKibanaRecord kr = recordCache.getKibanaRecord(entry.getName(), spd);
        if (kr != null) {
            synchronized (kr) {
                if (dashboard) {
                    KibanaUtil.logKibanaRecEvent(kr, entry.getLanguage(), KibanaUtil.Event.VALIDATED, null);
                }
                IFlowProduct flowProduct = kr.getFlowProduct(entry.getLanguage());
                if (flowProduct != null) {
                    flowProduct.setEntityId(entry.getId());
                    flowProduct.setTitleId(entry.getTitleId());
                    onProductFlowEvent(ILogEvent.PRODUCT_VALIDATED, kr.getDfName(), new Date(),
                            kr.getFlowProduct().getTransactionId(), flowProduct, toFlow(dashboard));
                }
            }
        }
    }

    public void onProductUnpacked(String packageName, Integer packageId, String cdNumber, String language,
                                  boolean dashboard, boolean spd) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, spd);
        if (kr == null) {
            kr = checkRecord(ILogEvent.PRODUCT_UNPACKED, packageId, cdNumber, language, spd, dashboard);
        }
        if (kr != null) {
            IFlowProduct product = language != null ? kr.addLanguage(language) : kr.getFlowProduct();
            if (language == null) {
                kr.getFlowProduct().setFlowAndProductState(FlowProduct.State.RECEIVED, null);
            }
            onProductFlowEvent(ILogEvent.PRODUCT_UNPACKED, packageName, new Date(),
                kr.getFlowProduct().getTransactionId(), product, PACKAGE_PRODUCT_CREATED.check(send(dashboard)), true);
        }
    }

    @Override
    public void onProductConverted(String cdNumber, String publicationType, boolean dashboard) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber);
        if (kr != null) {
            synchronized (kr) {
                FlowProduct product = kr.getFlowProduct();
                product.setPublicationType(publicationType);
                if (dashboard) {
                    KibanaUtil.logKibanaRecEvent(kr, KibanaUtil.Event.CONVERTED, null);
                }
                onProductFlowEvent(ILogEvent.PRODUCT_CONVERTED, null, new Date(), product.getTransactionId(),
                        product, toFlow(dashboard), true);
            }
        }
    }

    @Override
    public void onProductRendered(String cdNumber, boolean dashboard) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber);
        if (kr != null) {
            synchronized (kr) {
                if (dashboard) {
                    KibanaUtil.logKibanaRecEvent(kr, KibanaUtil.Event.RENDERED, null);
                }
                onProductFlowEvent(ILogEvent.PRODUCT_RENDERED, null, new Date(), kr.getFlowProduct().getTransactionId(),
                        kr.getFlowProduct(), toFlow(dashboard), true);
            }
        }
    }

    @Override
    public void onProductSaved(String cdNumber, boolean newDoi, boolean dashboard, boolean spd) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, spd);
        if (kr != null) {
            synchronized (kr) {
                if (dashboard) {
                    KibanaUtil.logKibanaRecEvent(kr, KibanaUtil.Event.UPLOADED_TO_DB, null);
                }
                FlowProduct flowProduct = kr.getFlowProduct();
                if (newDoi) {
                    flowProduct.setProductState(FlowProduct.State.CREATED, null);
                }
                onProductCreated(new Date(), flowProduct.isCreated(), flowProduct, send(dashboard));
            }
        }
    }

    @Override
    public void onTranslationDeleted(String cdNumber, Collection<String> deletedTranslations, boolean dashboard) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber);
        if (kr == null) {
            return;
        }
        FlowProduct flowProduct = kr.getFlowProduct();
        Date date = new Date();
        SFTypeSet set = TRANSLATION_DELETED.check(send(dashboard));
        synchronized (kr) {
            for (String language : deletedTranslations) {
                IFlowProduct tr = flowProduct.getTranslation(language);
                if (tr == null) {
                    tr = flowProduct.createTranslation(null, language, false);
                    flowProduct.addDeletedTranslation(activityLog.logDeliveryFlow(ActivityLogEntity.LogLevel.INFO,
                        ILogEvent.PRODUCT_DELETED, flowProduct.getSourcePackageId(), null, date, tr, set.toSend), tr);

                } else {
                    activityLog.logDeliveryFlow(ActivityLogEntity.LogLevel.INFO, ILogEvent.PRODUCT_RETRACTED,
                            flowProduct.getSourcePackageId(), null, date, tr, EMPTY.toSend);
                }
                tr.setState(FlowProduct.State.DELETED);
            }
        }
    }

    public void onProductDeleted(String packageName, Integer packageId, String cdNumber, boolean dashboard,
                                 boolean spd) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, spd);
        Date date = new Date();
        if (kr != null) {
            FlowProduct flowProduct = kr.getFlowProduct();
            onProductFlowEvent(ILogEvent.PRODUCT_DELETED, null, date, flowProduct.getTransactionId(), flowProduct,
                    toFlow(dashboard));
            if (packageId != null && !packageId.equals(flowProduct.getSourcePackageId())) {
                activityLog.logDeliveryFlow(ActivityLogEntity.LogLevel.INFO, ILogEvent.PRODUCT_DELETED,
                        packageId, packageName, date, flowProduct, EMPTY.toSend);
            }
            flowProduct.setState(FlowProduct.State.DELETED);
        }
    }

    @Override
    public void onProductsPublishingStarted(Collection<String> cdNumbers, boolean dashboard, boolean spd) {
        Date date = new Date();
        SFTypeSet to = toFlow(dashboard);
        for (String cdNumber: cdNumbers) {
            IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, spd);
            if (kr != null) {
                synchronized (kr) {
                    if (dashboard) {
                        KibanaUtil.logKibanaRecEvent(kr, KibanaUtil.Event.PUBLISHING_STARTED, null);
                    }
                    onProductFlowEvent(ILogEvent.PRODUCT_PUBLISHING_STARTED, null, date,
                            kr.getFlowProduct().getTransactionId(), kr.getFlowProduct(), to, true);
                }
            }
        }
    }

    private void logDashboardEvent(int flowEvent, KibanaUtil.Event event, IKibanaRecord kr, String packageName,
                                   String uuid, String errMsg, IFlowProduct product) {
        Date date = new Date();
        if (errMsg != null) { // error handling
            if (kr != null) {
                KibanaUtil.logKibanaRecEvent(kr, KibanaUtil.Event.ERROR, errMsg);
            }
            onProductFlowError(flowEvent, packageName, date, uuid, errMsg, product, toFlow(true), true);
            return;
        }
        if (kr == null) {    // handling without record cache
            onProductFlowEvent(flowEvent, packageName, date, uuid, product, true);
            return;
        }
        if (event != null) { // normal handling
            KibanaUtil.logKibanaRecEvent(kr, event, null);
            event.logFlow(packageName, kr, date, this, uuid);
        }
    }

    @Override
    public void onProductsPublishingSent(String publishFileName, Integer publishType, Integer dfId,
                                         String cdNumber, String uuid, String errMsg, boolean spd) {

        boolean ds = PubType.DS_DB_TYPES.contains(publishType);
        boolean ackP = !ds && PubType.TYPES_ARIES_ACK_P.contains(publishType);
        boolean ack = ackP || (!ds && PubType.TYPES_ARIES_ACK_D.contains(publishType));

        IKibanaRecord kr = ds && !spd ? recordCache.getKibanaRecord(cdNumber)
                : recordCache.getKibanaRecord(cdNumber, spd);
        if (kr != null) {
            synchronized (kr) {
                KibanaUtil.Event event = (!ds || kr.getFlowProduct().wasPublished())
                        ? KibanaUtil.setPublishNames(publishFileName, publishType, kr) : null;
                if (event != null && kr.getFlowProduct().toDashboard()) {
                    logDashboardEvent(event.getFlowLogEvent(), event, kr, publishFileName,
                            uuid == null ? kr.getFlowProduct().getTransactionId() : uuid, errMsg, kr.getFlowProduct());
                }
                if (!ds && !ack && spd) {
                    // reset blocking for SPD
                    recordCache.activateRecord(cdNumber, kr.getPubName(), false);
                }
            }
        } else if (ack && dfId != null) {
            onAcknowledgementPublishNoCache(publishFileName, dfId, cdNumber, uuid, ackP, errMsg);
        }
        if (ds) {
            kr = recordCache.checkPostRecord(cdNumber, null);
            if (kr != null && kr.getFlowProduct().toDashboard()) {
                synchronized (kr) {
                    KibanaUtil.Event event = KibanaUtil.setPublishNames(publishFileName, publishType, kr);
                    logDashboardEvent(event.getFlowLogEvent(), event, kr, publishFileName,
                            uuid == null ? kr.getFlowProduct().getTransactionId() : uuid, errMsg, kr.getFlowProduct());
                }
            }
        }
    }

    private void onAcknowledgementPublishNoCache(String fileName, Integer dfId, String cdNumber, String uuid,
                                                 boolean ackP, String errMsg) {
        if (ackP)   {
            PublishedAbstractEntity pae = CochraneCMSBeans.getPublishStorage().findWhenReadyByAcknowledgementId(
                    RecordHelper.buildRecordNumber(cdNumber), dfId);
            if (pae != null && pae.getBaseType().hasSFLogging()) {
                logDashboardEvent(ILogEvent.PRODUCT_ARIES_ACK_ON_PUBLISHED, null, null, fileName, uuid, errMsg, pae);
                return;
            }
        }
        List<PublishedAbstractEntity> list = CochraneCMSBeans.getRecordManager().getWhenReadyByDeliveryPackage(dfId);
        if (!list.isEmpty()) {
            PublishedAbstractEntity pae = list.get(0);
            if (pae.getBaseType().hasSFLogging()) {
                logDashboardEvent(ackP ? ILogEvent.PRODUCT_ARIES_ACK_ON_PUBLISHED
                                : ILogEvent.PRODUCT_ARIES_ACK_ON_RECEIVED, null, null, fileName, uuid, errMsg, pae);
            }
        }
    }

    @Override
    public void onProductPublished(String cdNumber, PublishDestination dest, Date eventDate, String firstOnlineDate,
                                   String onlineDate, boolean dashboard, boolean offline) {
        KibanaUtil.Event event = KibanaUtil.getPublishedEvent(dest, offline);
        if (event != null){
            IKibanaRecord kr = offline ? recordCache.getKibanaRecord(cdNumber, true)
                    : recordCache.getKibanaRecord(cdNumber);
            if (kr != null) {
                synchronized (kr) {
                    KibanaUtil.logKibanaRecEvent(kr, event, null);
                    FlowProduct product = kr.getFlowProduct();
                    if (product.isExisted()) {
                        product.setFirstOnlineDate(firstOnlineDate);
                        product.setOnlineDate(onlineDate);
                        if (offline) {
                            product.sPD(false);
                        } else if (PublishDestination.SEMANTICO == dest){
                            product.setFlowAndProductState(FlowProduct.State.PUBLISHED, null);
                        }
                    }
                    if (dashboard) {
                        event.logFlow(kr, new Date(), this, kr.getFlowProduct().getTransactionId());
                    }
                }
            }
        }
    }

    @Override
    public void onProductFlowEvent(int event, String packageName, Date date, String transactionId, IFlowProduct product,
                                   boolean withTranslations) {
        onProductFlowEvent(event, packageName, date, transactionId, product, toFlow(true), withTranslations);
    }

    @Override
    public void onProductAcknowledgedReceivedSent(Date date, IFlowProduct product) {
        onProductFlowEvent(ILogEvent.PRODUCT_ARIES_ACK_ON_RECEIVED, null, date, product.getTransactionId(), product,
                toFlow(true), false);
    }

    @Override
    public void onProductPublished(int event, String packageName, Date date, String firstOnline, IFlowProduct product) {

        boolean send = snowFlake();
        String uuid = product.getTransactionId();
        if (product.sPD().off()) {
            product.setState(FlowProduct.State.DELETED);
            onProductFlowEvent(event, packageName, date, uuid, product, PRODUCT_OFFLINE.check(send));
            return;
        }

        if (product.isExisted()) {
            product.setState(FlowProduct.State.PUBLISHED);
            onProductFlowEvent(event, packageName, date, uuid, product, PRODUCT_PUBLISHED.check(send));

            Collection<Pair<FlowLogEntity, IFlowProduct>> deletedTranslations = product.getDeletedTranslations();
            if (!deletedTranslations.isEmpty()) {
                SFTypeSet sendTo = TRANSLATION_DELETED.check(send);
                deletedTranslations.forEach(tr -> send(
                        tr.first, null, tr.first.getDate(), uuid, null, tr.second, sendTo));
            }
        }
        product.getTranslations().forEach(tr -> {
            if (tr.isExisted()) {
                if (tr.isDeleted()) {
                    onProductFlowEvent(event, packageName, date, uuid, tr, TRANSLATION_RETRACTED.check(send));
                } else {
                    tr.setState(FlowProduct.State.PUBLISHED);
                    onProductFlowEvent(event, packageName, date, uuid, tr, TRANSLATION_PUBLISHED.check(send));
                }
            }
        });
    }


    private void onFlowEnd(String packageName, Date date, IFlowProduct product, boolean dashboard) {
        FlowProduct.State state = product.getFlowState();
        switch (state) {
            case RECEIVED:
                onProductNotified(ILogEvent.PRODUCT_NOTIFIED_ON_RECEIVED, date, null,
                        FlowProduct.State.RECEIVED, product, toFlow(dashboard));
                break;
            case PUBLISHED:
                String notifiedDate = product.getOnlineDate() != null ? product.getOnlineDate()
                        : product.getFirstOnlineDate();
                onProductNotified(ILogEvent.PRODUCT_NOTIFIED_ON_PUBLISHED, date, notifiedDate,
                        FlowProduct.State.PUBLISHED, product, toFlow(dashboard));
                break;
            case DELETED:
                onProductNotified(ILogEvent.PRODUCT_NOTIFIED_ON_PUBLISHED, date, null,
                        FlowProduct.State.DELETED, product, toFlow(dashboard));
                break;
            default:
                break;
        }
    }

    @Override
    public void onProductPackageError(int event, String packageName, String cdNumber, String errMsg, boolean active,
                                      boolean dashboard, boolean spd) {
        if (dashboard) {
            IKibanaRecord kr = spd && !active ? recordCache.getKibanaRecord(cdNumber, spd)
                    : recordCache.getKibanaRecord(cdNumber);
            if (kr != null) {
                synchronized (kr) {
                    logDashboardEvent(event, null, kr, packageName, kr.getFlowProduct().getTransactionId(), errMsg,
                            kr.getFlowProduct());
                }
            }
        }
    }

    @Override
    public void onProductError(int event, String packageName, String cdNumber, int pubNumber, String err,
                               boolean dashboard) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, false);
        String publisherId = RecordHelper.buildPubName(cdNumber, pubNumber);

        if (kr == null || !kr.getPubName().equals(publisherId)) {
            kr = recordCache.getKibanaRecord(cdNumber, true);
            if (kr == null || !kr.getPubName().equals(publisherId)) {
                return;
            }
        }
        synchronized (kr) {
            if (dashboard) {
                KibanaUtil.logKibanaRecEvent(kr, KibanaUtil.Event.ERROR, err);
            }
            onProductFlowError(event, packageName, new Date(), err, null, kr.getFlowProduct(), toFlow(dashboard), true);
        }
    }

    @Override
    public void onProductError(int event, Integer packageId, String cdNumber, String language, String err,
                               boolean lastEvent, boolean dashboard, boolean spd) {
        onProductError(checkRecord(event, packageId, cdNumber, language, spd, dashboard),
                event, language, err, lastEvent, dashboard);
    }

    private void onProductError(IKibanaRecord kr, int event, String language, String error, boolean lastEvent,
                                boolean dashboard) {
        if (kr != null) {
            synchronized (kr) {
                if (dashboard) {
                    KibanaUtil.logKibanaRecEvent(kr, language, KibanaUtil.Event.ERROR, error);
                }
                IFlowProduct product = language != null ? kr.addLanguage(language) : kr.getFlowProduct();
                onProductFlowError(event, null, new Date(), null, error, product, toFlow(dashboard));
            }
        }
    }

    @Override
    public void onPackageError(int event, Integer packageId, String packageName, Collection<String> cdNumbers,
                               String err, boolean dashboard, boolean spd) {
        SFTypeSet to = toFlow(dashboard);
        Date date = new Date();
        for (String cdNumber: cdNumbers) {
            IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, spd);
            if (kr == null) {
                continue;
            }
            synchronized (kr) {
                if (dashboard) {
                    KibanaUtil.logKibanaRecEvent(kr, KibanaUtil.Event.ERROR, err);
                }
                onProductFlowError(event, packageName, date, null, err, kr.getFlowProduct(), to, true);
            }
        }
        recordCache.removeRecords(cdNumbers, spd);
    }

    @Override
    public void updateProduct(ArchieEntry entry, String stage, Integer statusId, String sid, String publicationType,
                              String spdDate, int highPriority) {
        boolean spd = spdDate != null;
        IKibanaRecord kr = recordCache.getKibanaRecord(entry.getName(), spd);
        if (kr != null) {
            synchronized (kr) {
                IFlowProduct product = updateRecord(kr, entry, stage, statusId, sid, spdDate, highPriority);
                if (product != null && publicationType != null) {
                    product.setPublicationType(publicationType);
                }
            }
        }
    }

    private IFlowProduct updateRecord(IKibanaRecord kr, ArchieEntry entry, String stage, Integer statusId, String sid,
                                      String spdDate, int highPriority) {
        boolean spd = spdDate != null;
        if (stage != null) {
            kr.setStage(stage);
        }
        if (statusId != null) {
            kr.setStatus(statusId);
        }
        if (entry.getEnglishTitle() != null) {
            kr.setTitle(entry.getEnglishTitle());   // kibana uses an english title for translations
        }
        IFlowProduct product = kr.getFlowProduct(entry.getLanguage());
        if (product != null) {
            product.setHighPriority(highPriority);

            if (entry.getTitle() != null) {
                product.setTitle(CmsUtils.unescapeEntities(entry.getTitle()));
            }
            if (sid != null) {
                product.setSID(sid);
            }
            if (entry.getCochraneVersion() != null) {
                product.setCochraneVersion(entry.getCochraneVersion());
            }
            if (spd) {
                product.setSPDDate(spdDate);
            }
        }
        return product;
    }

    @Override
    public void onFlowCompleted(Collection<String> cdNumbers) {
        cdNumbers.forEach(cdNumber -> onFlowCompleted(cdNumber, false));
    }

    @Override
    public void onFlowCompleted(String cdNumber, boolean offline) {
        if (offline) {
            recordCache.removeRecord(cdNumber, true);
            return;
        }
        IKibanaRecord kr = recordCache.removeRecord(cdNumber);
        if (kr != null) {
            synchronized (kr) {
                FlowProduct flowProduct = kr.getFlowProduct();
                if (!flowProduct.wasCompleted() && flowProduct.wasPublished()) {
                    recordCache.checkPostRecord(cdNumber, kr);
                }
            }
        }
    }

    @Override
    public void onFlowCompleted(String cdNumber, String language, boolean spd) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, spd);
        if (kr != null) {
            synchronized (kr) {
                removeRecord(kr, language, spd);
            }
        }
    }

    @Override
    public void onDashboardEventStart(KibanaUtil.Event event, FlowProduct.State state, String transactionId,
                                      Integer packageId, String cdNumber, String language, boolean spd) {
        IKibanaRecord kr = checkRecord(event.getFlowLogEvent(), packageId, cdNumber, language, spd, true);
        if (kr != null) {
            synchronized (kr) {
                recordCache.addKibanaTransaction(transactionId,
                        KibanaUtil.buildLogKibanaRecEvent(kr, language, event, null), cdNumber);
                kr.getFlowProduct().setFlowAndProductState(state, language);
            }
        }
    }

    private IKibanaRecord checkRecord(int event, Integer packageId, String cdNumber, String language, boolean spd,
                                      boolean dashboard) {
        IKibanaRecord kr = null;
        if (language == null && packageId != null && (event == ILogEvent.PRODUCT_VALIDATED
                || event == ILogEvent.PRODUCT_UNPACKED || event == ILogEvent.PRODUCT_NOTIFIED_ON_RECEIVED)) {
            kr = recordCache.getPreRecord(packageId, cdNumber);
            if (kr != null && !kr.getFlowProduct().isExisted())  {
                // this 'pre'-record has not been logged on first receive yet
                IFlowProduct flowProduct = kr.getFlowProduct();
                flowProduct.setState(FlowProduct.State.onReceived(false));
                flowProduct.setTransactionId(getTransactionId(ILogEvent.PRODUCT_RECEIVED, kr.getDfId()));
                onProductFlowEvent(ILogEvent.PRODUCT_RECEIVED, flowProduct.getPackageName(), new Date(),
                        flowProduct.getTransactionId(), flowProduct, toFlow(dashboard));
            }
        }
        return kr == null ? recordCache.getKibanaRecord(cdNumber, spd) : kr;
    }

    @Override
    public void onDashboardEventEnd(String transactionId, Integer packageId, boolean success, boolean spd) {
        Pair<List<String>, Set<String>> records = recordCache.removeKibanaTransaction(transactionId);
        if (success && records != null) {

            records.first.forEach(r -> KibanaUtil.logKibanaRecEvent(r, spd));
            Date date = new Date();
            for (String cdNumber: records.second) {
                IKibanaRecord kr = packageId != null ? recordCache.checkPreRecord(packageId, cdNumber, null) : null;
                kr = kr == null ? recordCache.getKibanaRecord(cdNumber, spd) : kr;
                if (kr != null) {
                    synchronized (kr) {
                        onFlowEnd(kr.getDfName(), date, kr.getFlowProduct(), true);
                    }
                }
            }
        }
    }

    @Override
    public void onDashboardEvent(KibanaUtil.Event event, String packageName, Date date,
                                 PublishedAbstractEntity product) {
        KibanaUtil.logKibanaRecPublishRepeatEvent(product, event);
        onProductFlowEvent(event.getFlowLogEvent(), packageName, date, findTransactionId(product.getSourcePackageId()),
                product, toFlow(true));
    }

    @Override
    public String findTransactionId(Integer entityId) {
        UUIDEntity sidEntity = sidManager.findUUIDEntity(ILogEvent.PRODUCT_RECEIVED, entityId);
        return sidEntity == null ? null : sidEntity.getSid();

    }

    @Override
    public String findTransactionId(String cdNumber) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber);
        return kr != null ? kr.getFlowProduct().getTransactionId() : null;
    }

    @Override
    public String getTransactionId(int event, Integer entityId) {
        UUIDEntity sidEntity = sidManager.findUUIDEntity(event, entityId);
        if (sidEntity == null) {
            sidEntity = sidManager.createUUIDEntity(event, entityId);
        }
        return sidEntity.getSid();
    }

    private void removeRecord(IKibanaRecord kr, String language, boolean spd) {
        if (language != null) {
             kr.getLanguages().remove(language);
        } else {
            kr.setStatus(Constants.UNDEF);   
        }
        if (!kr.hasLanguages() && !kr.hasReview()) {
            recordCache.removeRecord(kr.getName(), spd);
        }
    }

    private void onPackageFlowEvent(int event, String packageName, @NotNull Date date, @NotNull IFlowProduct product,
                                    @NotNull SFTypeSet to) {
        //product.setEventRecords(recordCount, totalCount);
        onProductFlowEvent(event, packageName, date, product.getTransactionId(), product, to);
    }

    private void onPackageFlowEventErr(int event, String packageName, @NotNull Date date, @NotNull IFlowProduct product,
                                       String err, @NotNull SFTypeSet to) {
        if (err != null && !err.isEmpty()) {
            onProductFlowError(event, packageName, date, product.getTransactionId(), err, product, to);
        }
    }

    private FlowLogEntity onProductFlowEvent(int event, String packageName, @NotNull Date date, String uuid,
                                             @NotNull IFlowProduct product, @NotNull SFTypeSet to) {
        FlowLogEntity flowLog  = activityLog.logDeliveryFlow(ActivityLogEntity.LogLevel.INFO, event,
                product.getSourcePackageId(), packageName, date, product, to.toSend);
        send(flowLog, packageName, date, uuid, null, product, to);
        return flowLog;
    }

    private void send(FlowLogEntity flowLog, String packageName, @NotNull Date date, String uuid, String err,
                      @NotNull IFlowProduct product, @NotNull SFTypeSet to) {
        to.sfTypes.forEach(type -> send(type.createEvent(flowLog, packageName, err, product, to.state, date, uuid)));
    }

    private void send(SFEvent event) {
        if (kafkaProducer.isAsyncMode()) {
            try {
                jmsCreator.eventHolder[0] = event;
                JMSSender.send(connectionFactory, kafkaQueue, jmsCreator);
            } catch (Throwable tr) {
                LOG.error(tr);
            }
        } else {
            kafkaProducer.produceMessage(event);
        }
    }

    private void onProductFlowError(int event, String packageName, Date date, String uuid, String err,
                                    IFlowProduct product, SFTypeSet to) {
        FlowLogEntity flowLog = activityLog.logDeliveryFlowError(event, product.getSourcePackageId(), packageName,
                date, err, product, to.toSend);
        send(flowLog, packageName, date, product.getTransactionId(), err, product, to);
    }

    private void onProductFlowEvent(int event, String packageName, Date date, String uuid, IFlowProduct product,
                                    SFTypeSet to, boolean withTa) {
        if (product.isExisted()) {
            onProductFlowEvent(event, packageName, date, uuid, product, to);
        }
        if (withTa) {
            product.getTranslations().forEach(tr -> onProductFlowEvent(event, packageName, date, uuid, tr, to, false));
        }
    }

    private void onProductFlowError(int event, String packageName, Date date, String uuid, String err,
                                    IFlowProduct product, SFTypeSet to, boolean withTa) {
        if (product.isExisted()) {
            onProductFlowError(event, packageName, date, uuid, err, product, to);
        }
        if (withTa) {
            product.getTranslations().forEach(
                    tr -> onProductFlowError(event, packageName, date, uuid, err, tr, to, false));
        }
    }

    private void onProductNotified(int event, Date date, String notifiedPublishedDate, FlowProduct.State state,
                                   IFlowProduct product, SFTypeSet to) {
        if (state == product.getState()) {
            product.setFirstOnlineDate(null);  // it is not set yet or not required anymore
            product.setOnlineDate(notifiedPublishedDate);
//          XDPS-2536 - event should present in Kafka notifications
//            boolean shouldCallMethod = !(event == ILogEvent.PRODUCT_NOTIFIED_ON_RECEIVED
//                    && CochraneCMSPropertyNames.isCochraneSftpPublicationNotificationFlowEnabled());
//            if (shouldCallMethod) {
                onProductFlowEvent(event, null, date, product.getTransactionId(), product, to);
//            }
        }
        product.getTranslations().forEach(tr -> onProductNotified(event, date, notifiedPublishedDate, state, tr, to));
    }

    private void onProductCreated(Date date, boolean isNewDoi, IFlowProduct product, boolean send) {
        if (product.isExisted()) {
            onProductFlowEvent(ILogEvent.PRODUCT_SAVED, null, date, product.getTransactionId(), product,
                    FLOW.check(send));
            if (isNewDoi) {
                onProductFlowEvent(ILogEvent.PRODUCT_CREATED, null, date, product.getTransactionId(), product,
                        PRODUCT_CREATED.check(send));
            }
        }
        product.getTranslations().forEach(tr -> onProductCreated(date, false, tr, send));
    }

    private IKibanaRecord createRecord(String packageFileName, Integer packageId, String cdNumber, int pubNumber,
                                       ArchieEntry entry, String vendor, String spdDate) {
        String initialPackageName = packageFileName;
        Integer initialPackageId = packageId;
        String initialVendor = vendor;
        if (entry != null && entry.wasReprocessed()) {
            PublishedAbstractEntity pe = sidManager.find(PublishedAbstractEntity.class, entry.getId());
            if (pe != null) {
                initialPackageId = pe.getInitialDeliveryId();
                DeliveryFileEntity de = sidManager.find(DeliveryFileEntity.class, initialPackageId);
                initialPackageName = de == null ? packageFileName : de.getName();
                initialVendor = de == null ? vendor : de.getVendorByType();
            }
        }
        IKibanaRecord kr = entry == null
                ? new KibanaArchieRecord(cdNumber, pubNumber, initialPackageName, initialPackageId, initialVendor)
                : new KibanaArchieRecord(entry, initialPackageName, initialPackageId, initialVendor);
        setSPDDate(kr, spdDate);
        return kr;
    }

    private IKibanaRecord createOnReceived(String packageFileName, Integer packageId, String cdNumber, int pubNumber,
                                           ArchieEntry entry, String vendor, String spdDate) {
        boolean spd = spdDate != null;
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, spd);
        if (kr == null) {
            kr = recordCache.checkPreRecord(packageId, cdNumber, null);
            if (kr == null) {
                kr = createRecord(packageFileName, packageId, cdNumber, pubNumber, entry, vendor, spdDate);
            }
            setSPDDate(kr, spdDate);
            recordCache.putKibanaRecord(kr.getName(), kr, spd, spd);

        } else if (!packageId.equals(kr.getDfId())) {
            kr = null;
        }
        recordCache.checkPostRecord(cdNumber, null);
        return kr;
    }

    private void setSPDDate(IKibanaRecord kr, String spdDate) {
        if (spdDate != null) {
            IFlowProduct product = kr.getFlowProduct();
            product.setSPDDate(spdDate);
            product.sPD(true);
        }
    }

    private IKibanaRecord createOnCanceled(String packageFileName, Integer packageId, String cdNumber, int pubNumber,
                                           ArchieEntry entry, String vendor) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, true);
        if (kr == null) {
            kr = recordCache.checkPreRecord(packageId, cdNumber, null);
            if (kr == null) {
                kr = createRecord(packageFileName, packageId, cdNumber, pubNumber, entry, vendor, null);
            }
            recordCache.putKibanaRecord(kr.getName(), kr, true, false);

        } else {
            FlowProduct product = kr.getFlowProduct();
            product.setPackageName(packageFileName);
            product.setPackageId(packageId);
        }
        kr.getFlowProduct().sPD(false);
        return kr;
    }

    private void onProductEvent(KibanaUtil.Event event, String cdNumber, boolean dashboard, boolean spd) {
        IKibanaRecord kr = recordCache.getKibanaRecord(cdNumber, spd);
        if (kr != null) {
            synchronized (kr) {
                if (dashboard) {
                    KibanaUtil.logKibanaRecEvent(kr, event, null);
                }
                onProductFlowEvent(event.getFlowLogEvent(), kr.getDfName(), new Date(),
                        kr.getFlowProduct().getTransactionId(), kr.getFlowProduct(), toFlow(dashboard), true);
            }
        }
    }

    private void onReceived(IKibanaRecord kr, String packageName, String language, ArchieEntry ae, boolean dashboard,
                            boolean offline) {
        if (kr == null) {
            return;
        }
        synchronized (kr) {
            IFlowProduct flowProduct;
            if (language != null) {
                if (ae != null) {
                    flowProduct = kr.getFlowProduct().addTranslation(ae.getDoi(), language, ae.isDeleted());
                    flowProduct.setSID(ae.getSid());
                } else {
                    flowProduct = kr.addLanguage(language);
                }
            } else {
                flowProduct = kr.getFlowProduct();
                if (ae != null) {
                    flowProduct.setSID(ae.getManuscriptNumber());
                }
            }
            flowProduct.setState(FlowProduct.State.onReceived(offline));
            if (dashboard) {
                KibanaUtil.logKibanaRecEvent(kr, language, KibanaUtil.Event.RECEIVED, null);
            }
            kr.getFlowProduct().setTransactionId(getTransactionId(ILogEvent.PRODUCT_RECEIVED, kr.getDfId()));
            if (ae == null || !ae.wasReprocessed()) {
                onProductFlowEvent(ILogEvent.PRODUCT_RECEIVED, packageName, new Date(),
                        kr.getFlowProduct().getTransactionId(), flowProduct, toFlow(dashboard));
            }
        }
    }

    private SFTypeSet toFlow(boolean dashboard) {
        return FLOW.check(send(dashboard));
    }

    private boolean send(boolean dashboard) {
        return dashboard && snowFlake();
    }

    private boolean snowFlake() {
        return snowFlake.get().asBoolean();
    }

    private static class SFTypeSet {

        final int toSend;
        final List<SFType> sfTypes;
        final FlowProduct.State state;

        SFTypeSet(boolean flow) {
            if (flow) {
                toSend = 1;
                sfTypes = Collections.singletonList(SFType.FLOW);
            } else {
                toSend = 0;
                sfTypes = Collections.emptyList();
            }
            state = null;
        }

        SFTypeSet(boolean flow, boolean product, boolean ta, boolean productPackage, FlowProduct.State state) {
            sfTypes = new ArrayList<>();
            toSend = makeSendTo(flow, product, ta, productPackage, sfTypes);
            this.state = state;
        }

        SFTypeSet check(boolean send) {
            return send ? this : EMPTY;
        }

        static int makeSendTo(boolean flow, boolean product, boolean translation, boolean productPackage,
                              Collection<SFType> sfTypes) {
            int ret = 0;
            if (flow) {
                sfTypes.add(SFType.FLOW);
                ret = 1;
            }
            if (product) {
                ret = BitValue.setBit(SFType.PRODUCT.ordinal(), ret);
                sfTypes.add(SFType.PRODUCT);
            }
            if (translation) {
                ret = BitValue.setBit(SFType.TRANSLATION.ordinal(), ret);
                sfTypes.add(SFType.TRANSLATION);
            }
            if (productPackage)  {
                ret = BitValue.setBit(SFType.PACKAGE_PRODUCT.ordinal(), ret);
                sfTypes.add(SFType.PACKAGE_PRODUCT);
            }
            return ret;
        }
    }

    private static class MessageCreator implements JMSSender.MessageCreator {
        SFEvent[] eventHolder = {null};
        @Override
        public Message createMessage(Session session) throws JMSException {
            return session.createObjectMessage(eventHolder[0]);
        }
    }
}
