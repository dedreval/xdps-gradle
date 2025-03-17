package com.wiley.cms.process;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.wiley.tes.util.Logger;

/**
 * @param <T> Bean Factory
 * @author <a href='mailto:sgulin@wiley.com'>Svyatoslav Gulin</a>
 * @version 22.11.2011
 */
public class AbstractBeanFactory<T> extends AbstractFactory<T> {
    private static final Logger LOG = Logger.getLogger(AbstractBeanFactory.class);

    private final String lookupName;

    protected AbstractBeanFactory(String lookupName) {
        this.lookupName = lookupName;
    }

    protected AbstractBeanFactory(String module, String beanImpl, Class<T> beanInterface) {
        lookupName = buildGlobalJNDIName(module, module, beanImpl, beanInterface);
    }

    @Override
    protected void init() {
        try {
            InitialContext ctx = new InitialContext();
            bean = (T) ctx.lookup(lookupName);
        } catch (NamingException e) {
            LOG.error(e, e);
        }
    }

    public static String buildGlobalJNDIName(String earAndModule, String beanImpl, Class beanInterface) {
        return buildGlobalJNDIName(earAndModule, earAndModule, beanImpl, beanInterface);
    }

    public static String buildGlobalJNDIName(String ear, String module, String beanImpl, Class beanInterface) {
        return String.format("java:global/%s/%s/%s!%s", ear, module, beanImpl, beanInterface.getName());
    }

    public static <I> I lookup(String ear, String module, String beanImpl, Class<I> beanInterface)
        throws NamingException {
        String lookup = buildGlobalJNDIName(ear, module, beanImpl, beanInterface);
        InitialContext ctx = new InitialContext();
        return (I) ctx.lookup(lookup);
    }

    public static <I> I lookup(String lookup) throws NamingException {
        InitialContext ctx = new InitialContext();
        return (I) ctx.lookup(lookup);
    }

    public static synchronized <I> AbstractBeanFactory<I> getFactory(String lookup) {
        AbstractBeanFactory ret = new AbstractBeanFactory(lookup);
        ret.init();
        return ret;
    }
}
