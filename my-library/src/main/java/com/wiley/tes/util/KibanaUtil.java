package com.wiley.tes.util;

import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.FlowEventProduct;
import com.wiley.cms.cochrane.activitylog.FlowLogEntity;
import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.IFlowProduct;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.activitylog.LogEntity;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IKibanaRecord;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.KibanaArchieRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.entity.DbEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * @version 1.2
 * @since 30.04.2020
 */
public final class KibanaUtil {
    private static final Logger LOG = Logger.getLogger(KibanaUtil.class);
    private static final Logger LOG_SPD = Logger.getLogger("SPD");

    private static final int TEMPLATE_SIZE = 13;
    private static final String TEMPLATE_EMPTY_CELL = "[%s]";
    private static final String LOG_TEMPLATE = getLogTemplate();

    private KibanaUtil() {
    }

    public static IKibanaRecord getKibanaRecord(BaseType bt, RecordEntity re, List<PublishedAbstractEntity> unpublished,
                                                IPublishStorage ps, IResultsStorage rs) {
        RecordMetadataEntity rme = re.getMetadata();
        if (rme == null) {
            return null;
        }

        int recordNumber = rme.getVersion().getNumber();
        boolean spdCanceled = re.isPublishingCancelled();

        IKibanaRecord kr = setStatusAndLanguages(re, recordNumber, rme, unpublished, spdCanceled);
        if (kr == null) {
            if (spdCanceled) {
                kr = setStatusAndLanguages(re, recordNumber, rme, unpublished, false);

            } else if (!re.getDeliveryFile().isAriesSFTP() && bt.isCCA())  {
                boolean unknownFlowId = DeliveryPackage.isPropertyUpdate(re.getDeliveryFile().getName());

                PublishedAbstractEntity pae = new PublishedAbstractEntity();
                pae.setId(DbEntity.NOT_EXIST_ID);
                pae.setDeliveryId(re.getDeliveryFileId());
                pae.setInitialDeliveryId(unknownFlowId ? findInitialPackageId(bt, re)
                        : re.getDeliveryFileId());
                pae.setRecordName(re.getName());
                pae.setNumber(bt.getProductType().buildRecordNumber(re.getName()));
                pae.setPubNumber(Constants.FIRST_PUB);
                pae.setVersion(ArchieEntry.NONE_VERSION);

                kr = setStatusAndLanguages(re, recordNumber, rme, Collections.singletonList(pae), false);
            }
            if (kr != null) {
                recoverKibanaRecord(bt, kr, recordNumber, rme, ps, rs);
            }
        } else {
            recoverKibanaRecord(bt, kr, recordNumber, rme, ps, rs);
        }
        return kr;
    }

    private static void recoverKibanaRecord(BaseType bt, IKibanaRecord kr, int recordNumber, IRecord rme,
                                            IPublishStorage ps, IResultsStorage rs) {
        List<FlowLogEntity> list = ActivityLogFactory.getFactory().getInstance().getFlowLogs(kr.getDfId());
        Integer dfId = kr.getDfId();

        if (ps != null) {
            setPublishNames(ps, rme.getPubNumber(), dfId, recordNumber, kr);
        }

        FlowProduct mainProduct = kr.getFlowProduct();
        mainProduct.setTransactionId(CochraneCMSBeans.getFlowLogger().findTransactionId(dfId));

        String pubName = mainProduct.getPubCode();

        Set<String> errors = null;
        for (FlowLogEntity fe: list) {
            if (fe.getEntityName() != null && fe.getEntityName().startsWith(pubName)) {
                errors = updateState(mainProduct, fe, errors, rs);
            }
        }
        if (errors != null) {
            // remove invalid languages
            errors.forEach(language -> kr.getLanguages().remove(language));
        }
    }

    private static void setState(FlowProduct mainProduct, FlowEventProduct product, String language) {
        if (product.isPublished()) {
            mainProduct.setFlowState(product.getState());
        }
        mainProduct.setState(product.getState());
    }

