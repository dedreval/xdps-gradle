package com.wiley.cms.cochrane.cmanager.contentworker;

import java.io.File;

import com.wiley.cms.cochrane.cmanager.ContentManagerFactory;
import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.process.handler.ParamHandler;
import com.wiley.cms.process.task.ITaskExecutor;
import com.wiley.cms.process.task.TaskVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/31/2019
 */
public class PackageUploader implements ITaskExecutor {
    public static final String UPLOAD_PACKAGE = "Package Upload";

    public boolean execute(TaskVO task) throws Exception {

        ParamHandler ph = (ParamHandler) task.getHandler();

        String packageName = ph.getParam(1);
        IContentManager manager = ContentManagerFactory.getFactory().getInstance();

        manager.newPackageReceived(new File(packageName).toURI());

        return true;
    }
}
