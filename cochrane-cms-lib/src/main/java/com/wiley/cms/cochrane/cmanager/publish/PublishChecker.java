package com.wiley.cms.cochrane.cmanager.publish;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.data.PublishRecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/14/2019
 *
 * @param <T>  <T> An item for checking (a record number | cdNumber.pubNumber)
 */
public abstract class PublishChecker<T> implements IPublishChecker<T> {
    private static final int MAX_DIFF_PERCENT = 10;

    private static final IPublishChecker EMPTY_TRUE = createByDefaultValue(false, true);
    private static final IPublishChecker EMPTY_FALSE = createByDefaultValue(false, false);
    private static final IPublishChecker EMPTY_TRUE_WITH_HISTORY = createByDefaultValue(true, true);
    private static final IPublishChecker EMPTY_FALSE_WITH_HISTORY = createByDefaultValue(true, false);

    Set<T> results = new HashSet<>();
    private final List<Integer> pubTypes;

    private PublishChecker(List<Integer> pubTypes) {
        this.pubTypes = pubTypes;
    }

    @Override
    public boolean isDelivered(T item) {
        return results.contains(item);
    }

    @Override
    public void initialize() {
        results.clear();
    }

    public int resultSize() {
        return results.size();
    }

    public boolean notUseDiapason(int minRecordNumber, int maxRecordNumber, int scopeSize) {
        int diapason = maxRecordNumber - minRecordNumber;
        int diff = diapason - scopeSize;
        return diapason > PublishRecordEntity.MAX_DIAPASON
                || (diff > 0 && Constants.HUNDRED_PERCENT * diff / diapason > MAX_DIFF_PERCENT);
    }

    public static PublishChecker getLiteratumDelivered(List<? extends RecordWrapper> recordList,
        Integer skipExportId, boolean sorted, boolean useHistory, IPublishStorage ps) {
        return (PublishChecker) getLiteratumDelivered(recordList, skipExportId, sorted, useHistory, null, ps);
    }

    public static IPublishChecker getLiteratumDelivered(List<? extends RecordWrapper> recordList, Integer skipExportId,
            boolean sorted, boolean useHistory, Boolean defaultValue, IPublishStorage ps) {
        return getDelivered(recordList, skipExportId, sorted, useHistory, defaultValue, PubType.LITERATUM_DB_TYPES, ps);
    }

    public static IPublishChecker getDSDelivered(List<? extends RecordWrapper> recordList, Integer skipExportId,
             boolean sorted, boolean useHistory, Boolean defaultValue, IPublishStorage ps) {
        return getDelivered(recordList, skipExportId, sorted, useHistory, defaultValue, PubType.DS_DB_TYPES, ps);
    }

    /**
     * It initializes an object to check if an article within a given scope was already delivered to the destination
     * specified by delivery types.
     * @param recordList        The not empty scope of checking articles
     * @param skipExportId      ID of the current publishing package to be exclude from the search
     * @param sorted            If TRUE the recordList is preliminary sorted by cdNumber
     * @param useHistory        If TRUE it will take in account historical records pub number (CDSR only)
     * @param defaultValue      If NOT NULL, this value will be always returned by checking this scope
     * @param pubTypes          The delivery types
     * @param ps                The storage to get information about delivery
     * @return                  The checker with delivery information related to the specified scope & destination
     */
    public static IPublishChecker getDelivered(List<? extends RecordWrapper> recordList, Integer skipExportId,
        boolean sorted, boolean useHistory, Boolean defaultValue, List<Integer> pubTypes, IPublishStorage ps) {

        if (defaultValue != null) {
            return getByDefaultValue(useHistory, defaultValue);
        }

        if (!sorted) {
            recordList.sort(Comparator.comparingInt(RecordWrapper::getNumber));
        }

        IPublishChecker ret = useHistory ? getDeliveredCDSR(recordList, skipExportId, pubTypes, ps)
                : getDelivered(recordList, skipExportId, pubTypes, ps);
        ret.initialize();
        return ret;
    }

