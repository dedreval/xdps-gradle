package com.wiley.cms.cochrane.process.handler;

import java.io.Serializable;

import com.wiley.cms.cochrane.activitylog.IActivityLog;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.DbUtils;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 3/4/2018
 *
 * @param <M>
 * @param <Q>
 */
public class DbHandler<M extends ProcessManager, Q> extends ProcessHandler<M, Q> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int COUNT_PARAM = 7;

    protected String dbName;
    private String dfName;

    private int issue = 0;
    private int dbId;
    private int dfId;
    private int issueId;
    private boolean previous;

    public DbHandler() {
    }

    public DbHandler(int issue, String dbName, int dbId, boolean previous) {
        this(issue, dbName, dbId, DbEntity.NOT_EXIST_ID, "", DbEntity.NOT_EXIST_ID, previous);
    }

    public DbHandler(int issue, String dbName, int dbId, int dfId, int issueId) {
        this(issue, dbName, dbId, dfId, "", issueId, false);
    }

    public DbHandler(int issue, String dbName, int dbId, int dfId, String dfName, int issueId) {
        this(issue, dbName, dbId, dfId, dfName, issueId, false);
    }

    private DbHandler(int issue, String dbName, int dbId, int dfId, String dfName, int issueId, boolean previous) {
        setIssue(issue);
        setDbName(dbName);
        setDbId(dbId);
        setDfId(dfId);
        setIssueId(issueId);
        setPrevious(previous);
        setDfName(dfName);
    }

    @Override
    protected void init(String... params) throws ProcessException {

        super.init(params);
        int ind = 1;
        setIssue(params[super.getParamCount()]);
        setDbName(params[super.getParamCount() + ind++]);
        setDbId(params[super.getParamCount() + ind++]);
        setDfId(params[super.getParamCount() + ind++]);
        setIssueId(params[super.getParamCount() + ind++]);
        setPrevious(params[super.getParamCount() + ind++]);
        setDfName(params[super.getParamCount() + ind]);
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + COUNT_PARAM;
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, getIssue(), getDbName(), getDbId(), getDfId(), getIssueId(), hasPrevious(), getDfName());
    }

    @Override
    protected void take(ProcessHandler from, ProcessVO fromPvo) {
        if (from instanceof DbHandler)  {
            DbHandler ph = (DbHandler) from;

            setIssue(ph.getIssue());
            setDbName(ph.getDbName());
            setDbId(ph.getDbId());
            setDfId(ph.getDfId());
            setIssueId(getIssueId());
            setPrevious(ph.hasPrevious());
            setDfName(ph.getDfName());
        }
        super.take(from, fromPvo);
    }

    public final void setIssue(int issue) {
        this.issue = issue;
    }

    public final void setIssue(String issue) throws ProcessException {
        setIssue(getIntegerParam(issue));
    }

    public final int getIssue() {
        return issue;
    }

    public final void setDbId(int dbId) {
        this.dbId = dbId;
    }

    public final void setDbId(String dbId) throws ProcessException {
        setDbId(getIntegerParam(dbId));
    }

    public final int getDbId() {
        return dbId;
    }

    public final void setDfId(int dfId) {
        this.dfId = dfId;
    }

    public final void setDfId(String dfId) throws ProcessException {
        setDfId(getIntegerParam(dfId));
    }

    public final int getDfId() {
        return dfId;
    }

    public final void setIssueId(int issueId) {
        this.issueId = issueId;
    }

    public final void setIssueId(String issueId) throws ProcessException {
        setIssueId(getIntegerParam(issueId));
    }

    public final int getIssueId() {
        return issueId;
    }

    public final void setDbName(String name) {
        dbName = name;
    }

    public final String getDbName() {
        return dbName;
    }

    public final void setDfName(String name) {
        dfName = name;
    }

    public final String getDfName() {
        return dfName;
    }

    public final boolean hasPrevious() {
        return previous;
    }

    public final void setPrevious(boolean value) {
        previous = value;
    }

    public final boolean isEntire() {
        return !DbUtils.exists(issueId);
    }

    public final ContentLocation getContentLocation() {
        return isEntire() ? ContentLocation.ENTIRE : ContentLocation.ISSUE;
    }

    private void setPrevious(String previous) throws ProcessException {
        setPrevious(getBooleanParam(previous));
    }

    protected void updateDeliveryFileOnFinish() {

        DbStorageFactory.getFactory().getInstance().updateAllAndRenderedRecordCount(getDbId());
        ResultStorageFactory.getFactory().getInstance().setDeliveryFileStatus(
                dfId, IDeliveryFileStatus.STATUS_PACKAGE_LOADED, false);
    }

    protected void updateDeliveryFileOnRecordCreate(String packageName, int type, String owner,
                                                    int size, IResultsStorage rs, IActivityLog logger) {

        DbStorageFactory.getFactory().getInstance().updateAllRecordCount(dbId);
        rs.setDeliveryFileStatus(dfId, IDeliveryFileStatus.STATUS_RECORDS_CREATED, true, type);
        logger.logDeliveryFile(ILogEvent.RECORDS_CREATED, dfId, packageName, owner, "" + size);
    }
}