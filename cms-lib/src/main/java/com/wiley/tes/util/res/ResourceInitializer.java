package com.wiley.tes.util.res;

import java.net.MalformedURLException;
import java.net.URL;

import com.wiley.cms.process.res.ProcessType;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 1/25/2018
 */
public abstract class ResourceInitializer {

    private static final String RES_FOLDER = "res";

    public void initialize(ResourceManager loader)  {

        Property.register(loader);
        Settings.register(loader);
        ProcessType.register(loader);
    }

    protected static URL getUrl(String path) throws MalformedURLException {
        return new URL(path);
    }

    protected static String getResPath(String path) {
        return path + RES_FOLDER;
    }
}
