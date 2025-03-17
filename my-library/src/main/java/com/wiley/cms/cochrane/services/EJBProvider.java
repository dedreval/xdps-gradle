package com.wiley.cms.cochrane.services;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.tes.util.Logger;

import javax.ejb.EJB;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Type;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 30.03.2017
 */
@Provider
public class EJBProvider implements InjectableProvider<EJB, Type> {

    private static final Logger LOG = Logger.getLogger(EJBProvider.class);

    public ComponentScope getScope() {
        return ComponentScope.Singleton;
    }

    public Injectable getInjectable(ComponentContext cc, EJB ejbAnnotation, Type type) {
        if (!(type instanceof Class)) {
            return null;
        }

        Class cl = (Class) type;
        String beanName = (ejbAnnotation.beanName() != ""
                ? ejbAnnotation.beanName()
                : cl.getName());
        String lookupName = CochraneCMSPropertyNames.buildLookupName(beanName, cl);
        Injectable<Object> injectable = null;

        try {
            Context ic = new InitialContext();
            final Object o = ic.lookup(lookupName);

            injectable = new Injectable<Object>() {
                public Object getValue() {
                    return o;
                }
            };
        } catch (Exception e) {
            LOG.error(
                    String.format("Failed to lookup injectable object %s by name %s", beanName, lookupName),
                    e);
        }
        return injectable;
    }
}
