package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.utils.ErrorInfo;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/30/2016
 */

@XmlAccessorType(XmlAccessType.NONE)
public class SemanticoMsg extends TransferMsg.MessageId implements ITibcoMessageData, Serializable {
    private static final long serialVersionUID = 1L;

    private final HWMsg hwMsg = new HWMsg();

    public SemanticoMsg() {
    }

    public SemanticoMsg(int transactionId, String path, String type, String freq, String clientId, String siteId,
                        boolean disableStaticContent) {

        this(transactionId, path, type, freq, disableStaticContent);

        setClientId(clientId);
        setSiteId(siteId);
    }

    public SemanticoMsg(int transactionId, String path, String type, String freq, boolean disableStaticContent) {
        this(buildMessageSid(transactionId), path, type, freq, disableStaticContent);
    }

    public SemanticoMsg(String fullTransactionSid, String path, String type, String freq,
                        boolean disableStaticContent) {
        setMsgId(fullTransactionSid);
        setJmsNameOrFilePath(path);
        setType(type);
        setFrequency(freq);
        setStaticContentDisabled(disableStaticContent);
    }

    public static boolean hasOurPrefix(String sid) {
        if (sid != null) {
            String[] parts = sid.split("_");
            if (parts.length == 2) {
                String system = PublishProfile.PUB_PROFILE.get().getId();
                return parts[0].equals(system);
            }
        }
        return true;  // try to take any suspicious messages to prevent saving them up ito the queue
    }

    public static String buildMessageSid(int transactionId) {
        return PublishProfile.PUB_PROFILE.get().getId() + "_" + transactionId;
    }

    public static int parseMessageId(String sid) throws Exception {

        String[] parts = sid.split("_");
        if (parts.length != 2) {
            throw new Exception(String.format(
                 "messageId=%s should have [prefix]_[number] format and could not be parsed", sid));
        }
        String system = PublishProfile.PUB_PROFILE.get().getId();
        if (!parts[0].equals(system)) {

            throw new CmsException(new ErrorInfo(null, ErrorInfo.Type.WRONG_SEMANTICO_SID,
                    String.format("messageId=%s doesn't belong to %s", sid, system)));
        }
        try {
            return Integer.valueOf(parts[1]);

        } catch (NumberFormatException ne) {
            throw new Exception(String.format("messageId=%s could not be parsed as a number", sid));
        }
    }

    @XmlElement(name = "DisableStaticContent")
    public boolean getStaticContentDisabled() {
        return hwMsg.getDisableStaticContent();
    }

    public void setStaticContentDisabled(boolean value) {
        hwMsg.setDisableStaticContent(value);
    }

    @XmlElement(name = "Client_ID")
    public final String getClientId() {
        return hwMsg.getClientCode();
    }

    public final void setClientId(String value) {
        hwMsg.setClientCode(value);
    }

    @XmlElement(name = "Site_ID")
    public final String getSiteId() {
        return hwMsg.getSiteCode();
    }

    public final void setSiteId(String value) {
        hwMsg.setSiteCode(value);
    }


    @XmlElement(name = "JMSNameOrFilePath")
    public final String getJmsNameOrFilePath() {
        return hwMsg.getFilePath();
    }

    public final void setJmsNameOrFilePath(String value) {
        hwMsg.setFilePath(value);
    }

    @XmlElement(name = "Type")
    public final String getType() {
        return hwMsg.getContentType();
    }

    public final void setType(String value) {
        hwMsg.setContentType(value);
    }

    @XmlElement(name = "Frequency")
    public final String getFrequency() {
        return hwMsg.getFrequency();
    }

    public final void setFrequency(String value) {
        hwMsg.setFrequency(value);
    }

    public ITibcoMessage createStubMessageForResponse(int imitateError) {
        return new TransferMsg().setResponse(getMsgId(), imitateError == 0, "" + imitateError);
    }

    public ITibcoMessage createStubMessageForRequest() {
        return new TransferMsg().setRequest(this);
    }

    @Override
    public String toString() {
        return String.format("%s package=%s", getMsgId(), getJmsNameOrFilePath());
    }

    public String toShortString() {
        return String.format("msgID=%s %s %s", getMsgId(), getType(), getFrequency());
    }

}
