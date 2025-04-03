package com.wiley.cms.cochrane.authentication;

import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.isc.util.ElementCreator;
import com.wiley.cms.isc.util.WebServiceMessageCall;


/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public class CochraneCMSAuthBean {

    private static final String AUTHORIZE_USER = "authorizeUser";
    private static final String USERNAME = "username";
    private static final String APP_NAME = "appName";
    private static final String GET_ALL = "getAll";
    private static final String PASSWORD = "password";

    private static final String APP_NAME_VALUE = "testapp";
    private static final String GET_ALL_VALUE = "true";

    private boolean hasAdminRights = false;

    public boolean isAuthenticatedAsAdmin() {
        return this.hasAdminRights;
    }

    public boolean authenticatedAsAdmin(String username, String password) throws Exception {
        return auth(createQuery(username, password));
    }

    private boolean auth(Element[] elems) throws Exception {
        this.hasAdminRights = false;

        URL url = new URL(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.AUTHSERVICE_SERVICE_URL));

        //OK, populate the userHashMap with a call to the user authentication service
        WebServiceMessageCall myCall = new WebServiceMessageCall(url, AUTHORIZE_USER, elems);

        //Something to check the return elements
        org.w3c.dom.Element[] returnElems = myCall.callService();

        //Get whether user was authorized
        NodeList authNodeList = returnElems[0].getElementsByTagName("authorized");
        if (authNodeList != null && authNodeList.item(0) != null
                && authNodeList.item(0).getFirstChild() != null
                && authNodeList.item(0).getFirstChild().getNodeValue().equals(GET_ALL_VALUE)) {

            this.hasAdminRights = true;
        } else {
            this.hasAdminRights = false;
        }

        return this.hasAdminRights;
    }

    public boolean authenticatedAsAdmin(String dn) throws Exception {
        return auth(createQuery(dn));
    }

    private org.w3c.dom.Element[] createQuery(String dn) throws ParserConfigurationException {

        ElementCreator elemCreator = new ElementCreator();
        org.w3c.dom.Document doc;

        doc = elemCreator.createDocument();

        // Construct the XML for the query
        org.w3c.dom.Element userQueryElem = doc.createElement(AUTHORIZE_USER);

        org.w3c.dom.Element userNameElem = elemCreator.createElementWithText(doc, USERNAME, dn);
        //org.w3c.dom.Element passwordElem = elemCreator.createElementWithText(doc, "password", password);
        org.w3c.dom.Element appNameElem = elemCreator.createElementWithText(doc, APP_NAME, APP_NAME_VALUE);
        org.w3c.dom.Element getAllElem = elemCreator.createElementWithText(doc, GET_ALL, GET_ALL_VALUE);

        userQueryElem.appendChild(userNameElem);
        //userQueryElem.appendChild(passwordElem);
        userQueryElem.appendChild(appNameElem);
        userQueryElem.appendChild(getAllElem);

        //Append those to the main element
        doc.appendChild(userQueryElem);

        //Create the element array
        return new org.w3c.dom.Element[]{userQueryElem};
    }

    private org.w3c.dom.Element[] createQuery(String userName, String password) throws ParserConfigurationException {

        ElementCreator elemCreator = new ElementCreator();
        org.w3c.dom.Document doc;

        doc = elemCreator.createDocument();

        // Construct the XML for the query
        org.w3c.dom.Element userQueryElem = doc.createElement(AUTHORIZE_USER);

        org.w3c.dom.Element userNameElem = elemCreator.createElementWithText(doc, USERNAME, userName);
        org.w3c.dom.Element passwordElem = elemCreator.createElementWithText(doc, PASSWORD, password);
        org.w3c.dom.Element appNameElem = elemCreator.createElementWithText(doc, APP_NAME, APP_NAME_VALUE);
        org.w3c.dom.Element getAllElem = elemCreator.createElementWithText(doc, GET_ALL, GET_ALL_VALUE);

        userQueryElem.appendChild(userNameElem);
        userQueryElem.appendChild(passwordElem);
        userQueryElem.appendChild(appNameElem);
        userQueryElem.appendChild(getAllElem);

        //Append those to the main element
        doc.appendChild(userQueryElem);

        //Create the element array
        return new org.w3c.dom.Element[]{userQueryElem};
    }
}
