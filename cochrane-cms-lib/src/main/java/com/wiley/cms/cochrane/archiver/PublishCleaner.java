package com.wiley.cms.cochrane.archiver;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PubType;

import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 5/3/2019
 */
class PublishCleaner {
    private static final Logger LOG = Logger.getLogger(PublishCleaner.class);
    private static final int NUMBER_4_REPORT = 100000;

    private final Map<Integer, Set<Integer>> parent2Children = new HashMap<>();
    private final Map<Integer, Map<Integer, Integer>> db2UITypes = new HashMap<>();
    private final Set<String> savedNames = new HashSet<>();
    private final Set<Integer> publishWaitIds = new HashSet<>();
    private final Map<Integer, PublishInfo> aLL = new HashMap<>();

    private final String[] publishHistoryQueries;
    private final Map<String, String> publishHistoryParams = new HashMap<>();
    private final Cleaner.Stats stats;

    private int savedCount = 0;
    private int sentCount = 0;
    private int committedCount = 0;
    private int deleteCount = 0;

    private final IPublishStorage ps = CochraneCMSBeans.getPublishStorage();

    PublishCleaner(Cleaner.Stats stats) throws Exception {
        publishHistoryQueries = initPublishHistoryQueries();
        this.stats = stats;
    }

    void cleanOldPublishEntities() {

        LOG.debug("a check for unsent packages ...");
        BaseType.getAll().forEach(this::cleanOldNotSentEntities);
        //if (sentCount > 0) {
        //    LOG.info("a check for sent packages ...");
        //    cleanOldSentEntities(stats);
        //}
        aLL.clear();
    }

    void clear() {
        parent2Children.clear();
        db2UITypes.clear();
        savedNames.clear();
        publishWaitIds.clear();
        aLL.clear();
    }

    boolean isPublishNameSaved(String publishFileName) {
        return savedNames.contains(publishFileName);
    }

    private void cleanOldNotSentEntities(Res<BaseType> bt) {

        publishWaitIds.addAll(ps.findPublishWait());

        String dbName = bt.get().getId();
        Date expiredDate = stats.getExpirationDate();
        List<PublishEntity> list = ps.findPublishesByDbName(dbName);
        Iterator<PublishEntity> it = list.iterator();
        while (it.hasNext()) {

            PublishEntity pe = it.next();
            Integer relId = pe.relationId();
            PublishInfo relPi = getPublishInfo(relId);
            if (stats.isPublishEntityExcluded(relId)) {
                it.remove();
                continue;
            }

            ClDbEntity clDb = pe.getDb();
            Integer dbId = clDb.getId();
            boolean sent = pe.sent();
            if (checkLastOne4UI(dbId, pe) || relPi.saved() || stats.isDbSaved(dbId)
                    || !isPublishDateExpired(pe, expiredDate)) {

                savePublishInfo(relPi);
                it.remove();
                continue;
            }

            if (sent) {
                Integer publishId = pe.getId();
                if (pe.hasParent()) {
                    parent2Children.computeIfAbsent(pe.getParentId(), f -> new HashSet<>()).add(publishId);
                }
                setSent(publishId);
                it.remove();
            }
        }
        printReport(dbName, !list.isEmpty() ? removeNotSentPublish(list) : 0);
    }

    private void cleanOldSentEntities() {

        Set<String> litNames = new HashSet<>();
        Date expiredDate2record = stats.getExpirationDate();
        long allCount = 0;
        int startIndex = 0;
        PublishRecordEntity last = null;
        DbUtils.DbCommitter committer = new DbUtils.DbCommitter(this::commitPublishWithBackup);

        List<PublishRecordEntity> list = ps.findPublishRecords(0, Integer.MAX_VALUE, startIndex,
            PublishRecordEntity.MAX_DIAPASON);
        allCount += list.size();

        while (!list.isEmpty()) {
            last = removeSentPublish(list, expiredDate2record, litNames, committer, last);
            startIndex += PublishRecordEntity.MAX_DIAPASON;
            list = ps.findPublishRecords(0, Integer.MAX_VALUE, startIndex, PublishRecordEntity.MAX_DIAPASON);
            if (allCount % NUMBER_4_REPORT == 0) {
                printReport(allCount, committer.allCommitted(), last);
            }
            allCount += list.size();
        }
        printReport(allCount, committer.allCommitted(), last);

        // remove remaining deleted LIT and expired sent packages
        Iterator<Integer> it = aLL.keySet().iterator();
        while (it.hasNext()) {
            Integer publishId = it.next();
            PublishInfo pi = getPublishInfo(publishId);
            if (pi.canDelete()) {
                commit(publishId, pi, committer);
                it.remove();
            }
        }
        committer.commitLast();
        printReport(committer.allCommitted());
    }

