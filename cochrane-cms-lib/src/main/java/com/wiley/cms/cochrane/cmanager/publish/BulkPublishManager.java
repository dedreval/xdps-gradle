package com.wiley.cms.cochrane.cmanager.publish;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.stats.OpStats;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumSentConfirm;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.tes.util.DbConstants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 6/15/2016
 */
@Stateless
@Local(IBulkPublishManager.class)
public class BulkPublishManager implements IBulkPublishManager {

    @EJB(beanName = "PublishStorage")
    private IPublishStorage ps;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public PublishWrapper acceptLiteratumDelivery(LiteratumSentConfirm response, Date responseDate,
                                                  List<Integer> publishIds, boolean hw, OpStats statsByDf) {
        List<PublishRecordEntity> all = ps.getPublishRecords(publishIds);
        PublishWrapper ret = null;
        if (!all.isEmpty()) {
            PublishEntity publishEntity = all.get(0).getPublishPacket();
            ClDbEntity db = publishEntity.getDb();
            BaseType bt = BaseType.find(db.getTitle()).get();
            if (!hw || !bt.isCentral()) {  // accept any WOLLIT or HW CDSR, EDI, CCA
                return hw ? null : acceptWOLLITDelivery(response, responseDate, bt, db, publishEntity, all, statsByDf);
            }
            Date date = new Date();        // accept HW CENTRAL
            acceptHWDeliveryForCentralBulk(db, all, response.getDeliveryId(), responseDate, response.getFullGroup(),
                    date, statsByDf);
            ps.deletePublishes(null, publishIds);
            if (!db.isEntire()) {
                ret = PublishWrapper.createIssuePublishWrapper(PubType.TYPE_SEMANTICO, db.getTitle(), db.getId(),
                    IPublish.BY_WHEN_READY, date, false, true);
                ret.getPublishEntity().setDb(db);
            }
        }
        return ret;
    }

    private PublishWrapper acceptWOLLITDelivery(LiteratumSentConfirm response, Date responseDate, BaseType bt,
            ClDbEntity db, PublishEntity parent, List<PublishRecordEntity> all, OpStats stats) {

        Date date = new Date();
        boolean entire = db.isEntire();
        Map<String, List<PublishRecordEntity>> allMap = new HashMap<>();
        all.forEach(pre -> allMap.computeIfAbsent(
                RecordHelper.buildCdNumber(pre.getNumber()), f -> new ArrayList<>()).add(pre));
        List<LiteratumSentConfirm.HasPart> parts = response.getHasPart();
        Set<String> cdNumbers = new HashSet<>();
        for (LiteratumSentConfirm.HasPart part: parts) {
            if (part != null) {
                String doi = part.getDoi();
                String cdNumber = bt.getCdNumberByDoi(doi);
                int pub = bt.getProductType().parsePubNumber(doi);
                List<PublishRecordEntity> list = allMap.get(cdNumber);
                if (list != null) {
                    cdNumbers.add(cdNumber);
                    ps.acceptLiteratumDeliveryByPubNumber(pub, list, responseDate, date, stats);
                } else {
                    stats.addError(String.format("\ncannot find %s (%s) to send to HW", cdNumber, doi));
                }
            }
        }
        Collection<Integer> toRemove = getWaitForPublishToRemove(allMap);
        if (toRemove != null && !toRemove.isEmpty()) {
            ps.deletePublishes(null, toRemove);
        }
        PublishWrapper ret = null;
        if (!cdNumbers.isEmpty()) {
            try {
                ret = entire ? PublishWrapper.createEntirePublishWrapper(PubType.TYPE_SEMANTICO, PubType.MAJOR_TYPE_S,
                        db.getTitle(), true, date) : PublishWrapper.createIssuePublishWrapperEx(PubType.TYPE_SEMANTICO,
                        db.getTitle(), db.getId(), IPublish.BY_WHEN_READY, date, new boolean[] {false, true, false});
                ret.setGenerate(true);
                ret.setSend(true);
                ret.setCdNumbers(cdNumbers);
                ret.getPublishEntity().setDb(db);
                ret.setHWFrequency(parent.noPrioritySet() ? null
                        : HWFreq.getHWFreq(parent.getHwFrequency()).getValue());
            } catch (Exception e) {
                stats.addError(String.format("\na error for HW package by Lit package %s [%d] - %s",
                        response.getDeliveryId(), parent.getId(), e.getMessage()));
            }
        }
        return ret;
    }

    private void acceptHWDeliveryForCentralBulk(ClDbEntity db, List<PublishRecordEntity> publishRecords,
                                                String responseDeliveryId, Date responseDate, boolean responseFullGroup,
                                                Date handledDate, OpStats statsByDf) {
        int size = publishRecords.size();
        int max = DbConstants.DB_PACK_SIZE << 2;
        if (size > max) {
            int ind = 0;
            while (ind < size) {
                int start = ind;
                ind += max;
                ps.acceptHWDeliveryForCentral(db, publishRecords.subList(start, ind > size ? size : ind),
                        responseDeliveryId, responseDate, responseFullGroup, handledDate, statsByDf);
            }
        } else {
            ps.acceptHWDeliveryForCentral(db, publishRecords, responseDeliveryId, responseDate, responseFullGroup,
                    handledDate, statsByDf);
        }
    }

    private Collection<Integer> getWaitForPublishToRemove(Map<String, List<PublishRecordEntity>> allMap) {
        Set<Integer> publishIds = null;
        Set<Integer> publishIdsNotCompleted = null;
        for (Map.Entry<String, List<PublishRecordEntity>> entry: allMap.entrySet()) {
            List<PublishRecordEntity> list = entry.getValue();
            for (PublishRecordEntity pre: list) {
                Integer publishId = pre.getPublishPacket().getId();
                if (pre.getHandledDate() == null) {
                    if (publishIdsNotCompleted == null) {
                        publishIdsNotCompleted = new HashSet<>();
                    }
                    publishIdsNotCompleted.add(publishId);
                    if (publishIds != null) {
                        publishIds.remove(publishId);
                    }
                } else if (publishIdsNotCompleted == null || !publishIdsNotCompleted.contains(publishId)) {
                    if (publishIds == null) {
                        publishIds = new HashSet<>();
                    }
                    publishIds.add(publishId);
                }
            }
        }
        return publishIds;
    }
}
