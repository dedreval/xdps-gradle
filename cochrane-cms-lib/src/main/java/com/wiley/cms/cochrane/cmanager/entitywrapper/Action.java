package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.authentication.IVisit;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface Action {
    int APPROVE_ACTION = 1;
    int UN_APPROVE_ACTION = 2;
    int REJECT_ACTION = 3;
    int UN_REJECT_ACTION = 6;
    int PUBLISH_ACTION = 4;
    int UN_PUBLISH_ACTION = 5;
    int RESUME_ACTION = 6;
    int MAKE_PERM_ACTION = 7;
    int DELETE_ACTION = 8;
    int CREATE_ACTION = 9;
    int RECREATE_ACTION = 10;
    int COMMENT_ACTION = 11;
    int RECOVER_ACTION = 12;
    int ARCHIVE_ACTION = 13;
    int EDIT_ACTION = 14;
    int DOWNLOAD_MESHTERMS_ACTION = 15;
    int DELIVER_INITIAL_PACKAGE_ACTION = 16;
    int DELIVER_CENTRAL_PACKAGE_ACTION = 17;
    int CHECK_CHANGES_MESH_CODES_ACTION = 18;
    int RENDER_HTML_ACTION = 19;
    int RENDER_PDF_ACTION = 20;
    int CONVERT_TO_3G_ACTION = 21;
    int CONVERT_REVMAN_ACTION = 22;
    int WHEN_READY_ACTION = 23;
    int RESET_ACTION = 24;
    int RESUME_RENDER_ACTION = 25;
    int REPROCESS_ACTION = 26;
    int CONVERT_JATS_ACTION = 27;
    int ACKNOWLEDGEMENT_ACTION = 28;
    int UNARCHIVE_ACTION = 29;

    int getId();

    String getDisplayName();

    void perform();

    void perform(IVisit visit);

    boolean isCommentRequested();

    void setCommentRequested(boolean value);

    boolean isConfirmable();

    void setConfirmable(boolean value);

    String getConfirmMessage();

    void setConfirmMessage(String value);
}