    private void removeSentLitPublish(String pubName, boolean failed, PublishEntity pe, Date expiredDate2record,
                                      Set<String> litNames) {
        boolean wasChecked4Lit = litNames.contains(pubName);
        if (!wasChecked4Lit) {
            litNames.clear();
        }
        Integer relId = pe.relationId();
        Integer publishId = pe.getId();
        PublishInfo piRel = getPublishInfo(relId);
        PublishInfo pi = relId.equals(publishId) ? piRel : getPublishInfo(publishId);

        if (piRel.saved()) {
            savePublishInfo(pi);
            if (!failed) {
                litNames.add(pubName);
            }
            return;
        }
        if (!failed && (!isPublishDateExpired(pe, expiredDate2record) || !litNames.contains(pubName))) {
            savePublishInfo(piRel);
            savePublishInfo(pi);
            litNames.add(pubName);
            return;
        }
        deletePublishInfo(pi);
    }

    private void removeSentPublish(PublishEntity pe, Date expiredDate2record, DbUtils.DbCommitter committer) {
        Integer publishId = pe.getId();
        PublishInfo pi = getPublishInfo(publishId);
        if (pi.saved() || pi.committed()) {
            return;
        }

        if (!isPublishDateExpired(pe, expiredDate2record)) {
            savePublishInfo(pi);
            return;
        }
        commit(publishId, pi, committer);
    }

    private PublishRecordEntity removeSentPublish(List<PublishRecordEntity> list, Date expiredDate2record,
        Set<String> litNames, DbUtils.DbCommitter committer, PublishRecordEntity last) {

        PublishRecordEntity ret = last;
        for (PublishRecordEntity pre: list) {
            ret = pre;
            if (stats.isPublishRecordEntityExcluded(pre.getId())) {
                continue;
            }
            PublishEntity pe = pre.getPublishPacket();
            if (PubType.LITERATUM_DB_TYPES.contains(pe.getPublishType())) {
                removeSentLitPublish(pre.getPubName(), pre.isFailed(), pe, expiredDate2record, litNames);
            } else {
                removeSentPublish(pe, expiredDate2record, committer);
            }
        }
        return ret;
    }

    private int removeNotSentPublish(List<PublishEntity> list) {
        DbUtils.DbCommitter committer = new DbUtils.DbCommitter(this::commitPublish);
        for (PublishEntity pe: list) {
            Integer publishId = pe.getId();
            if (stats.isPublishEntityExcluded(publishId)) {
                continue;
            }
            if (parent2Children.containsKey(publishId)) {
                replaceSentRelatedPublish(parent2Children.get(publishId));
                parent2Children.remove(publishId);
            }
            commit(publishId, committer);
        }
        committer.commitLast();
        return committer.allCommitted();
    }

    private void replaceSentRelatedPublish(Set<Integer> set) {
        set.forEach(ps::resetParentPublish);
    }

    private boolean isPublishDateExpired(PublishEntity pe, Date expiredDate) {
        Date ret = pe.getGenerationDate();
        if (ret == null) {
            ret = pe.getSendingDate();
            if (ret == null) {
                ret = pe.getStartSendingDate();
            }
        }
        return ret == null || expiredDate.after(ret);
    }

    private boolean checkLastOne4UI(Integer clDbId, PublishEntity pe) {
        Integer pubTypeId = pe.getPublishType();
        if (PubType.UI_TYPES.contains(pubTypeId)) {

            Map<Integer, Integer> map = db2UITypes.computeIfAbsent(clDbId, f -> new HashMap<>());
            Integer publishId = map.get(pubTypeId);
            if (publishId == null) {

                ClDbEntity db = pe.getDb();
                PublishEntity peUI = db.isEntire() ? ps.takeEntirePublishByDbAndType(db.getDatabase(), pubTypeId)
                        : ps.takePublishByDbAndType(db.getId(), pubTypeId);
                savedNames.add(peUI.getFileName());
                publishId = isEligible(peUI) ? peUI.relationId() : DbEntity.NOT_EXIST_ID;
                map.put(pubTypeId, publishId);
            }
            Integer newPublishId = pe.relationId();
            if (DbEntity.NOT_EXIST_ID == publishId && isEligible(pe)) {
                publishId = newPublishId;
                map.put(pubTypeId, newPublishId);
            }
            if (newPublishId.equals(publishId)) {
                savedNames.add(pe.getFileName());
                return true;
            }
        }
        return false;
    }

