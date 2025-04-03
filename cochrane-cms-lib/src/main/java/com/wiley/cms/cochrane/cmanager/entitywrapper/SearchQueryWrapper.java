package com.wiley.cms.cochrane.cmanager.entitywrapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.data.SearchQueryEntity;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class SearchQueryWrapper extends AbstractWrapper implements java.io.Serializable {

    private SearchQueryEntity entity;

    public SearchQueryWrapper(SearchQueryEntity entity) {
        this.entity = entity;
    }

    public Integer getId() {
        return entity.getId();
    }

    public String getArea() {
        return entity.getArea();
    }

    public Date getDate() {
        return entity.getDate();
    }

    public String getFileStatus() {
        return entity.getFileStatus();
    }

    public Integer getSystemStatus() {
        return entity.getSystemStatus();
    }

    public String getText() {
        return entity.getText();
    }

    public String getSystemStatusName() {
        String statusName = "";
        switch (getSystemStatus()) {
            case SearchRecordStatus.QA_PASSED:
                statusName = "QA passed";
                break;
            case SearchRecordStatus.QA_FAILED:
                statusName = "QA failed";
                break;
            case SearchRecordStatus.RENDER_PASSED:
                statusName = "Render Passed";
                break;
            case SearchRecordStatus.RENDER_FAILED:
                statusName = "Render Failed";
                break;
            case SearchRecordStatus.REJECTED:
                statusName = "Rejected";
                break;
            case SearchRecordStatus.UNAPPROVED:
                statusName = "Unapproved";
                break;
            case SearchRecordStatus.APPROVED:
                statusName = "Approved";
                break;
            case SearchRecordStatus.HW_FAILURE:
                statusName = "HW publication failed";
                break;
            default:
                statusName = "All records";
        }
        return statusName;
    }

    public static void createSearchQuery(String text, String area,
                                         String fileStatus, int systemStatus, Date date) throws Exception {
        getResultStorage().createSearchQuery(text, area, fileStatus, systemStatus, date);
    }

    public static List<SearchQueryWrapper> getSearchQueryList(List<SearchQueryEntity> list) {
        List<SearchQueryWrapper> entityList = new ArrayList<SearchQueryWrapper>();
        for (SearchQueryEntity entity : list) {
            entityList.add(new SearchQueryWrapper(entity));
        }
        return entityList;
    }

    public static List<SearchQueryWrapper> getSearchQueryList(int beginIndex, int limit) {
        return getSearchQueryList(getResultStorage().getSearchQueryList(beginIndex, limit));
    }

    public static int getSearchQueryListCount() {
        return getResultStorage().getSearchQueryListCount();
    }
}
