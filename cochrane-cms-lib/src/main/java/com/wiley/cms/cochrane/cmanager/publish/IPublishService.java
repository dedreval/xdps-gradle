package com.wiley.cms.cochrane.cmanager.publish;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumSentConfirm;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWClient;
import com.wiley.cms.cochrane.cmanager.publish.send.semantico.HWMsg;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IPublishService {

    /**
      * Check and accept Literatum response event if this event corresponds to the criteria specified:
      * 1. an event is CONTENT_ONLINE and its deliveryId is among publish entities that are waiting for WOLLIT events
      *    to be sent to HW (XDPS-1140) or
      * 2. specifically for CENTRAL CONTENT_ONLINE  HW events to be sent to DS (XDPS-1761) or
      * 3. specifically for CENTRAL CONTENT_OFFLINE HW events for withdrawn DOIs (XDPS-2089)
      * @param response       The response event
      * @param responseDate   The response date
      * @return  TRUE if the response event was accepted.
    */
    boolean acceptLiteratumDeliveryOnline(LiteratumSentConfirm response, Date responseDate);

    /**
     * Check and accept a Literatum response event if this event corresponds to the criteria specified:
     * the event is LOAD_TO_PUBLISH (XDPS-1143, XDPS-1761).
     * @param response       The response event
     * @param responseDate   The response date
     * @return  TRUE if the response event was accepted.
     */
    boolean acceptLiteratumDeliveryOnLoadToPublish(LiteratumSentConfirm response, Date responseDate);

    void publishDb(int dbId, List<PublishWrapper> publishList);

    void publishDbSync(int dbId, List<PublishWrapper> publishList);

    void publishEntireDb(String dbName, List<PublishWrapper> publishList);

    void publishEntireDbSync(String dbName, List<PublishWrapper> publishList);

    void publishWhenReady(BaseType baseType, DeliveryFileVO df, boolean mesh, boolean pu, boolean puPdf, boolean aries);

    void publishWhenReady(BaseType baseType, int dbId, int dfId, Set<String> pubTypes, String hwFrequency,
                          boolean generateNewUUID);

    void publishWhenReady(int dfId) throws Exception;

    void publishWhenReadyDS(String dbName, int dbId, int dfId, String cdNumber);

    void publishWhenReadyHW(String dbName, int dbId, Set<String> names, String hwFrequency);

    /**
     * Re-send an existing publishing package to HW if it has not been sent yet.
     * It's used for automatic re-sending on failure.
     * @param publishId    a HW publishing package identifier
     * @param hwPayLoad    an existing JSON payload for HW service (currently is is not used)
     */
    void sendWhenReadyHW(int publishId, String hwPayLoad);

    /**
     * Re-send an acknowledgement to Aries with the package that has already been generated
     * @param publishId   The identifier of the generated Aries acknowledgement package
     * @throws Exception
     */
    void sendAcknowledgementAries(int publishId) throws Exception;

    /**
     * Generate and send a When Ready article to Aries or HW
     * @param dbName      a database name
     * @param dbId        an identifier of Issue database
     * @param dfId        an identifier of the package for acknowledgement type: deliver | publish | cancel SPD
     * @param cdNumber    cd number
     * @param type        a delivery type: ack deliver | ack publish | semantico
     * @throws Exception
     */
    void publishWhenReadySync(String dbName, int dbId, int dfId, String cdNumber, String type, String uuid)
            throws Exception;

    /**
     * Send an acknowledgement on publication to Aries
     * @param dbName           a database name
     * @param clDbId           an identifier of Issue database
     * @param dfId             an identifier of the package for acknowledgement type: deliver | publish
     * @param manuscriptNumber a manuscript number
     * @throws Exception
     */
    void sendAcknowledgementAriesOnPublish(String dbName, int clDbId, int dfId, String manuscriptNumber,
                                           Integer whenReadyId, boolean generateNewUUID) throws Exception;

    /**
     * Try to send an acknowledgement on publication to Aries
     * @param wrEntity  When-Ready entity
     */
    void sendAcknowledgementAriesOnPublish(String dbName, PublishedAbstractEntity wrEntity);

    String notifySemantico(HWMsg msg, boolean deleteDois, boolean testMode, HWClient client) throws Exception;

    String notifySemantico(int publishId, String filename, String dbName, String frequency,
                           boolean disableStaticContent, boolean testMode, HWClient client) throws Exception;

    String notifySemantico(int publishId, String dbName, String frequency, Collection<String> deletedCdNumbers,
                           boolean testMode, HWClient client) throws Exception;
}