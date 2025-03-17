package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;

import com.wiley.cms.cochrane.activitylog.ErrorLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 28.06.2017
 */
public class HWFailureErrorsCollector extends RecordProcessingErrorsCollector {

    private final NotPublishedRecordIdSource notPublishedRecordIdSource;

    public HWFailureErrorsCollector(int fullIssueNumb) {
        this(new NotPublishedRecordIdSource(), new HwFailureErrorsSource(fullIssueNumb));
    }

    public HWFailureErrorsCollector(
            NotPublishedRecordIdSource notPublishedRecordIdSource,
            HwFailureErrorsSource hwErrsSource) {
        super(hwErrsSource);
        this.notPublishedRecordIdSource = notPublishedRecordIdSource;
    }

    @Override
    public Map<Integer, String> getData(List<Integer> recordIds) {
        List<Integer> notConvertedRecordIds = selectNotPublishedRecordIds(recordIds);
        return super.getData(notConvertedRecordIds);
    }

    private List<Integer> selectNotPublishedRecordIds(List<Integer> recordIds) {
        if (CollectionUtils.isEmpty(recordIds)) {
            return Collections.emptyList();
        } else {
            return notPublishedRecordIdSource.getData(recordIds);
        }
    }

    /**
     *
     */
    private static class NotPublishedRecordIdSource implements ReportDataSource<List<Integer>, List<Integer>> {

        public List<Integer> getData(List<Integer> recordIds) {
            return AbstractManager.getRecordStorage().getRecordIds(recordIds, RecordEntity.STATE_HW_PUBLISHING_ERR);
        }
    }

    /**
     *
     */
    public static class HwFailureErrorsSource implements ProcessingErrorsDataSource {

        private final int fullIssueNumb;

        public HwFailureErrorsSource(int fullIssueNumb) {
            this.fullIssueNumb = fullIssueNumb;
        }

        public Map<Integer, String> getData(List<Integer> recordIds) {
            IActivityLogService logService = AbstractManager.getActivityLogService();
            return getRecordErrors(logService.getLatestErrorLogs(Collections.singletonList(ILogEvent.PRODUCT_ERROR),
                    recordIds, fullIssueNumb));
        }

        private static Map<Integer, String> getRecordErrors(Collection<ErrorLogEntity> conversionErrors) {
            Map<Integer, String> recordErrors = new HashMap<>(conversionErrors.size());
            conversionErrors.forEach(e -> recordErrors.put(e.getEntityId(), e.getComments()));
            return recordErrors;
        }
    }
}
