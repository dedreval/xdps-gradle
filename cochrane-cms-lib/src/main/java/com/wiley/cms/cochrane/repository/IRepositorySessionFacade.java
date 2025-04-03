package com.wiley.cms.cochrane.repository;

import java.io.IOException;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 */
public interface IRepositorySessionFacade {

    void setAssets(String fileName);

    String getArticleXmlFileAsString(String recordXMLPath) throws IOException;

    /**
     * @param recordXMLPath  path to xml
     * @param correctDtdPath whether the dtd path is correct, set false if need to try to check it
     * @return xml data as String
     * @throws IOException
     */
    String getArticleXmlFileAsString(String recordXMLPath, boolean correctDtdPath) throws IOException;
}