    private static Set<String> updateState(FlowProduct mainProduct, FlowLogEntity fe, Set<String> errors,
                                           IResultsStorage rs) {
        if (fe.getDbType() == DatabaseEntity.CDSR_KEY || fe.getDbType() == DatabaseEntity.EDITORIAL_KEY
                || fe.getDbType() == DatabaseEntity.CCA_KEY) {
            if (!mainProduct.isExisted()) {
                FlowEventProduct product = new FlowEventProduct(fe);
                setState(mainProduct, product, null);

                //mainProduct.setState(product.getState());
                mainProduct.setEntityId(product.getEntityId());
                mainProduct.setPublicationType(product.getPublicationType());
                mainProduct.setFirstOnlineDate(product.getFirstOnlineDate());
                mainProduct.setOnlineDate(product.getOnlineDate());
                mainProduct.setSPDDate(product.getSPDDate());
                mainProduct.setTitleId(product.getTitleId(),
                        rs != null ? rs.getRecordTitle(mainProduct.getTitleId()) : null);
                mainProduct.setHighPriority(product.getHighPriority());

            } else if (fe.getEvent().getId() == ILogEvent.PRODUCT_CREATED && !mainProduct.isPublished()) {
                mainProduct.setState(new FlowEventProduct(fe).getState());
            }

        } else if (fe.getDbType() == DatabaseEntity.CDSR_TA_KEY) {
            return updateTranslationState(mainProduct, fe, errors, rs);
        }
        return errors;
    }

    private static Set<String> updateTranslationState(FlowProduct mainProduct, FlowLogEntity fe, Set<String> errors,
                                                      IResultsStorage rs) {
        Set<String> ret = errors;
        FlowEventProduct product = new FlowEventProduct(fe);
        String language = product.getLanguage();
        IFlowProduct tr = mainProduct.getTranslation(language);

        if (!product.isExisted()) {
            if (ret == null) {
                ret = new HashSet<>();
            }
            ret.add(language);
            return ret;
        }

        if (tr == null) {
            if (product.isDeleted()) {
                tr = mainProduct.createTranslation(product.getDOI(), language, false);
                mainProduct.addDeletedTranslation(fe, tr);
                tr.setState(product.getState());
            }
        } else {
            if ((product.isRetracted() && !tr.isRetracted()) || product.isDeleted() && !tr.isDeleted()) {
                IFlowProduct prev = tr;
                tr = mainProduct.addTranslation(product.getDOI(), language, product.isRetracted());
                tr.setState(prev.getState());
                tr.setEntityId(prev.getEntityId());
                tr.setSID(prev.getSID());
                tr.setCochraneVersion(prev.getCochraneVersion());
            }
            if (!tr.isExisted()) {
                tr.setState(product.getState());
                tr.setEntityId(product.getEntityId());
            }
            if (product.getTitleId() != null) {
                tr.setTitleId(product.getTitleId());
                tr.setTitle(rs != null ? rs.getRecordTitle(product.getTitleId()) : null);
            }
        }
        if (!mainProduct.isExisted() && product.isPublished()) {
            mainProduct.setFlowState(product.getState());
        }
        return ret;
    }

    private static IKibanaRecord setStatusAndLanguages(RecordEntity re, int recNumber, RecordMetadataEntity rme,
                                                       List<PublishedAbstractEntity> unpublished, boolean byLastDf) {
        IKibanaRecord kr = null;
        Integer curDfId = re.getDeliveryFileId();
        for (PublishedAbstractEntity pae: unpublished) {
            if (pae.getNumber() != recNumber || pae.getPubNumber() != rme.getVersion().getPubNumber()) {
                continue;
            }
            if (kr == null) {
                Integer dfId = byLastDf ? pae.getDeliveryId() : pae.getInitialDeliveryId();
                DeliveryFileEntity dfe = curDfId.equals(dfId) ? re.getDeliveryFile()
                     : ResultStorageFactory.getFactory().getInstance().getDeliveryFileEntity(dfId);
                if (dfe == null) {
                    continue;
                }
                kr = new KibanaArchieRecord(re, rme, dfe);
            }
            IFlowProduct product;
            if (!pae.hasLanguage()) {
                kr.setStatus(rme.getStatus());
                product = kr.getFlowProduct();
                product.setSID(pae.getManuscriptNumber());
                if (pae.sPD().is()) {
                    product.sPD(pae.sPD().on());
                }
            } else  {
                product = kr.addLanguage(pae.getLanguage());
                product.setSID(pae.getSID());
            }
            product.setCochraneVersion(pae.getVersion());
        }
        return kr;
    }

