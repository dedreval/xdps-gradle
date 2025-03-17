package com.wiley.cms.cochrane.activitylog;

import java.util.Collection;
import java.util.Date;

import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.activitylog.kafka.IKafkaMessageProducer;
import com.wiley.cms.cochrane.activitylog.snowflake.SFEvent;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.publish.PublishDestination;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.tes.util.KibanaUtil;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/15/2021
 */
public interface IFlowLogger {

    /**
     * Build a flow record about new product received and put it in cache as 'pre-record' without logging
     * @param packageName
     * @param packageId
     * @param entry        a container for new product metadata
     * @param vendor       a package vendor (aries | archie)
     * @param spdDate      NOt null for SPD record
     * @param highPriority High Publication Frequency and High Profile values
     */
    void receiveProduct(String packageName, Integer packageId, ArchieEntry entry,
                        String vendor, String spdDate, int highPriority);

    /**
     * Change existing 'pre' flow record state in cache as received and log it about new product received
     * @param packageName
     * @param packageId
     * @param cdNumber
     * @param pubNumber
     * @param vendor
     * @param spdDate
     * @param dashboard
     */
    void onProductReceived(String packageName, Integer packageId, String cdNumber, int pubNumber, String vendor,
                           String spdDate, boolean dashboard);

    void onProductReceived(String packageName, Integer packageId, ArchieEntry entry, String vendor,
                           String spdDate, boolean dashboard);

    void onPackageReceived(BaseType bt, String packageName, Integer packageId, String vendor, String errorMessage);

    void onProductCanceled(String packageName, Integer packageId, ArchieEntry entry, String vendor, boolean dashboard);

    /**
     * Must be called for CDSR, EDI, CCA databases as it takes the title ID from the entry.
     * @param entry
     * @param dashboard   If TRUE, a kafka message will be generated and sent. Currently it should be TRUE here only
     *                    for CDSR because CCA and EDI are validated later on the Wiley ML3G validation step
     * @param spd
     */
    void onProductValidated(ArchieEntry entry, boolean dashboard, boolean spd);

    /**
     * It's called for EDI and CCA databases on the Wiley ML3G validation step
     * @param cdNumber
     * @param dashboard  If TRUE, a kafka message will be generated and sent.
     * @param spd
     */
    void onProductValidated(String cdNumber, boolean dashboard, boolean spd);

    void onProductUnpacked(String packageName, Integer packageId, String cdNumber, String language,
                           boolean dashboard, boolean spd);

    void onPackageUnpacked(BaseType bt, String packageName, Integer packageId, String vendor, String title,
                           Integer recordCount, String errorMessage);

    void onProductConverted(String cdNumber, String publicationType, boolean dashboard);

    void onProductRendered(String cdNumber, boolean dashboard);

    void onProductSaved(String cdNumber, boolean newDoi, boolean dashboard, boolean spd);

    void onTranslationDeleted(String cdNumber, Collection<String> deletedTranslations, boolean dashboard);

    void onProductDeleted(String packageName, Integer packageId, String cdNumber, boolean dashboard, boolean spd);

    void onProductsPublishingStarted(Collection<String> cdNumbers, boolean dashboard, boolean spd);

    void onPackagePublishingStarted(BaseType bt, String packageName, Integer packageId, String vendor); //todo remove
        
    void onProductsPublishingSent(String publishFileName, Integer publishType, Integer packageId, String cdNumber,
                                  String transactionId, String errMessage, boolean spd);

    void onProductPublished(String cdNumber, PublishDestination dest, Date eventDate, String firstOnlineDate,
                            String onlineDate, boolean dashboard, boolean offline);

    /**
     * Finds unacknowledged Flow Log events and re-sent them to kafka
     * @return amount of reproduced Flow log events
     */
    int reproduceFlowLogEvents();

    /**
     * Marks acknowledged Snow Flake events as completed
     * @return completed Snow Flake events
     */
    @NotNull SFEvent[] completeFlowLogEvents();

    /**
     * Reproduces Flow Log event by its identifier to kafka
     * @param flowLogId  The Flow Log event identifier
     * @return  TRUE if the Flow Log event found and reproduced
     */
    boolean reproduceFlowLogEvent(Long flowLogId);

    IRecordCache getRecordCache();

    IActivityLog getActivityLog();

    IKafkaMessageProducer getKafkaProducer();

    IUUIDManager getUUIDManager();

    void onProductFlowEvent(int event, String packageName, Date date, String transactionId, IFlowProduct product,
                            boolean withTranslations);

    void onPackageFlowEvent(int event, BaseType bt, String packageName, Integer packageId, String vendor,
                            String transactionId, Integer... counters);

    void onPackageFlowEventError(int event, BaseType bt, String packageName, Integer packageId, String vendor,
                            String transactionId, String errorMessage, Integer... counters);

    /**
     * It is responsible for 'Acknowledged on Received' event and PackageProduct kafka messages.
     * Used for CCA and EDI content only.
     * @param date
     * @param product
     */
    void onProductAcknowledgedReceivedSent(Date date, IFlowProduct product);

    void onProductPublished(int event, String packageName, Date date, String firstOnlineDate, IFlowProduct product);

    void onProductError(int event, Integer packageId, String cdNumber, String language, String errMsg,
                        boolean lastEvent, boolean dashboard, boolean spd);

    void onProductPackageError(int event, String packageName, String cdNumber, String errMsg, boolean active,
                        boolean dashboard, boolean spd);

    void onProductError(int event, String packageName, String cdNumber, int pubNumber, String errMsg,
                        boolean dashboard);

    void onPackageError(int event, Integer packageId, String packageName, Collection<String> cdNumbers, String errMsg,
                        boolean dashboard, boolean spd);


    void onDashboardEventStart(KibanaUtil.Event event, FlowProduct.State state, String transactionId,
                               Integer packageId, String cdNumber, String language, boolean spd);

    void onDashboardEventEnd(String transactionId, Integer packageId, boolean success, boolean spd);

    void onDashboardEvent(KibanaUtil.Event event, String packageName, Date date, PublishedAbstractEntity product);

    void updateProduct(ArchieEntry entry, String stage, Integer statusId, String sid, String publicationType,
                       String spdDate, int highPriority);

    // for active flow
    void onFlowCompleted(Collection<String> cdNumbers);

    void onFlowCompleted(String cdNumber, boolean offline);

    void onFlowCompleted(String cdNumber, String language, boolean spd);

    String getTransactionId(int event, Integer entityId);

    String findTransactionId(Integer entityId);

    String findTransactionId(String cdNumber);
}
