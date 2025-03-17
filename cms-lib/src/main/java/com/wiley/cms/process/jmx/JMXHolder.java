package com.wiley.cms.process.jmx;

import java.lang.management.ManagementFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 9/8/2015
 */
public abstract class JMXHolder {

    private MBeanServer platformMBeanServer;
    private ObjectName objectName = null;
    private String prefix = "CMS";

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void resetPrefix() {
        prefix = null;
    }

    @PostConstruct
    public void registerInJMX() {
        try {
            String start = prefix == null ? "" : prefix + "-";
            objectName = new ObjectName(start + getClass().getSimpleName() + ":type=" + getClass().getName());
            platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            platformMBeanServer.registerMBean(this, objectName);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Problem during registration of %s into JMX: %s",
                    getClass().getName(), e));
        }
    }

    @PreDestroy
    public void unregisterFromJMX() {
        try {
            platformMBeanServer.unregisterMBean(this.objectName);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Problem during un-registration of %s manager into JMX: %s",
                    getClass().getName(), e));
        }
    }
}
