package com.wiley.cms.cochrane.cmanager.publish.send.semantico;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/2/2016
 */
@XmlRootElement(name = TransferMsg.RES_NAME)
public class TransferMsg extends ResourceStrId implements ITibcoMessage {

    static final String RES_NAME = "Transfer";
    static final String STATUS_SUCCESS = "success";

    private static final long serialVersionUID = 1L;

    private static final JaxbResourceFactory<TransferMsg> FACTORY = JaxbResourceFactory.create(TransferMsg.class);
    private static final JaxbResourceFactory<TransferMsg> FORMAT_FACTORY = JaxbResourceFactory.create(
            TransferMsg.class);

    @XmlElement(name = "TransferMessageBody")
    private TransferMsgBody body = new TransferMsgBody();

    private String textMsg;

    public TransferMsg() {
        setId("");
    }

    public static void register(ResourceManager loader) {
        FORMAT_FACTORY.enableFormatter();
        loader.register(RES_NAME, FACTORY);
    }

    public final void setTextMessage(String value) {
        textMsg = value;
    }

    public final String getTextMessage() {
        return textMsg;
    }

    public final TransferMsg setResponse(Response value) {
        body.response = value;
        return this;
    }

    public final TransferMsg setResponse(String sid, boolean success, String code) {
        Response response = new Response();
        response.init(sid, success, code);
        return setResponse(response);
    }

    public final SemanticoMsg getRequest() {
        return body.request;
    }

    public final TransferMsg setRequest(SemanticoMsg value) {
        body.request = value;
        return this;
    }

    public boolean hasEmptyResponse() {
        return body.response == null || body.response.getMsgId() == null || body.response.getMsgId().length() == 0;
    }

    public final String getResponseSid() {
        return body.response != null ? body.response.getMsgId() : null;
    }

    public final boolean isOurResponse() {
        return SemanticoMsg.hasOurPrefix(getResponseSid());
    }

    public final int getResponseId() throws Exception {
        String sid = getResponseSid();
        if (sid == null) {
            throw new Exception("a response identifier is null");
        }
        return SemanticoMsg.parseMessageId(sid);
    }

    public final String getRequestSid() {
        return body.request != null ? body.request.getMsgId() : null;
    }

    public final String asXmlString() {
        return asString(FACTORY);
    }

    @Override
    public final String asFormatXmlString() {
        return asString(FORMAT_FACTORY);
    }

    public final boolean isSuccess() {
        return body.response != null && body.response.status != null
                && STATUS_SUCCESS.equals(body.response.status.toLowerCase());
    }

    public String getResponseStatus() {
        return body.response != null ? body.response.status : null;
    }

    public String getResponseCode() {
        return body.response != null ? body.response.returnCode : null;
    }

    @Override
    public String toString() {
        return body.response != null ? body.response.toString()
                : (body.request != null ? body.request.toString() : null);
    }

    public String toShortString() {
        return body.response != null ? body.response.toString()
                : (body.request != null ? body.request.toShortString() : null);
    }

    public static TransferMsg create(String element, boolean response) {
        try {
            TransferMsg ret = FACTORY.createResource(element);
            if (response && ret.hasEmptyResponse()) {
                throw new Exception("empty response identifier");
            }
            ret.setTextMessage(element);
            return ret;

        } catch (Exception e) {
            LOG.error(e.getMessage());
            LOG.error(element);
            return null;
        }
    }

    static class MessageId {
        @XmlElement(name = "MessageId")
        String msgId = "0";

        void setMsgId(String value) {
            msgId = value;
        }

        public String getMsgId() {
            return msgId;
        }
    }

    static class Response extends MessageId {

        @XmlElement(name = "Status")
        String status = STATUS_SUCCESS;

        @XmlElement(name = "ReturnCode")
        String returnCode = "0";

        public Response() {
        }

        void init(String sid, boolean success, String code) {
            setMsgId(sid);
            status = success ? STATUS_SUCCESS : "false";
            returnCode = code;
        }

        public final boolean isSuccess() {
            return STATUS_SUCCESS.equals(getStatus());
        }

        public final String getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return String.format("msgID=%s %s code=%s", getMsgId(), status, returnCode);
        }
    }

    private static class TransferMsgBody {

        @XmlElement(name = "TransferResponse")
        Response response;

        @XmlElement(name = "TransferRequest")
        SemanticoMsg request;

        public TransferMsgBody() {
        }
    }
}
