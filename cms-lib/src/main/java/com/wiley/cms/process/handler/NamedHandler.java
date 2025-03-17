package com.wiley.cms.process.handler;


import java.io.Serializable;

import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessManager;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 18.07.13
 * @param <M> Manager
 * @param <Q> Queue
*/
public class NamedHandler<M extends ProcessManager, Q> extends ProcessHandler<M, Q> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;

    public NamedHandler() {
    }

    public NamedHandler(String name) {
        setName(name);
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name == null ? super.getName() : name;
    }
}
