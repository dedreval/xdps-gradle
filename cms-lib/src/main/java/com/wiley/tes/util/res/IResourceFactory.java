package com.wiley.tes.util.res;

import org.w3c.dom.Node;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 28.03.13
 */
public interface IResourceFactory {

    Resource createResource(Node node) throws Exception;

    Resource createResource(String str) throws Exception;

    IResourceContainer getResourceContainer();

    void saveResource(Resource res, String fileName) throws Exception;

    String convertResourceToString(Resource res) throws Exception;
}

