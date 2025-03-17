package com.wiley.cms.process.handler;

import java.net.URI;
import java.net.URISyntaxException;

import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessManager;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 02.07.13
 * @param <M> Manager
 */
public abstract class CallbackHandler<M extends ProcessManager> extends NamedHandler<M, Object> {
    private URI callbackURI;

    protected CallbackHandler() {
    }

    protected CallbackHandler(String label, URI callbackURI) {
        super(label);
        setCallbackURI(callbackURI);
    }

    @Override
    protected void init(String... params) throws ProcessException {
        super.init(params);
        try {
            setCallbackURI(params[super.getParamCount()]);
        } catch (Exception e) {
            throw new ProcessException(e);
        }
    }

    @Override
    protected int getParamCount() {
        return super.getParamCount() + 1;
    }

    @Override
    protected void buildParams(StringBuilder sb) {
        super.buildParams(sb);
        buildParams(sb, getCallbackURI());
    }

    public void setCallbackURI(String callbackURI) throws URISyntaxException {
        this.callbackURI = new URI(callbackURI);
    }

    public void setCallbackURI(URI callbackURI) {
        this.callbackURI = callbackURI;
    }

    public URI getCallbackURI() {
        return callbackURI;
    }
}