    private static Integer findInitialPackageId(BaseType bt, RecordEntity re) {
        FlowLogEntity lastFlowLog = ActivityLogFactory.getFactory().getInstance().findLastFlowLog(re.getName(),
            bt.getDbId(), Now.convertToDate(LocalDateTime.now().minusMonths(
                 CochraneCMSPropertyNames.getAmountOfLastActualMonths())));
        if (lastFlowLog == null || (lastFlowLog.getId() == ILogEvent.PRODUCT_SENT_DS
                && lastFlowLog.getLogLevel() != LogEntity.LogLevel.ERROR)) {
            return re.getDeliveryFileId();
        }

        DeliveryFileEntity de = ResultStorageFactory.getFactory().getInstance().getDeliveryFileEntity(
                lastFlowLog.getPackageId());
        return de == null ? re.getDeliveryFileId() : de.getId();
    }

    private static void setPublishNames(IPublishStorage ps, int pubNum, int dfId, int recNum, IKibanaRecord kr) {
        List<PublishRecordEntity> pubRecEntities = ps.findPublishByRecAndPubAndDfId(recNum, pubNum, dfId);
        pubRecEntities.stream().map(PublishRecordEntity::getPublishPacket)
                                .forEach(publishPackage -> setPublishNames(publishPackage.getFileName(),
                                        publishPackage.getPublishType(), kr));
    }

    public static Event getPublishedEvent(PublishDestination dest, boolean offline) {
        return PublishDestination.WOLLIT == dest ? KibanaUtil.Event.PUBLISHED_IN_LIT
            : (PublishDestination.SEMANTICO == dest ? (offline ? Event.OFFLINE_IN_HW : Event.PUBLISHED_IN_HW) : null);
    }

    public static Event setPublishNames(String publishName, Integer publishType, IKibanaRecord kr) {
        Event event = null;
        if (PubType.LITERATUM_DB_TYPES.contains(publishType)) {
            kr.setLitPackageName(publishName);
            event = Event.SENT_TO_LIT;
        } else if (PubType.SEMANTICO_DB_TYPES.contains(publishType)) {
            // avoid repeated setting due to PDF re-sending to HW
            if (!kr.hasHwPackageName() || PublishDestination.SEMANTICO.getWhenReadyTypeId() == publishType) {
                kr.setHwPackageName(publishName);
                event = Event.SENT_TO_HW;
            }
        } else if (PubType.DS_DB_TYPES.contains(publishType)) {
            kr.setDsPackageName(publishName);
            event = Event.SENT_TO_DS;

        } else if (PubType.TYPES_ARIES_ACK_D.contains(publishType)) {
            event = Event.ACK_ON_DELIVERY;

        } else if (PubType.TYPES_ARIES_ACK_P.contains(publishType)) {
            event = Event.ACK_ON_PUBLISH;

        } else {
            LOG.warn(String.format("%s - publish type %d not found", kr, publishType));
        }
        return event;
    }

    public static void logKibanaRecEvent(IKibanaRecord kr, Event event, String msg) {
        if (kr == null) {
            return;
        }

        if (kr.hasReview()) {
            logKibanaRecEvent(kr, null, event, msg);
        }

        boolean withoutTAs = (event == Event.SENT_TO_LIT || event == Event.PUBLISHED_IN_LIT);
        if (!withoutTAs) {
            kr.getLanguages().forEach(lang -> logKibanaRecEvent(kr, lang, event, msg));
        }
    }

