package com.wiley.cms.process.handler;


import java.io.Serializable;

import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessManager;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 18.07.13
 * @param <M> Manager
 */
public class SimpleHandler<M extends ProcessManager> extends ProcessHandler<M, Object> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String label;

    public SimpleHandler() {
    }

    public SimpleHandler(String param) {
        setLabel(param);
    }

    @Override
    protected int getParamCount() {
        return 1;
    }

    @Override
    protected void init(String... params) throws ProcessException {
        setLabel(params[super.getParamCount()]);
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        buildParams(sb, getLabel());
    }

    public void setLabel(String param) {
        label = param;
    }

    public String getLabel() {
        return label;
    }
}
