package com.wiley.cms.process;

import java.io.Serializable;
import java.util.Map;

import com.wiley.cms.process.entity.ProcessPartEntity;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 04.07.13
 */
public class ProcessPartVO extends ProcessSupportVO implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int parentId;
    public final String uri;
    public Map<String, String> parametersMap;

    public ProcessPartVO(ProcessPartEntity pe) {
        this(pe.getId(), pe.getParent().getId(), pe.getUri(), pe.getState(),
                ProcessHelper.buildParametersMap(pe.getParams()));
        setMessage(pe.getMessage());
    }

    public ProcessPartVO(int id, int parentId, ProcessPartVO pvo) {
        this(id, parentId, pvo.uri, pvo.getState(), pvo.parametersMap);
    }

    public ProcessPartVO(int id, int parentId, String uri, String params, ProcessState state) {
        this(id, parentId, uri, state, ProcessHelper.buildParametersMap(params));
    }

    private ProcessPartVO(int id, int parentId, String uri, ProcessState state, Map<String, String> paramMap) {
        super(id);
        this.parentId = parentId;
        this.uri = uri;
        setState(state);
        parametersMap = paramMap;
    }

    public final void setRedelivered() {
        setMessage(ProcessState.RESTARTED);
    }

    public final boolean isRedelivered() {
        return ProcessState.RESTARTED.equals(getMessage());
    }

    @Override
    public String toString() {
        return String.format("%s [%d]-[%d]", uri, parentId, getId());
    }
}
