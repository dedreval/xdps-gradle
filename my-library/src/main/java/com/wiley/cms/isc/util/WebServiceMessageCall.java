package com.wiley.cms.isc.util;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.Vector;

import javax.xml.rpc.ServiceException;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.message.SOAPBodyElement;
import org.w3c.dom.Element;

import com.wiley.tes.util.Logger;

/**
 * @author Matthew Larson, created on Apr 23, 2006 at 3:27:47 PM
 * @version $Id: WebServiceMessageCall.java,v 1.6 2011-11-25 14:05:49 sgulin Exp $
 */
public class WebServiceMessageCall {

    private static final Logger LOG = Logger.getLogger(WebServiceMessageCall.class);

    URL url;
    String serviceName;
    Element[] elements;

    public WebServiceMessageCall(URL url, String serviceName, Element[] elements) {
        this.url = url;
        this.serviceName = serviceName;
        this.elements = elements;
    }

    public Element[] callService() throws ServiceException {
        Call call = null;

        try {
            Service service = new Service();
            call = (Call) service.createCall();

            call.setTargetEndpointAddress(url);
            //call.setOperationName(new QName("http://soapinterop.org/", serviceName));
            call.setOperationStyle("rpc");

        } catch (ServiceException e) {
            throw e;
        }

        // Convert Element array to SOAPBodyElement array, ClassCastException otherwise
        int i = 0;
        SOAPBodyElement[] input = new SOAPBodyElement[elements.length];
        for (Element e : elements) {
            input[i++] = new SOAPBodyElement(e);
        }

        // input[0] = new SOAPBodyElement(elements[0]);

        // Get data back
        Vector<SOAPBodyElement> elems;
        try {
            elems = (Vector) call.invoke(input);
        } catch (RemoteException e) {
            throw new ServiceException(e);
        } catch (Exception e) {
            throw new ServiceException(e);
        }

        SOAPBodyElement[] soapElements = new SOAPBodyElement[elems.size()];
        int j = 0;
        for (SOAPBodyElement elem : elems) {
            soapElements[j++] = elem;
        }

        // SOAPBodyElement elem = (SOAPBodyElement) elems.get(0);

        return soapElements;
    }
}
