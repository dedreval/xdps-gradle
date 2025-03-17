
package com.wiley.cms.notification.service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for notificationResult complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="notificationResult">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="message" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="resultCode" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="success" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *         &lt;element name="tryAgain" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
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
@XmlType(name = "notificationResult", propOrder = {
        "message",
        "resultCode",
        "success",
        "tryAgain"})
public class NotificationResult {

    protected String message;
    protected int resultCode;
    protected boolean success;
    protected boolean tryAgain;

    /**
     * Gets the value of the message property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the value of the message property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setMessage(String value) {
        this.message = value;
    }

    /**
     * Gets the value of the resultCode property.
     */
    public int getResultCode() {
        return resultCode;
    }

    /**
     * Sets the value of the resultCode property.
     */
    public void setResultCode(int value) {
        this.resultCode = value;
    }

    /**
     * Gets the value of the success property.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets the value of the success property.
     */
    public void setSuccess(boolean value) {
        this.success = value;
    }

    /**
     * Gets the value of the tryAgain property.
     */
    public boolean isTryAgain() {
        return tryAgain;
    }

    /**
     * Sets the value of the tryAgain property.
     */
    public void setTryAgain(boolean value) {
        this.tryAgain = value;
    }

}
