package com.wiley.cms.cochrane.activitylog;

import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/21/2021
 */
public class FlowEventProduct extends FlowProduct {
    private static final long serialVersionUID = 1L;

    private String packageName;
    private String vendor;
    private final String language;
    private final String message;


    public FlowEventProduct(FlowLogEntity flowEntity) {
        super(flowEntity.getEntityName(), parseDoi(flowEntity), flowEntity.getPackageId(),
            BaseType.find(BaseType.getDbName(flowEntity.getDbType())).get(), getNull4Empty(flowEntity.getWho()), null);

        setEntityId(flowEntity.getEntityId());
        message = getNull4Empty(FlowLogCommentsPart.parseComments(flowEntity.getComments(), this));

        this.language = parseLanguage(flowEntity);
        FlowProduct.State state = State.byEvent(flowEntity.getEvent().getId(), isRetracted(), false, sPD().off(),
                LogEntity.LogLevel.ERROR == flowEntity.getLogLevel());
        setFlowState(state);
        if (wasCompleted()) {
            setState(State.PUBLISHED);
        } else {
            setState(state);
        }
    }

    private static String parseDoi(FlowLogEntity flowEntity) {
        BaseType bt = BaseType.find(BaseType.getDbName(flowEntity.getDbType())).get();

        if (flowEntity.getDbType() == DatabaseEntity.CDSR_TA_KEY) {
            String[] parts = flowEntity.getEntityName().split(Constants.NAME_SPLITTER);
            String pubName = parts.length > 2 ? parts[0] + Constants.NAME_POINT + parts[1] : parts[0];
            return bt.getProductType().buildDoi(pubName);
        }
        return bt.getProductType().buildDoi(flowEntity.getEntityName());
    }

    private static String parseLanguage(FlowLogEntity flowEntity) {
        BaseType bt = BaseType.find(BaseType.getDbName(flowEntity.getDbType())).get();
        if (flowEntity.getDbType() == DatabaseEntity.CDSR_TA_KEY) {
            String[] parts = flowEntity.getEntityName().split(Constants.NAME_SPLITTER);
            return parts[parts.length - 1];
        }
        return null;
    }

    @Override
    public String getLanguage() {
        return language;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String getError() {
        return message;
    }
}
