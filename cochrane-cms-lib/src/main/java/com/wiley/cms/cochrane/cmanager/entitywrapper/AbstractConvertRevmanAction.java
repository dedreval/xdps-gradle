package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.entity.DbEntity;

import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 26.12.2012
 */
public abstract class AbstractConvertRevmanAction extends AbstractAction {

    public int getId() {
        return Action.CONVERT_REVMAN_ACTION;
    }

    public String getDisplayName() {
        return "Convert revman";
    }


    protected String getReport(List<ErrorInfo> errors, int issueNumber, IActivityLogService logService) {
        return RevmanMetadataHelper.getReport(errors, DbEntity.NOT_EXIST_ID, issueNumber, logService);
    }
}