    public static void logKibanaRecEvent(IKibanaRecord kr, String lang, Event event, String msg) {
        int dbType = kr.getFlowProduct().getDbType();
        if (dbType == DatabaseEntity.CDSR_KEY || dbType == DatabaseEntity.CDSR_TA_KEY) {
            logKibanaRecEvent(buildLogKibanaRecEvent(kr, lang, event, msg), kr.getFlowProduct().sPD().is());
        }
    }

    public static void logKibanaRecPublishRepeatEvent(PublishedAbstractEntity pae, Event event) {
        int dbType = pae.getDbType();
        if (dbType == DatabaseEntity.CDSR_KEY || dbType == DatabaseEntity.CDSR_TA_KEY) {
            logKibanaRecEvent(buildLogKibanaRecCroppedEvent(pae, event), pae.sPD().is());
        }
    }

    public static String buildLogKibanaRecEvent(IKibanaRecord kr, String lang, Event event, String msg) {
        String normalizedMsg = msg != null ? msg.replaceAll("[\\r\\n]+", " ").trim() : "";
        return String.format(LOG_TEMPLATE,
                 lang != null ? FilePathBuilder.buildTAName(lang, kr.getPubName()) : kr.getPubName(),
                 kr.getStage(),
                 lang != null ? Constants.TR : kr.getStatus(),
                 event,
                 kr.getDfName(),
                 kr.getDfId(),
                 lang != null ? "" : kr.getLitPackageName(),
                 kr.getHwPackageName(),
                 kr.getDsPackageName(),
                 kr.getTitle(),
                 kr.getVendor(),
                 event == Event.ERROR ? normalizedMsg : "",
                 event == Event.DELAY ? normalizedMsg : "");
    }

    private static String buildLogKibanaRecCroppedEvent(PublishedAbstractEntity pae, Event event) {
        String pubName = RevmanMetadataHelper.buildPubName(pae.getRecordName(), pae.getPubNumber());
        return String.format(LOG_TEMPLATE,
                 pae.hasLanguage() ? FilePathBuilder.buildTAName(pae.getLanguage(), pubName) : pubName,
                 "", "", event, "", pae.getDeliveryId(), "", "", "", "", "", "", "");
    }

    public static void logKibanaRecEvent(String loggedEvent, boolean spd) {
        if (spd) {
            LOG_SPD.info(loggedEvent);
        } else {
            LOG.info(loggedEvent);
        }
    }

    public static boolean addKibanaLogging(BaseType bt, Integer publishTypeId) {
        return (!bt.isCentral())
            && (PubType.SEMANTICO_RT_TYPES.contains(publishTypeId) || PubType.LITERATUM_RT_TYPES.contains(publishTypeId)
                || PubType.DS_DB_TYPES.contains(publishTypeId) || PubType.TYPES_ARIES_ACK_D.contains(publishTypeId)
                || PubType.TYPES_ARIES_ACK_P.contains(publishTypeId));
    }

    private static String getLogTemplate() {
        return IntStream.range(0, TEMPLATE_SIZE).mapToObj(i -> TEMPLATE_EMPTY_CELL).collect(Collectors.joining());
    }

    /**
     * Events with Kibana Records
     */
    public enum Event {
        RECEIVED (ILogEvent.PRODUCT_RECEIVED),
        VALIDATED (ILogEvent.PRODUCT_VALIDATED),
        NOTIFY_ARCHIE_RECEIVED (ILogEvent.PRODUCT_NOTIFIED_ON_RECEIVED),
        UNPACKED (ILogEvent.PRODUCT_UNPACKED),
        CONVERTED (ILogEvent.PRODUCT_CONVERTED),
        RENDERED (ILogEvent.PRODUCT_RENDERED),
        UPLOADED_TO_DB (ILogEvent.PRODUCT_SAVED),
        PUBLISHING_STARTED (ILogEvent.PRODUCT_PUBLISHING_STARTED),

