package com.wiley.cms.cochrane.process.handler;

import java.util.List;

import javax.jms.Queue;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.process.IWml3gConversionManager;
import com.wiley.cms.cochrane.process.Wml3gConversionManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.handler.NamedHandler;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 02.09.2014
 * @param <M> Manager
 */
public class Wml3gConversionHandler<M extends ProcessManager> extends NamedHandler<M, Object> {

    private static final long serialVersionUID = 1L;
    private static final String BY_RECORD = " selective";

    private int dbId;
    private String qualifier;
    private String logName;
    private ActivityLogEntity.EntityLevel entLvl;

    public Wml3gConversionHandler() {
    }

    public Wml3gConversionHandler(int dbId,
                                  String qualifier,
                                  String logName,
                                  ActivityLogEntity.EntityLevel entLvl) {
        super(IWml3gConversionManager.PROC_LABEL);

        this.dbId = dbId;
        this.qualifier = qualifier;
        this.logName = logName;
        this.entLvl = entLvl;
    }

    public static Wml3gConversionHandler createWml3gConversionHandler(int dbId, String dbName, String log,
        boolean byRecs, boolean prev) {

        String qualifier = dbName + " entire";
        if (byRecs) {
            qualifier += BY_RECORD;
        }
        if (prev) {
            qualifier += " with previous";
        }
        return new Wml3gConversionHandler(dbId, qualifier, CmsUtils.getLoginName(log),
                ActivityLogEntity.EntityLevel.ENTIREDB);
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + Parameter.values().length;
    }

    @Override
    protected void init(String... params) throws ProcessException {
        super.init(params);

        dbId = Integer.parseInt(params[getIdx(Parameter.DB_ID)]);
        qualifier = params[getIdx(Parameter.QUALIFIER)];
        logName = params[getIdx(Parameter.LOG_NAME)];
        entLvl = ActivityLogEntity.EntityLevel.valueOf(params[getIdx(Parameter.ENTITY_LVL)]);
    }

    private int getIdx(Parameter param) {
        return super.getParamCount() + param.ordinal();
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, dbId, qualifier, logName, entLvl);
    }

    @Override
    protected void onStartAsync(ProcessVO pvo, List<ProcessPartVO> list, M pm, Queue queue)
            throws ProcessException {
        // just to keep an old approach for ProcessType 104
        Wml3gConversionManager.Factory.getBeanInstance().startConversion(this, pvo);
    }

    public int getDbId() {
        return dbId;
    }

    public String getQualifier() {
        return qualifier;
    }

    public String getLogName() {
        return logName;
    }

    public ActivityLogEntity.EntityLevel getEntityLevel() {
        return entLvl;
    }

    public boolean byRecord() {
        return qualifier.contains(BY_RECORD);
    }

    /**
     *
     */
    enum Parameter {
        DB_ID, QUALIFIER, LOG_NAME, ENTITY_LVL;
    }
}
