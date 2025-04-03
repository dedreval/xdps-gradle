
package com.wiley.cms.notification.service;

import javax.xml.bind.annotation.XmlEnum;


/**
 * <p>Java class for levels.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;simpleType name="levels">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="NOTIFICATION"/>
 *     &lt;enumeration value="FATAL"/>
 *     &lt;enumeration value="ERROR"/>
 *     &lt;enumeration value="WARN"/>
 *     &lt;enumeration value="INFO"/>
 *     &lt;enumeration value="DEBUG"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 *
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 03.09.2014
 */
@XmlEnum
public enum Levels {

    NOTIFICATION,
    FATAL,
    ERROR,
    WARN,
    INFO,
    DEBUG;

    public String value() {
        return name();
    }

    public static Levels fromValue(String v) {
        return valueOf(v);
    }
}
