package com.wiley.cms.process;

import java.io.Serializable;

import com.wiley.cms.process.handler.NamedHandler;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 3/4/2018
 *
 * @param <H>
 * @param <M>
 * @param <Q>
 */
public abstract class FunctionalHandler
        <H extends ProcessHandler, M extends ProcessManager, Q> extends NamedHandler<M, Q> implements Serializable {
    private static final long serialVersionUID = 1L;

    private H contentHandler;

    public FunctionalHandler() {
    }

    public FunctionalHandler(H handler) {
        setContentHandler(handler);
    }

    protected abstract Class<H> getTClass();

    protected void setContentHandler(H handler) {
        contentHandler = handler;
    }

    public H getContentHandler() {
        return contentHandler;
    }

    @Override
    public void pass(ProcessVO pvo, ProcessHandler to) {
        to.take(getContentHandler(), pvo);
    }

    @Override
    protected void take(ProcessHandler from, ProcessVO fromPvo) {
        if (contentHandler == null) {
            setContentHandler(getTClass().cast(from));
        }
    }

    @Override
    protected void init(String... params) throws ProcessException {
        setContentHandler(createHandler(params[params.length - 2 - getFunctionalParamCount()], params, getTClass()));
    }

    protected int getFunctionalParamCount() {
        return 0;
    }

    @Override
    protected int getParamCount() {
        return contentHandler.getParamCount() + 1;
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        contentHandler.buildParams(sb);
        buildParams(sb, contentHandler.getClass().getName());
    }

    @Override
    protected void validate(ProcessVO pvo) throws ProcessException {
        contentHandler.validate(pvo);
    }
}

