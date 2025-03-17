package com.wiley.cms.process;

/**
 * @param <T> Resource Factory
 * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
 * @version 22.11.2011
 */
public abstract class AbstractFactory<T> {

    protected T bean;

    protected abstract void init();

    public T get() {
        return bean;
    }

    public synchronized T getInstance() {
        if (bean == null) {
            init();
        }
        return bean;
    }

    public synchronized void reload() {
        init();
    }
}
