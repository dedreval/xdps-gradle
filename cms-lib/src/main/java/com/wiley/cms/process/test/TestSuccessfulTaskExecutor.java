package com.wiley.cms.process.test;

import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/12/2014
 */
public class TestSuccessfulTaskExecutor implements ITaskExecutor {
    public boolean execute(TaskVO task) throws Exception {
        return true;
    }
}
