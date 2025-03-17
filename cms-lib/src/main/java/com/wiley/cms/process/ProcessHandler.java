package com.wiley.cms.process;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jms.Queue;

import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.06.13
 * @param <M> Process Manager
 * @param <Q> Process Queue
 */
public abstract class ProcessHandler<M extends ProcessManager, Q> implements ITaskExecutor, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ProcessHandler.class);
    private static final String DELIMITER = "\\s";

    public String getName() {

        String ret = getClass().getSimpleName();
        int ind = ret.indexOf("Handler");
        if (ind != -1) {
            ret = ret.substring(0, ind);
        }
        return ret;
    }

    public void setName(String name) {
    }

    protected void take(ProcessHandler from, ProcessVO fromPvo) {
        // do nothing by default
    }

    public void pass(ProcessVO pvo, ProcessHandler to) {
        to.take(this, pvo);
    }

    public static ProcessHandler createHandler(String handlerParams) {
        String[] params = parseParamString(handlerParams);
        return createHandler(params[params.length - 1], params);
    }

    protected static ProcessHandler createHandler(String name, String[] params) {
        try {
            ProcessHandler handler = createProcessHandler(name);
            handler.init(params);
            return handler;

        } catch (Throwable th) {
            throw new RuntimeException(th);
        }
    }

    protected static <H extends ProcessHandler> H createHandler(String name, String[] params, Class<H> check)
            throws ProcessException {
        return castProcessHandler(createHandler(name, params), check);
    }

    //private static ProcessHandler createHandler(String name, String[] params) {
    //    try {
    //        Class cl = ProcessHelper.getSupportingClass(name);
    //        ProcessHandler handler = (ProcessHandler) cl.newInstance();
    //        handler.init(params);
    //        return handler;

    //    } catch (Exception e) {
    //        LOG.error(e);
                /*try {
                    ModuleIdentifier mi1 = ModuleIdentifier.create("deployment.CochraneCMS.ear.CochraneCMS.jar");
                    Class cl = Module.loadClassFromCallerModuleLoader(mi1, params[params.length - 1]);
                    ProcessHandler handler = (ProcessHandler) cl.newInstance();
                    handler.init(params);
                    return handler;
                } catch (Exception e) {
                }*/
    //        return new ParamHandler(params);
    //    } catch (Throwable th) {
    //        LOG.error(th);
    //        throw new RuntimeException(th);
    //    }
    //}

    public static ProcessHandler createHandler(ProcessType type, ProcessHandler prevHandler) {
        try {
            String newHandlerName = type.getHandlerName();
            return newHandlerName == null ? prevHandler : ProcessHandler.createProcessHandler(newHandlerName);
            //handler.take(from.pass(fromPvo, handler), fromPvo);
            //from.pass(fromPvo, handler);

            //return handler;

        } catch (Throwable th) {
            throw new RuntimeException(th);
        }
    }

    protected static ProcessHandler createProcessHandler(String name) throws Exception {
        ClassLoader cld = Thread.currentThread().getContextClassLoader();
        Class cl;
        try {
            cl = cld.loadClass(name);

        } catch (ClassNotFoundException ce) {
            cl = ProcessHelper.getSupportingClass(name);
            if (cl == null) {
                LOG.error(ce);
                throw new Exception(String.format(
                    "cannot create a process handler, %s class is not supported, error: %s", name, ce.getMessage()));
            }
        }
        return (ProcessHandler) cl.newInstance();
    }

    public static <T> T getProcessHandler(String handlerParams, Class<T> cl) {
        try {
            return castProcessHandler(createHandler(handlerParams), cl);
        } catch (ProcessException pe) {
            LOG.error(pe.getMessage(), pe);
        }
        return null;
    }

    public static <T> T getProcessHandler(ProcessHandler handler, Class<T> cl) {
        try {
            return castProcessHandler(handler, cl);
        } catch (ProcessException pe) {
            LOG.warn(pe.getMessage(), pe);
        }
        return null;
    }

    public static <T> T castProcessHandler(ProcessHandler handler, Class<T> cl) throws ProcessException {
        if (!cl.isInstance(handler)) {
            throw new ProcessException(cl.getName() + " expected!");
        }
        return cl.cast(handler);
    }

    public static ProcessException createNullMandatoryParamError(ProcessVO pvo, String paramName) {
        return new ProcessException(String.format("mandatory param %s is null, %s", paramName, pvo));
    }

    public boolean execute(TaskVO task) throws Exception {
        throw new UnsupportedOperationException("this handler doesn't support task executing");
    }

    public void onMessage(ProcessVO pvo, ProcessPartVO processPart) throws Exception {
        onMessage(processPart);
    }

    public void onMessage(ProcessPartVO processPart) throws Exception {
    }

    public void onMessage(ProcessVO pvo) throws Exception {
    }

    public void onExternalMessage(ProcessVO pvo, Q queue) throws ProcessException {
    }

    protected void validate(ProcessVO pvo) throws ProcessException {
    }

    protected void onStart(ProcessVO pvo, M manager) throws ProcessException {
        validate(pvo);
        manager.onStart(this, pvo); // just to support an old approach
    }

    protected void onStartSync(ProcessVO pvo, List<ProcessPartVO> inputData, M manager)  throws ProcessException {
        validate(pvo);
    }

    protected void onStartAsync(ProcessVO pvo, List<ProcessPartVO> inputData, M manager, Queue queue)
            throws ProcessException {
        validate(pvo);
        ProcessType pt = pvo.getType();
        if (inputData != null) {
            manager.sendProcessParts(pvo, pt, inputData, pt.capacity(), pt.countInMessage(), queue);

        } else if (pt.isCreatePartsBefore()) {
            manager.sendProcessParts(pvo, pt, pt.capacity(), pt.countInMessage(), queue);

        } else {
            manager.sendProcess(pvo, pt, queue);
        }
    }

    protected void onEnd(ProcessVO pvo, M manager) {
    }

    protected void init(String... params) throws ProcessException {
        if (params.length < getParamCount()) {
            throw new ProcessException("invalid params count: " + params.length + ", but required: " + getParamCount());
        }
    }

    protected int getParamCount() {
        return 0;
    }

    protected void buildParams(StringBuilder sb) {
    }

    public final String getParamString() {

        StringBuilder sb =  new StringBuilder();
        buildParams(sb);
        sb.append(getClass().getName());

        return sb.toString();
    }

    protected final String[] getParams() {
        return getParamString().split(DELIMITER);
    }

    protected void buildParams(StringBuilder sb, Object... params) {
        for (Object obj: params) {
            sb.append(obj).append("\n");
        }
    }

    public void logOnStart(ProcessVO pvo, ProcessManager manager) {
        manager.logProcessStart(pvo, 0, getName(), pvo.getId());
    }

    public void logOnEnd(ProcessVO pvo, ProcessManager manager) {
        manager.logProcessEnd(pvo, 0, getName(), pvo.getId());
    }

    public void logOnFail(ProcessVO pvo, String msg, ProcessManager manager) {
        manager.logProcessFail(pvo, 0, getName(), pvo.getId(), msg);
    }

    public IProcessStats writeStats(IProcessStats stats, ProcessSupportVO processSupport) {
        return stats;
    }

    public IProcessStats readStats(ProcessSupportVO processSupport) {
        //if (notNull) {
        //    throw new RuntimeException("stats reading stats is not realized for " + processSupport);
        //}
        return null;
    }

    protected static String[] parseParamString(String paramStr) {
        return paramStr == null ? new String[0] : paramStr.split(DELIMITER);
    }

    public static Map<Integer, Integer> getInt2IntMapParam(String paramName, ProcessPartVO processPart)
            throws ProcessException {
        Map<Integer, Integer> ret = ProcessHelper.getInt2IntMapParam(paramName, processPart.parametersMap);
        if (ret == null || ret.isEmpty()) {
            throw new ProcessException(String.format("an empty '%s' param", paramName));
        }
        return ret;
    }

    public static List<Integer> getIdsParam(ProcessPartVO processPart) throws ProcessException {
        List<Integer> ids = ProcessHelper.getIdsParam(processPart.parametersMap);
        if (ids == null && processPart.uri != null) {
            ids = new ArrayList<>();
            ids.add(Integer.valueOf(processPart.uri));
        }
        if (ids == null || ids.isEmpty()) {
            throw new ProcessException("no identifiers");
        }
        LOG.debug("process part[%d] [%d] starts for %d records", processPart.getId(), processPart.parentId, ids.size());
        return ids;
    }

    public static Integer getIntegerParam(String param) throws ProcessException {
        try {
            return Integer.valueOf(param);
        } catch (NumberFormatException ne) {
            throw new ProcessException(ne.getMessage());
        }
    }

    public static Boolean getBooleanParam(String param) throws ProcessException {
        try {
            return Boolean.valueOf(param);
        } catch (NumberFormatException ne) {
            throw new ProcessException(ne.getMessage());
        }
    }
}
