package com.wiley.cms.cochrane.cmanager.specrender.xlsfiles.manualreport;

import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchEntireRecordOrder;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.utils.Constants;

import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 11.05.2017
 */
public class IncrementalRecordIdCollector implements ReportDataCollector<List<Integer>, Integer> {

    private final RecordIdDataSource dataSource;

    public IncrementalRecordIdCollector(int dbId, boolean entire, String[] recordNames) {
        dataSource = (entire
                ? new EntireRecordIdDataSource(dbId, recordNames)
                : new IssueRecordIdDataSource(dbId, recordNames));
    }

    public List<Integer> getData(Integer offset) {
        return dataSource.getData(offset);
    }

    /**
     *
     */
    public interface RecordIdDataSource extends ReportDataSource<List<Integer>, Integer> {
    }

    /**
     *
     */
    private static class IssueRecordIdDataSource implements RecordIdDataSource {

        private final int dbId;
        private final String[] recordNames;
        private final int batchSize;
        private final IRecordStorage storage;

        private IssueRecordIdDataSource(int dbId, String[] recordNames) {
            this.dbId = dbId;
            this.recordNames = recordNames;
            this.batchSize = PublishProfile.PUB_PROFILE.get().getBatch();
            this.storage = AbstractManager.getRecordStorage();
        }

        public List<Integer> getData(Integer offset) {
            return storage.getDbRecordIdList(dbId, recordNames, Constants.UNDEF, offset, batchSize);
        }
    }

    /**
     *
     */
    private static class EntireRecordIdDataSource implements RecordIdDataSource {

        private final String dbName;
        private final String[] recordNames;
        private final int batchSize;
        private final IEntireDBStorage storage;

        private EntireRecordIdDataSource(int dbId, String[] recordNames) {
            this.dbName = BaseType.getDbName(dbId);
            this.recordNames = recordNames;
            this.batchSize = PublishProfile.PUB_PROFILE.get().getBatch();
            this.storage = AbstractManager.getEntireDBStorage();
        }

        public List<Integer> getData(Integer offset) {
            return storage.getRecordIds(dbName, offset, batchSize, recordNames, null, SearchEntireRecordOrder.NONE,
                    false, null, Constants.UNDEF);
        }
    }
}
