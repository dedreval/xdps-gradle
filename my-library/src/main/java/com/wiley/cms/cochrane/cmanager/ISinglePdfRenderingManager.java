package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.converter.services.ConversionData;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 21.03.2017
 */
public interface ISinglePdfRenderingManager {

    byte[] renderPdf(ConversionData rndData,
                     SinglePdfRenderingManager.RenderingParams rndParams) throws Exception;
}
