package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

import com.wiley.cms.cochrane.activitylog.ErrorLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.RecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 28.06.2017
 */
public class Wml3gConversionErrorsCollector extends RecordProcessingErrorsCollector {

    private final NotConvertedRecordIdSource notConvertedRecordIdSource;

    public Wml3gConversionErrorsCollector(int fullIssueNumb) {
        this(new NotConvertedRecordIdSource(), new Wml3gConversionErrorsSource(fullIssueNumb));
    }

    public Wml3gConversionErrorsCollector(
            NotConvertedRecordIdSource notConvertedRecordIdSource,
            Wml3gConversionErrorsSource convErrsSource) {
        super(convErrsSource);
        this.notConvertedRecordIdSource = notConvertedRecordIdSource;
    }

    @Override
    public Map<Integer, String> getData(List<Integer> recordIds) {
        List<Integer> notConvertedRecordIds = selectNotConvertedRecordIds(recordIds);
        return super.getData(notConvertedRecordIds);
    }

    private List<Integer> selectNotConvertedRecordIds(List<Integer> recordIds) {
        if (CollectionUtils.isEmpty(recordIds)) {
            return Collections.emptyList();
        } else {
            return notConvertedRecordIdSource.getData(recordIds);
        }
    }

    /**
     *
     */
    private static class NotConvertedRecordIdSource implements ReportDataSource<List<Integer>, List<Integer>> {

        public List<Integer> getData(List<Integer> recordIds) {
            return AbstractManager.getRecordStorage()
                    .getRecordIds(recordIds, RecordPublishEntity.getNotConvertedStates());
        }
    }

    /**
     *
     */
    public static class Wml3gConversionErrorsSource implements ProcessingErrorsDataSource {

        private final int fullIssueNumb;

        public Wml3gConversionErrorsSource(int fullIssueNumb) {
            this.fullIssueNumb = fullIssueNumb;
        }

        public Map<Integer, String> getData(List<Integer> recordIds) {
            Collection<ErrorLogEntity> conversionErrors = requestConversionErrors(recordIds);
            return getRecordErrors(conversionErrors);
        }

        private Collection<ErrorLogEntity> requestConversionErrors(List<Integer> recordIds) {
            IActivityLogService logService = AbstractManager.getActivityLogService();
            List<Integer> eventId = getConversionFailedEventId();
            return logService.getLatestErrorLogs(eventId, recordIds, fullIssueNumb);
        }

        private List<Integer> getConversionFailedEventId() {
            List<Integer> eventId = new ArrayList<Integer>();
            eventId.add(ILogEvent.CONVERSION_TO_3G_FAILED);
            return eventId;
        }

        private Map<Integer, String> getRecordErrors(Collection<ErrorLogEntity> conversionErrors) {
            Map<Integer, String> recordErrors = new HashMap<Integer, String>(conversionErrors.size());
            for (ErrorLogEntity conversionError : conversionErrors) {
                recordErrors.put(conversionError.getEntityId(), conversionError.getComments());
            }
            return recordErrors;
        }
    }
}
