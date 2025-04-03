package com.wiley.cms.cochrane.cmanager;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 26-Mar-2007
 */
public interface IDeliveryFileStatus {
    // final states
    int STATUS_BEGIN = 1;
    int STATUS_PICKUP_FAILED = 2;
    //    static final int STATUS_PICKED_UP = 3;
    //    static final int STATUS_UNZIPPED = 4;
    int STATUS_BAD_FILE_NAME = 3;
    int STATUS_CORRUPT_ZIP = 4;
    int STATUS_INVALID_CONTENT = 5;
    int STATUS_QAS_FAILED = 6;
    int STATUS_QAS_FINISHED_SOME_BAD = 7;
    int STATUS_RND_FAILED = 8;
    int STATUS_RND_SOME_FAILED = 9;
    int STATUS_RND_FINISHED_SUCCESS = 10;
    int STATUS_MESHTERM_UPDATING_FAILED = 32;
    int STATUS_VALIDATION_FAILED = 33;
    int STATUS_VALIDATION_SOME_FAILED = 34;
    int STATUS_PUBLISHING_FAILED = 35;
    int STATUS_PUBLISHING_FINISHED_SUCCESS = 36;
    int STATUS_RND_SUCCESS_AND_FULL_FINISH = 40;
    int STATUS_PACKAGE_LOADED = 41;
    int STATUS_RND_NOT_STARTED = 29;

    //interim states
    int STATUS_PACKAGE_IDENTIFIED = 11;
    int STATUS_SOURCES_SAVED = 12;
    int STATUS_RECORDS_CREATED = 13;
    int STATUS_MANIFESTS_CREATED = 14;
    int STATUS_PACKAGE_DELETED = 15;
    int STATUS_QAS_STARTED = 16;
    int STATUS_QAS_ACCEPTED = 17;
    int STATUS_RENDERING_STARTED = 18;
    int STATUS_RENDERING_ACCEPTED = 19;
    int STATUS_SHADOW = 20;
    int STATUS_PICKED_UP = 21;
    int STATUS_UNZIPPED = 22;
    int STATUS_PARTIAL_RND_ACCEPTED = 24;
    int STATUS_MESHTERM_UPDATING_STARTED = 30;
    int STATUS_MESHTERM_UPDATING_ACCEPTED = 31;
    int STATUS_REVMAN_CONVERTED = 37;
    int STATUS_REVMAN_CONVERTING = 38;
    int STATUS_PUBLISHING_STARTED = 39;

    //modify statuses
    int STATUS_MAKE_PERM_STARTED = 25;
    int STATUS_MAKE_PERM_FINISHED = 26;
    int STATUS_EDITING_STARTED = 27;
    int STATUS_EDITING_FINISHED = 28;

    //operational statuses
    @Deprecated
    int OP_MOVE_SHADOW = 100;

    int OP_ARIES_ACK_SENT = 101;
}