package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.RecordWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 27.04.2017
 */
public class RecordMetadataCollector
        implements ReportDataCollector<List<RecordMetadataCollector.RecordMetadata>, List<Integer>> {

    private final RecordMetadataSource metadataSource;

    public RecordMetadataCollector(boolean entire) {
        this.metadataSource = (entire
                ? new EntireRecordMetadataSource()
                : new IssueRecordMetadataSource());
    }

    public RecordMetadataCollector(RecordMetadataSource metadataSource) {
        this.metadataSource = metadataSource;
    }

    public List<RecordMetadata> getData(List<Integer> recordIds) {
        if (recordIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<? extends RecordWrapper> records = metadataSource.getData(recordIds);
            return toReportMetadataList(records);
        }
    }

    private List<RecordMetadata> toReportMetadataList(List<? extends RecordWrapper> records) {
        List<RecordMetadata> recordMetadataLst = new ArrayList<RecordMetadata>(records.size());
        for (RecordWrapper record : records) {
            RecordMetadata recordMetadata = new RecordMetadata(record);
            recordMetadataLst.add(recordMetadata);
        }
        return recordMetadataLst;
    }

    /**
     *
     */
    public interface RecordMetadataSource extends ReportDataSource<List<? extends RecordWrapper>, List<Integer>> {
    }

    /**
     *
     */
    public static class IssueRecordMetadataSource implements RecordMetadataSource {

        private final IRecordStorage storage;

        private IssueRecordMetadataSource() {
            this.storage = AbstractManager.getRecordStorage();
        }

        public List<RecordWrapper> getData(List<Integer> recordIds) {
            List<RecordEntity> records = storage.getRecordEntitiesByIds(recordIds);
            return RecordWrapper.getRecordWrapperList(records);
        }
    }

    /**
     *
     */
    public static class EntireRecordMetadataSource implements RecordMetadataSource {

        private final IEntireDBStorage storage;

        private EntireRecordMetadataSource() {
            this.storage = AbstractManager.getEntireDBStorage();
        }

        public List<EntireRecordWrapper> getData(List<Integer> recordIds) {
            List<EntireDBEntity> records = storage.getRecordsByIds(recordIds);
            return EntireRecordWrapper.getEntireRecordWrapperList(records);
        }
    }

    /**
     *
     */
    public static class RecordMetadata {

        final String hash;
        private final int id;
        private final String name;
        private final String title;
        private final String status;
        private final String subtitle;
        private final String group;

        public RecordMetadata(RecordWrapper record) {
            id = record.getId();
            name = record.getName();
            title = record.getUnitTitle();
            status = record.getUnitStatus();
            subtitle = record.getProductSubtitle();
            group = record.getGroupName();
            hash = id + name + title + status + subtitle + group;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getTitle() {
            return title;
        }

        public String getStatus() {
            return status;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getGroup() {
            return group;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof RecordMetadata) {
                RecordMetadata that = (RecordMetadata) o;
                return hash.equals(that.hash);
            }
            return false;

        }

        @Override
        public int hashCode() {
            return hash.hashCode();
        }
    }
}
