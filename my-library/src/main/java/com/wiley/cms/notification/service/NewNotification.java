
package com.wiley.cms.notification.service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.ArrayList;
import java.util.List;

import com.wiley.tes.util.res.ResourceStrId;


/**
 * <p>Java class for newNotification complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="newNotification">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="body" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="level" type="{http://webservice.web.ntfservice.cms.wiley.com/}levels" minOccurs="0"/>
 *         &lt;element name="messageDate" type="{http://www.w3.org/2001/XMLSchema}dateTime" minOccurs="0"/>
 *         &lt;element name="profileName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="subject" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="tags" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 * @author <a href='mailto:aasadulin@wiley.com'>Alexander Asadulin</a>
 * @version 03.09.2014
 */
@XmlRootElement(name = "newNotification")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "newNotification", propOrder = {
        "body",
        "level",
        "messageDate",
        "profileName",
        "subject",
        "tags"})
public class NewNotification extends ResourceStrId implements Cloneable {

    protected String body;
    protected Levels level;
    protected XMLGregorianCalendar messageDate;
    protected String profileName;
    protected String subject;
    @XmlElement(nillable = true)
    protected List<String> tags;

    public NewNotification() {
        setId("");
    }

    /**
     * Gets the value of the body property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getBody() {
        return body;
    }

    /**
     * Sets the value of the body property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setBody(String value) {
        this.body = value;
    }

    /**
     * Gets the value of the level property.
     *
     * @return possible object is
     * {@link Levels }
     */
    public Levels getLevel() {
        return level;
    }

    /**
     * Sets the value of the level property.
     *
     * @param value allowed object is
     *              {@link Levels }
     */
    public void setLevel(Levels value) {
        this.level = value;
    }

    /**
     * Gets the value of the messageDate property.
     *
     * @return possible object is
     * {@link XMLGregorianCalendar }
     */
    public XMLGregorianCalendar getMessageDate() {
        return messageDate;
    }

    /**
     * Sets the value of the messageDate property.
     *
     * @param value allowed object is
     *              {@link XMLGregorianCalendar }
     */
    public void setMessageDate(XMLGregorianCalendar value) {
        this.messageDate = value;
    }

    /**
     * Gets the value of the profileName property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Sets the value of the profileName property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setProfileName(String value) {
        this.profileName = value;
    }

    /**
     * Gets the value of the subject property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the value of the subject property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setSubject(String value) {
        this.subject = value;
    }

    /**
     * Gets the value of the tags property.
     * <p/>
     * <p/>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the tags property.
     * <p/>
     * <p/>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTags().add(newItem);
     * </pre>
     * <p/>
     * <p/>
     * <p/>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     */
    public List<String> getTags() {
        if (tags == null) {
            tags = new ArrayList<String>();
        }
        return this.tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * Returns a shallow copy of this <tt>NewNotification</tt> instance.
     * @return a clone of this <tt>NewNotification</tt> instance.
     */
    @Override
    public NewNotification clone() {
        try {
            NewNotification notif = (NewNotification) super.clone();
            notif.setBody(body);
            notif.setLevel(level);
            notif.setMessageDate(messageDate);
            notif.setProfileName(profileName);
            notif.setSubject(subject);
            notif.setTags(tags);

            return notif;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Failed to clone object of " + NewNotification.class, e);
        }
    }
}
