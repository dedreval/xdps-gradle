package com.wiley.cms.cochrane.process.handler;

import java.io.Serializable;


import com.wiley.cms.cochrane.process.AcceptRenderQueue;
import com.wiley.cms.cochrane.process.EntireRenderingManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.handler.NamedHandler;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 */
public class RenderingRecordHandler extends NamedHandler<ProcessManager, AcceptRenderQueue>
    implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int COUNT_PARAM = 3;

    private String dbName;
    private int planId;
    private boolean hasPrevious;

    public RenderingRecordHandler() {
    }

    public RenderingRecordHandler(String label, String dbName, int planId, boolean hasPrevious) {

        super(label);
        this.dbName = dbName;
        this.planId = planId;
        this.hasPrevious = hasPrevious;
    }

    @Override
    public void onExternalMessage(ProcessVO pvo, AcceptRenderQueue queue) throws ProcessException {
        queue.processMessage(this, pvo);
    }

    @Override
    public void onMessage(ProcessVO pvo) throws Exception {
        EntireRenderingManager.Factory.getFactory().getInstance().startRendering(this, pvo);
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + COUNT_PARAM;
    }

    @Override
    protected void onEnd(ProcessVO pvo, ProcessManager manager) {
        EntireRenderingManager.Factory.getFactory().getInstance().endRendering(this, pvo);
    }

    @Override
    protected void init(String... params) throws ProcessException {

        super.init(params);

        setDbName(params[super.getParamCount()]);
        setPlanId(params[super.getParamCount() + 1]);
        setPrevious(params[super.getParamCount() + 2]);
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, getDbName(), getPlanId(), hasPrevious());
    }

    public String getDbName() {
        return dbName;
    }

    public int getPlanId() {
        return planId;
    }

    public boolean hasPrevious() {
        return hasPrevious;
    }

    private void setDbName(String name) {
        dbName = name;
    }

    private void setPlanId(int planId) {
        this.planId = planId;
    }

    private void setPlanId(String planId) throws ProcessException {
        setPlanId(getIntegerParam(planId));
    }

    private void setPrevious(boolean previous) {
        hasPrevious = previous;
    }

    private void setPrevious(String previous) throws ProcessException {
        setPrevious(getBooleanParam(previous));
    }
}