    private boolean isEligible(PublishEntity pe) {
        return pe != null && pe.getFileName() != null && pe.getFileName().length() > 0;
    }

    private void commitPublish(Collection<Integer> ids) {
        List<Integer> pwIds = null;
        if (!publishWaitIds.isEmpty()) {
            for (Integer id: ids) {
                pwIds = checkPublishWait(id, pwIds);
            }
        }
        if (!stats.isDryRun()) {
            ps.deletePublishes(ids, pwIds);
        }
    }

    private List<Integer> checkPublishWait(Integer id, List<Integer> pwIds) {
        if (publishWaitIds.remove(id)) {
            if (pwIds == null) {
                List<Integer> ret = new ArrayList<>();
                ret.add(id);
                return ret;
            }
            pwIds.add(id);
        }
        return pwIds;
    }

    private void commitPublishWithBackup(Collection<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        Iterator<Integer> it = ids.iterator();
        Integer firstId = it.next();
        sb.append(firstId);
        List<Integer> pwIds = checkPublishWait(firstId, null);
        while (it.hasNext()) {
            Integer id = it.next();
            pwIds = checkPublishWait(firstId, null);
            sb.append(",").append(id);
        }
        //if (!stats.isDryRun()) {
        publishHistoryParams.put("<ids>", sb.toString());
        Cleaner.updateDB(publishHistoryQueries, publishHistoryParams, stats.isDryRun(), ps);
        if (!stats.isDryRun()) {
            ps.deletePublishes(ids, pwIds);
        }
    }

    private String[] initPublishHistoryQueries() throws Exception {
        return InputUtils.readStreamToString(new FileInputStream(new File(new URI(
            CochraneCMSPropertyNames.getCochraneResourcesRoot() + "archive/cochrane_history_publish.sql")))).split(";");
    }

    private void printReport(String dbName, int deletedCount) {
        LOG.debug(String.format(
            "[%s] %s -> removed unsent %d, total removed %d, saved %d, sent packages to check %d, checked %d",
                !stats.isDryRun(), dbName, deletedCount, committedCount, savedCount, sentCount, aLL.size()));
    }

    private void printReport(int deletedCount) {
        LOG.info(String.format("[%s] total removed %d/%d, saved %d, remained %d",
                !stats.isDryRun(), committedCount, deletedCount, savedCount, aLL.size()));
    }

    private void printReport(long checkedCount, int deletedCount, PublishRecordEntity last) {
        if (last == null) {
            return;
        }
        LOG.debug(String.format(
            "[%s] %d records checked, last: %d, LIT entities to remove %d, total removed %d/%d, saved %d, remained %d",
                !stats.isDryRun(), checkedCount, last.getNumber(), deleteCount, committedCount, deletedCount,
                    savedCount, aLL.size()));
    }

    private void savePublishInfo(PublishInfo pi) {
        if (!pi.saved()) {
            savedCount++;
        }
        pi.save();
    }

    private void deletePublishInfo(PublishInfo pi) {
        if (!pi.toDelete()) {
            deleteCount++;
        }
        pi.delete();
    }

    private PublishInfo getPublishInfo(Integer id) {
        return aLL.computeIfAbsent(id, f -> new PublishInfo());
    }

    private void setSent(Integer id) {
        PublishInfo pi = getPublishInfo(id);
        pi.setSent();
        sentCount++;
    }

    private void commit(Integer id, PublishInfo pi, DbUtils.DbCommitter committer) {
        if (!pi.committed()) {
            committedCount++;
        }
        pi.commit();
        committer.commit(id);
    }

    private void commit(Integer id, DbUtils.DbCommitter committer) {
        committer.commit(id);
        committedCount++;
    }

    private static class PublishInfo {
        private static final int DELETED = -2;
        private static final int TO_DELETE = -1;
        private static final int SENT = 1;
        private static final int SAVED = 2;

        int state = 0;

        private void setSent() {
            state = SENT;
        }

        private void commit() {
            state = DELETED;
        }

        boolean committed() {
            return state == DELETED;
        }

        boolean canDelete() {
            return state == TO_DELETE || state == SENT;
        }

        boolean toDelete() {
            return state == TO_DELETE;
        }

        void delete() {
            state = TO_DELETE;
        }

        boolean saved() {
            return state == SAVED;
        }

        void save() {
            state = SAVED;
        }
    }
}
