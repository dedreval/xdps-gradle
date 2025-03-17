package com.wiley.cms.cochrane.process.handler;

import java.io.Serializable;
import java.util.List;

import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.process.IConversionManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessPartVO;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.handler.NamedHandler;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.07.13
 *
 * @param <M>
 * @param <Q>
 */
public class ConversionRecordHandler<M extends ProcessManager, Q> extends NamedHandler<M, Q> implements Serializable {

    public static final int WITH_SPECIAL = 1;
    public static final int ONLY_SPECIAL = 2;

    private static final long serialVersionUID = 1L;
    private static final int COUNT_PARAM = 3;

    private String dbName;
    private boolean hasPrevious;

    private int convertTA = 0;

    public ConversionRecordHandler() {
    }

    public ConversionRecordHandler(String label, String dbName, boolean hasPrevious, int convertTA) {
        this(label, dbName, hasPrevious);
        this.convertTA = convertTA;
    }

    public ConversionRecordHandler(String label, String dbName, boolean hasPrevious) {

        super(label);
        this.dbName = dbName;
        this.hasPrevious = hasPrevious;
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + COUNT_PARAM;
    }

    @Override
    protected void init(String... params) throws ProcessException {

        super.init(params);

        setDbName(params[super.getParamCount()]);
        setPrevious(params[super.getParamCount() + 1]);
        setConvertTA(params[super.getParamCount() + COUNT_PARAM - 1]);
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, getDbName(), hasPrevious(), convertTA);
    }

    @Override
    public void onMessage(ProcessPartVO processPart) throws Exception {
        process(processPart, CochraneCMSBeans.getConversionManager());
    }

    public void process(ProcessPartVO processPart, IConversionManager convManager) throws Exception {
        List<Integer> ids = getIdsParam(processPart);
        convManager.performRevmanConversion(processPart.parentId, dbName, ids, hasPrevious(),
                isConvertTA(), isOnlyConvertTA());
    }

    @Override
    public void logOnStart(ProcessVO pvo, ProcessManager manager) {
        int event = ILogEvent.CONVERSION_REVMAN_STARTED;
        manager.logProcessStart(pvo, event, getDbName(), 0);
    }

    public void logOnEnd(ProcessVO pvo, ProcessManager manager) {
        int event = ILogEvent.REVMAN_DATA_CONVERTED;
        manager.logProcessEnd(pvo, event, getDbName(), 0);
    }

    public void logOnFail(ProcessVO pvo, String msg, ProcessManager manager) {
        int event = ILogEvent.CONVERSION_REVMAN_FAILED;
        manager.logProcessFail(pvo, event, getDbName(), 0, msg);
    }

    public boolean isOnlyConvertTA() {
        return convertTA == ONLY_SPECIAL;
    }

    public boolean isConvertTA() {
        return convertTA == WITH_SPECIAL || isOnlyConvertTA();
    }

    public String getDbName() {
        return dbName;
    }

    public boolean hasPrevious() {
        return hasPrevious;
    }

    private void setDbName(String name) {
        dbName = name;
    }

    private void setPrevious(boolean previous) {
        hasPrevious = previous;
    }

    private void setConvertTA(String value) throws ProcessException {
        convertTA = getIntegerParam(value);
    }

    private void setPrevious(String previous) throws ProcessException {
        setPrevious(getBooleanParam(previous));
    }
}
