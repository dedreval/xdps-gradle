package com.wiley.cms.converter.services;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 31.10.2012
 */
public class ConversionProcessFactory extends AbstractBeanFactory<IConversionProcess> {

    private static final ConversionProcessFactory INSTANCE = new ConversionProcessFactory();

    private ConversionProcessFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("ConversionProcessImpl", IConversionProcess.class));
    }

    public static ConversionProcessFactory getFactory() {
        return INSTANCE;
    }

}
