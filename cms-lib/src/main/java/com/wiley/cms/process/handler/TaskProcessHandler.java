package com.wiley.cms.process.handler;

import java.io.Serializable;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessException;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessManager;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.task.TaskVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/21/2015
 */
public class TaskProcessHandler extends ParamHandler implements Serializable {
    private static final long serialVersionUID = 1L;

    public TaskProcessHandler() {
    }

    public TaskProcessHandler(int procId, String procManagerLookUp) {
        super("" + procId, procManagerLookUp);
    }

    @Override
    protected void onStart(ProcessVO pvo, ProcessManager manager) throws ProcessException {
        try {
            IProcessManager pm = (IProcessManager) new InitialContext().lookup(getProcManagerLookUp());
            pm.startProcess(getProcId());
        } catch (NamingException ne) {
            throw new ProcessException(ne.getMessage(), getProcId());
        }
    }

    @Override
    public boolean execute(TaskVO task) throws Exception {

        IProcessManager pm = (IProcessManager) new InitialContext().lookup(getProcManagerLookUp());
        ProcessVO pvo = pm.startProcess(getProcId());
        if (pvo != null && pvo.getState().isSuccessful() && task.isRepeatable()) {
            task.setSchedule(null);
        }
        return true;
    }

    public String getProcStr()  {
        return getParam(0);
    }

    public int getProcId() throws ProcessException {
        return ProcessHandler.getIntegerParam(getProcStr());
    }

    public String getProcManagerLookUp() {
        return getParam(1);
    }
}
