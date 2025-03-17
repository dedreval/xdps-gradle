package com.wiley.cms.cochrane.cmanager.publish.udw;

import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.data.RecordAbstract;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.utils.BatchTransformIterator;
import com.wiley.cms.cochrane.utils.QueryResultsIterator;
import com.wiley.cms.cochrane.utils.QueryResultsSupplier;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang.time.DateUtils;

import java.util.Date;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.wiley.cms.cochrane.cmanager.CochraneCMSProperties.getIntProperty;
import static com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder.ID;
import static com.wiley.cms.cochrane.cmanager.res.PubType.SEMANTICO_DB_TYPES;
import static com.wiley.cms.cochrane.cmanager.res.PubType.UDW_DB_TYPES;
import static com.wiley.cms.cochrane.utils.Constants.UNDEF;
import static java.util.stream.Collectors.summarizingInt;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 22.01.2019
 */
public class ArticlesForUdwFeedFinder {
    private static final int DEFAULT_LAST_COLLECTION_DATE_SHIFT_IN_MIN = 2 * 60;
    private final String dbName;
    private final int lastCollectionDateShiftInMin;
    private final Function<List<PublishRecordEntity>, List<PublishRecordEntity>> notSentToUdwFilter;
    private final IEntireDBStorage entireStorage;
    private final IPublishStorage pubStorage;

    public ArticlesForUdwFeedFinder(String dbName) {
        this.dbName = dbName;
        this.lastCollectionDateShiftInMin = getIntProperty(
                "cms.cochrane.udw.last_collection_date.shift_in_min", DEFAULT_LAST_COLLECTION_DATE_SHIFT_IN_MIN);
        this.notSentToUdwFilter = createNotSentToUdwArticlesFilter();
        this.entireStorage = AbstractManager.getEntireDBStorage();
        this.pubStorage = CochraneCMSBeans.getPublishStorage();
    }

    private Function<List<PublishRecordEntity>, List<PublishRecordEntity>> createNotSentToUdwArticlesFilter() {
        return target -> {
            IntSummaryStatistics stat = target.stream().collect(summarizingInt(PublishRecordEntity::getNumber));
            Set<String> unwanted = pubStorage
                    .findSentPublishRecords(stat.getMin(), stat.getMax(), UDW_DB_TYPES, UNDEF)
                    .stream()
                    .map(pubMd -> "" + pubMd.getNumber() + pubMd.getPubNumber())
                    .collect(Collectors.toSet());
            return target.stream()
                    .filter(it -> !unwanted.contains("" + it.getNumber() + it.getPubNumber()))
                    .collect(Collectors.toList());
        };
    }

    public Optional<List<Integer>> find() {
        Date lastCollectDate = getLastCollectionDateWithShift();
        if (lastCollectDate == null) {
            return Optional.empty();
        }

        Iterator<Integer> forUdwArticles = getUpdatedArticles(lastCollectDate);
        return Optional.of(IteratorUtils.toList(forUdwArticles));
    }

    private Date getLastCollectionDateWithShift() {
        PublishEntity publishMd = pubStorage.takeLatestSentPublishByDbAndPubTypes(dbName, UDW_DB_TYPES);
        if (publishMd == null) {
            return null;
        } else {
            return DateUtils.addMinutes(publishMd.getSendingDate(), -lastCollectionDateShiftInMin);
        }
    }

    private Iterator<Integer> getUpdatedArticles(Date lastCollectDate) {
        Function<List<PublishRecordEntity>, List<Integer>> articleIdFromPubMdObtainer = pubMds -> {
            Set<String> articleNames = pubMds.stream()
                    .map(RecordAbstract::getNumber)
                    .map(RecordHelper::buildCdNumber)
                    .collect(Collectors.toSet());
            return entireStorage.getRecordIds(
                    dbName, 0, UNDEF, articleNames.toArray(new String[0]), null, ID, false, null, UNDEF);
        };

        QueryResultsSupplier<PublishRecordEntity> sentToHwArticlesSupplier = (offset, limit) ->
                pubStorage.findSentPublishRecordsAfterDate(dbName, SEMANTICO_DB_TYPES, lastCollectDate, offset, limit);
        Iterator<PublishRecordEntity> sentToHwArticles = new QueryResultsIterator<>(sentToHwArticlesSupplier);
        Iterator<PublishRecordEntity> forUdwArticles =
                new BatchTransformIterator<>(sentToHwArticles, notSentToUdwFilter);
        return new BatchTransformIterator<>(forUdwArticles, articleIdFromPubMdObtainer);
    }
}
