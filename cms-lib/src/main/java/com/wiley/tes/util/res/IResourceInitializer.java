package com.wiley.tes.util.res;

import java.net.URL;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 28.03.13
 */
public interface IResourceInitializer {

    String INITIALIZER_CLASS = "com.wiley.tes.util.res.initializer";

    URL[] getResourceRoot();

    void initialize(ResourceManager loader);
}

