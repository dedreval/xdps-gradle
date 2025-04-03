package com.wiley.cms.cochrane.process.handler;

import java.io.Serializable;
import java.util.List;

import com.wiley.cms.cochrane.process.OperationManager;
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
public class OpHandler<M extends ProcessManager, Q> extends NamedHandler<M, Q> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int COUNT_PARAM = 3;

    private int systemId;
    private String systemName;

    private OperationManager.Operation op;

    public OpHandler() {
    }

    public OpHandler(String name, int systemId, String systemName) {
        super(name);
        setSystemId(systemId);
        setSystemName(systemName);
    }

    public OpHandler(OperationManager.Operation op, int systemId, String systemName) {

        super(op.name());

        setSystemId(systemId);
        setSystemName(systemName);
    }

    @Override
    protected void init(String... params) throws ProcessException {

        super.init(params);

        setSystemId(params[super.getParamCount()]);
        setSystemName(params[super.getParamCount() + 1]);
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + COUNT_PARAM;
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, getSystemId(), getSystemName());
    }

    @Override
    protected void onStartSync(ProcessVO pvo, List<ProcessPartVO> results, M manager)
            throws ProcessException {
        super.onStartSync(pvo, results, manager);
        getOperation().performOperation(this, pvo);
    }

    //@Override
    //protected void onEnd(ProcessVO pvo, OperationManager manager) {
    //    try {
    //        getOperation().onEnd(this, pvo, manager);
    //    } catch (ProcessException pe) {
    //        throw new RuntimeException(pe);
    //    }
    //}

    @Override
    public void onMessage(ProcessPartVO processPart) throws Exception {
        getOperation().performOperation(this, processPart);
    }

    @Override
    public void onMessage(ProcessVO pvo) throws Exception {
        getOperation().performOperation(this, pvo);
    }

    @Override
    public void logOnStart(ProcessVO pvo, ProcessManager manager) {
        try {
            getOperation().logOnStart(this, pvo, manager);
        } catch (ProcessException pe) {
            throw new RuntimeException(pe);
        }
    }

    @Override
    public void logOnEnd(ProcessVO pvo, ProcessManager manager) {
        try {
            getOperation().logOnEnd(this, pvo, manager);
        } catch (ProcessException pe) {
            throw new RuntimeException(pe);
        }
    }

    @Override
    public void logOnFail(ProcessVO pvo, String msg, ProcessManager manager) {
        try {
            getOperation().logOnFail(this, pvo, msg, manager);
        } catch (ProcessException pe) {
            throw new RuntimeException(pe);
        }
    }

    public void setSystemId(int packageId) {
        systemId = packageId;
    }

    public void setSystemId(String systemId) throws ProcessException {
        setSystemId(getIntegerParam(systemId));
    }

    public int getSystemId() {
        return systemId;
    }

    public void setSystemName(String name) {
        systemName = name;
    }

    public String getSystemName() {
        return systemName;
    }

    public OperationManager.Operation getOperation() throws ProcessException {
        if (op == null) {

            op = tryGetOperation(getName());

            if (op == null) {
                op = tryGetOperation(getSystemName());
            }

            if (op == null) {
                throw new ProcessException(String.format("operation %s or %s is not supported", getName(),
                        getSystemName()));
            }
        }
        return op;
    }

    private OperationManager.Operation tryGetOperation(String opName) {
        try {
            return OperationManager.Operation.valueOf(opName);
        } catch (Throwable th){
            return null;
        }
    }
}