    private static IPublishChecker getByDefaultValue(boolean useHistory, boolean defaultValue) {
        return useHistory ? (defaultValue ? EMPTY_TRUE_WITH_HISTORY : EMPTY_FALSE_WITH_HISTORY)
                : (defaultValue ? EMPTY_TRUE : EMPTY_FALSE);
    }

    private static IPublishChecker createByDefaultValue(boolean useHistory, boolean defaultValue) {
        return new IPublishChecker() {
            @Override
            public boolean isDelivered(Object object) {
                return defaultValue;
            }

            @Override
            public Object getPublishCheckerItem(int recordNumber, String pubName) {
                return useHistory ? pubName : recordNumber;
            }
        };
    }

    private static IPublishChecker getDelivered(List<? extends RecordWrapper> recordList,
        Integer skipExportId, List<Integer> pubTypes, IPublishStorage ps) {

        return new PublishChecker<Integer>(pubTypes) {
            @Override
            public void initialize() {
                super.initialize();
                results.addAll(getSentNumbers(recordList, skipExportId, ps));
            }

            @Override
            public Integer getPublishCheckerItem(int recordNumber, String pubName) {
                return recordNumber;
            }
        };
    }

    private static IPublishChecker getDeliveredCDSR(List<? extends RecordWrapper> recordList,
        Integer skipExportId, List<Integer> pubTypes, IPublishStorage ps) {

        return new PublishChecker<String>(pubTypes) {
            @Override
            public void initialize() {
                super.initialize();

                List<Object[]> list = getSentPubNames(recordList, skipExportId, ps);
                for (Object[] pubName: list) {
                    results.add(RevmanMetadataHelper.buildPubName(RecordHelper.buildCdNumber(
                            Integer.parseInt(pubName[0].toString())), Integer.parseInt(pubName[1].toString())));
                }
            }

            @Override
            public String getPublishCheckerItem(int recordNumber, String pubName) {
                return pubName;
            }
        };
    }

    List<Object[]> getSentPubNames(List<? extends RecordWrapper> recordList, Integer skipExportId,
                                    IPublishStorage ps) {
        int size = recordList.size();
        int minRecordNumber = recordList.get(0).getNumber();
        int maxRecordNumber = recordList.get(size - 1).getNumber();

        return ps.findSentPublishRecordAndPubNumbers(minRecordNumber, maxRecordNumber,
                notUseDiapason(minRecordNumber, maxRecordNumber, size) ? getRecordNumbers(recordList) : null,
                    pubTypes, skipExportId);
    }

    List<Integer> getSentNumbers(List<? extends RecordWrapper> recordList, Integer skipExportId, IPublishStorage ps) {
        int size = recordList.size();
        int minRecordNumber = recordList.get(0).getNumber();
        int maxRecordNumber = recordList.get(size - 1).getNumber();

        return ps.findSentPublishRecordNumbers(minRecordNumber, maxRecordNumber,
                notUseDiapason(minRecordNumber, maxRecordNumber, size) ? getRecordNumbers(recordList) : null,
                    pubTypes, skipExportId);
    }

    List<PublishRecordEntity> getSentRecords(List<? extends RecordWrapper> recordList, Integer skipExportId,
                                             IPublishStorage ps) {
        int size = recordList.size();
        int minRecordNumber = recordList.get(0).getNumber();
        int maxRecordNumber = recordList.get(size - 1).getNumber();

        return (notUseDiapason(minRecordNumber, maxRecordNumber, size)
            ? ps.findSentPublishRecords(minRecordNumber, maxRecordNumber, getRecordNumbers(recordList),
                    pubTypes, skipExportId)
            : ps.findSentPublishRecords(minRecordNumber, maxRecordNumber,
                    pubTypes, skipExportId));
    }

    private List<Integer> getRecordNumbers(List<? extends RecordWrapper> recordList) {
        List<Integer> recordNumbers = new ArrayList<>();
        for (RecordWrapper rw : recordList) {
            recordNumbers.add(rw.getNumber());
        }
        return recordNumbers;
    }
}
