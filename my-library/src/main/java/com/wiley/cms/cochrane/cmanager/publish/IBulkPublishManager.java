package com.wiley.cms.cochrane.cmanager.publish;

import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumSentConfirm;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 6/15/2016
 */
public interface IBulkPublishManager {

    PublishWrapper acceptLiteratumDelivery(LiteratumSentConfirm response, Date responseDate, List<Integer> publishIds,
                                           boolean hw, OpStats statsByDf);
}
