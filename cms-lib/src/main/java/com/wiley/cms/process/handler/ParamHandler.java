package com.wiley.cms.process.handler;

import java.io.Serializable;

import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 18.07.13
 * @param <M> Manager
 */
public class ParamHandler<M extends ProcessManager> extends ProcessHandler<M, Object> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ParamHandler.class);

    private String[] params;

    public ParamHandler() {
    }

    public ParamHandler(String paramsStr) {
        setParams(paramsStr);
    }

    public ParamHandler(String... params) {
        this.params = params;
    }

    @Override
    public int getParamCount() {
        return params == null ? 0 : params.length;
    }

    @Override
    protected void init(String... params) throws ProcessException {
        this.params = params;
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        if (params != null) {
            buildParams(sb, (Object[]) params);
        }
    }

    public void setParams(String paramsStr) {
        params = parseParamString(paramsStr);
    }

    public void setParams(String[] params) {
        this.params = params;
    }

    public String getParam(int ind) {
        return params[ind];
    }

    public ITaskExecutor createExecutor() throws Exception {
        if (getParamCount() == 0 || getExecutorName().isEmpty()) {
            throw new ProcessException("a class name to execute a task was not passed");
        }
        return ProcessHelper.createExecutor(getExecutorName().trim(), params);
    }

    private String getExecutorName() {
        return params[0];
    }

    @Override
    public boolean execute(TaskVO task) throws Exception {
        ITaskExecutor executor = task.getExecutor();
        if (executor == null) {
            executor = createExecutor();
            if (task.isFast()) {

                task.setExecutor(executor);
                LOG.info(String.format("an executor %s for %s is set", getExecutorName(), task));
            }
        }
        return executor.execute(task);
    }
}
