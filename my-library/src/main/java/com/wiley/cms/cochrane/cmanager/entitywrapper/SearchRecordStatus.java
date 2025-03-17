package com.wiley.cms.cochrane.cmanager.entitywrapper;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface SearchRecordStatus {
    int QA_PASSED = 1;
    int QA_FAILED = 2;
    int RENDER_PASSED = 3;
    int RENDER_FAILED = 4;
    int REJECTED = 5;
    int UNAPPROVED = 6;
    int APPROVED = 7;
    int UNREJECTED = 8;
    int WHEN_READY_PUBLISHING = 9;
    int CCH_PUBLISHING = 10;
    int HW_PUBLISHING = 12;
    int DS_PUBLISHING = 13;
    int HW_FAILURE = 14;
}
