package com.wiley.cms.cochrane.cmanager.publish.generate.ml3g;

import java.util.ArrayList;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireDbWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.EntireRecordWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.SearchRecordOrder;
import com.wiley.cms.cochrane.cmanager.publish.generate.AbstractGeneratorEntire;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveEntry;
import com.wiley.cms.cochrane.cmanager.publish.generate.ArchiveHolder;
import com.wiley.cms.cochrane.converter.ConverterException;
import com.wiley.tes.util.Extensions;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 14.11.11
 */
public class ML3GCentralGeneratorEntire extends AbstractGeneratorEntire<ArchiveHolder> {
    private static final int THREE = 3;

    public ML3GCentralGeneratorEntire(EntireDbWrapper db, String generateName, String exportTypeName) {
        super(db, generateName, exportTypeName);
    }

    @Override
    protected List<EntireRecordWrapper> getRecordList(int startIndex, int count) {
        return (!byRecords())
            ? (hasIncludedNames() ? getRecordListFromIncludedNames(count, SearchRecordOrder.LAST_ISSUE, true)
                    : EntireRecordWrapper.getRecordWrapperList(getDb().getDbName(), startIndex, count, null, null,
                SearchRecordOrder.LAST_ISSUE, true))
            : EntireRecordWrapper.getProcessEntireRecordWrapperList(getDb().getDbName(), startIndex, count,
                SearchRecordOrder.LAST_ISSUE, true, getRecordsProcessId());
    }

    @Override
    @SuppressWarnings({"unchecked"})
    protected List<ArchiveEntry> processRecordList(List<EntireRecordWrapper> recordList) throws Exception {

        List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
        if (recordList.isEmpty())  {
            return ret;
        }

        int lastIssue = recordList.get(0).getLastPublishedIssue();
        List<EntireRecordWrapper> subList = new ArrayList<EntireRecordWrapper>();

        for (EntireRecordWrapper record: recordList) {

            int issue = record.getLastPublishedIssue();

            if (issue != lastIssue) {

                addConvertedSourcesToList(subList, ret, lastIssue);

                subList.clear();
                lastIssue =  issue;
            }

            subList.add(record);
            onRecordArchive(record);
        }

        if (!subList.isEmpty()) {
            addConvertedSourcesToList(subList, ret, lastIssue);
        }

        return ret;
    }

    private void addConvertedSourcesToList(List<EntireRecordWrapper> recordList, List<ArchiveEntry> ret, int issue)
        throws ConverterException {

        for (EntireRecordWrapper record : recordList) {
            if (record.isDisabled()) {
                continue;
            }

            String recName = record.getName();

            ret.add(new ArchiveEntry(getArchivePrefix(recName) + "/" + recName + Extensions.XML,
                    rps.getRealFilePath(getConvertedSource(recName)), null));
        }
    }

    @Override
    protected String getArchivePrefix(String recName) {
        return recName.substring(recName.length() - THREE);
    }
}