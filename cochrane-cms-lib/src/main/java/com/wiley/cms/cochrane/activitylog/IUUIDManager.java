package com.wiley.cms.cochrane.activitylog;

import java.util.List;

import com.wiley.cms.process.IModelController;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/14/2022
 */
public interface IUUIDManager extends IModelController {

    /**
     * It creates a new UUID on new initial package receiving or
     * publishing generation | sending in the following cases:
     * - UUID was pre-set as a template: 'UUID' (UI publishing)
     * - [DS only] UUID was pre-set, but the package was already sent (repeat sending)
     * - [DS only] UUID was not pre-set
     *   (UUID is pre-set on: generatePublishListForDSCentralAut, publishWhenReadyDS, publishWhenReady, CCA legacy)
     *
     * @param event     a flow log event
     * @param entityId  an identifier of an initial delivery package or a publishing package
     * @return
     */
    UUIDEntity createUUIDEntity(int event, Integer entityId);

    UUIDEntity findUUIDEntity(int event, Integer entityId);

    UUIDEntity findLastUUIDEntity(int event);

    List<UUIDEntity> findUUIDEntities(int event);
}
