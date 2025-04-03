
package com.wiley.cms.notification.service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for processNotificationResponse complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="processNotificationResponse">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="return" type="{http://webservice.web.ntfservice.cms.wiley.com/}notificationResult"
 *         minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 03.09.2014
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "processNotificationResponse", propOrder = {
        "ret"})
public class ProcessNotificationResponse {

    @XmlElement(name = "return")
    protected NotificationResult ret;

    /**
     * Gets the value of the return property.
     *
     * @return possible object is
     * {@link NotificationResult }
     */
    public NotificationResult getReturn() {
        return ret;
    }

    /**
     * Sets the value of the return property.
     *
     * @param value allowed object is
     *              {@link NotificationResult }
     */
    public void setReturn(NotificationResult value) {
        this.ret = value;
    }

}
