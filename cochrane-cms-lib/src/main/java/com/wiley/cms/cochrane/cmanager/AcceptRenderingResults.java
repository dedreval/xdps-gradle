package com.wiley.cms.cochrane.cmanager;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.naming.InitialContext;

import org.jboss.ws.api.annotation.WebContext;

import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.render.services.IAcceptRenderingResults;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 * @version 1.0
 */
@Stateless
@WebService(name = "AcceptRenderingResults",
            serviceName = "AcceptRenderingResults",
            endpointInterface = "com.wiley.cms.render.services.IAcceptRenderingResults",
            targetNamespace = "http://services.render.cms.wiley.com/jaws")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@WebContext(contextRoot = "/CochraneCMS")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AcceptRenderingResults implements IAcceptRenderingResults {
    protected static Logger log = Logger.getLogger(AcceptRenderingResults.class);
    protected String queueName = "java:jboss/exported/jms/queue/accept_rendering";

    public void acceptRenderingResults(
            @WebParam(name = "jobId", targetNamespace = "http://services.render.cms.wiley.com/jaws") final int jobId,
            @WebParam(name = "renderingResult", targetNamespace = "http://services.render.cms.wiley.com/jaws")
            final String renderingResult) {
        log.debug("Accept Rnd jobid=" + jobId);

        try {
            InitialContext ctx = new InitialContext();
            JMSSender.send(JMSSender.lookupQueue(), (Queue) ctx.lookup(queueName),
                    new JMSSender.MessageCreator() {
                        public Message createMessage(Session session) throws JMSException {
                            return session.createTextMessage(jobId + ":" + renderingResult);
                        }
                    });
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
