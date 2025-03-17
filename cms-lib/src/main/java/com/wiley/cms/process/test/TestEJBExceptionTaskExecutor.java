package com.wiley.cms.process.test;

import javax.ejb.EJBException;

import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/12/2014
 */
public class TestEJBExceptionTaskExecutor implements ITaskExecutor {
    public boolean execute(TaskVO task) throws Exception {
        throw new EJBException("it's a dummy test exception");
    }
}