        SENT_TO_LIT (ILogEvent.PRODUCT_SENT_WOLLIT) {
            @Override
            public void logFlow(IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                flowLogger.onProductFlowEvent(event, kr.getLitPackageName(), date, uuid, kr.getFlowProduct(), false);
            }
        },
        PUBLISHED_IN_LIT (ILogEvent.PRODUCT_PUBLISHED_WOLLIT) {
            @Override
            public void logFlow(IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                flowLogger.onProductFlowEvent(event, kr.getLitPackageName(), date, uuid, kr.getFlowProduct(), false);
            }
        },
        SENT_TO_HW (ILogEvent.PRODUCT_SENT_HW) {
            @Override
            public void logFlow(IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                flowLogger.onProductFlowEvent(event, kr.getHwPackageName(), date, uuid, kr.getFlowProduct(), true);
            }
        },
        PUBLISHED_IN_HW (ILogEvent.PRODUCT_PUBLISHED_HW) {
            @Override
            public void logFlow(IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                flowLogger.onProductPublished(event, kr.getHwPackageName(), date,
                        kr.getFlowProduct().getFirstOnlineDate(), kr.getFlowProduct());
            }
        },
        OFFLINE_IN_HW (ILogEvent.PRODUCT_OFFLINE_HW) {
            @Override
            public void logFlow(IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                flowLogger.onProductPublished(event, kr.getHwPackageName(), date,
                        kr.getFlowProduct().getFirstOnlineDate(), kr.getFlowProduct());
            }
        },

        NOTIFY_PUBLISHED(ILogEvent.PRODUCT_NOTIFIED_ON_PUBLISHED),

        SENT_TO_DS (ILogEvent.PRODUCT_SENT_DS) {
            @Override
            public void logFlow(IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                kr.getFlowProduct().setFlowAndProductState(FlowProduct.State.PUBLISHED_DS, null);
                flowLogger.onProductFlowEvent(event, kr.getDsPackageName(), date, uuid, kr.getFlowProduct(), true);
            }
        },

        ERROR (ILogEvent.PRODUCT_ERROR),
        DELAY (DbEntity.NOT_EXIST_ID),

        ACK_ON_DELIVERY (ILogEvent.PRODUCT_ARIES_ACK_ON_RECEIVED) {
            @Override
            public void logFlow(IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                logFlow(kr.getDfName(), kr, date, flowLogger, uuid);
            }

            @Override
            public void logFlow(String packageName, IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                kr.getFlowProduct().setFlowAndProductState(FlowProduct.State.RECEIVED, null);
                flowLogger.onProductFlowEvent(event, packageName, date, uuid, kr.getFlowProduct(), true);
            }
        },

        ACK_ON_PUBLISH (ILogEvent.PRODUCT_ARIES_ACK_ON_PUBLISHED) {
            @Override
            public void logFlow(IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                logFlow(kr.getDfName(), kr, date, flowLogger, uuid);
            }

            @Override
            public void logFlow(String packageName, IKibanaRecord kr, Date date, IFlowLogger flowLogger, String uuid) {
                kr.getFlowProduct().setFlowAndProductState(FlowProduct.State.PUBLISHED, null);
                flowLogger.onProductFlowEvent(event, packageName, date, uuid, kr.getFlowProduct(), true);
            }
        };

        final int event;

        Event(int event) {
            this.event = event;
        }

        public void logFlow(IKibanaRecord record, Date date, IFlowLogger flowLogger, String uuid) {
        }

        public void logFlow(String packageName, IKibanaRecord record, Date date, IFlowLogger flowLogger, String uuid) {
            logFlow(record, date, flowLogger, uuid);
        }

        public int getFlowLogEvent() {
            return event;
        }
    }
}