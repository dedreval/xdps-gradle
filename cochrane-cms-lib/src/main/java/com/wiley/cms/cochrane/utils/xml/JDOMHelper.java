package com.wiley.cms.cochrane.utils.xml;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;

import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 09.06.12
 */
public class JDOMHelper {

    public static final String ISSUE_NUMBER = "ISSUE_NO";
    public static final String DAY = "DAY";
    public static final String MONTH = "MONTH";
    public static final String YEAR = "YEAR";

    private static final Logger LOG = Logger.getLogger(JDOMHelper.class);

    private JDOMHelper() {
    }

    public static Element getElement(Object context, String xPath) throws JDOMException {

        Object obj = XPath.selectSingleNode(context, xPath);
        return (obj instanceof Element) ? (Element) obj : null;
    }

    public static String getElementValue(Object context, String xPath) throws JDOMException {

        Object obj = XPath.selectSingleNode(context, xPath);
        return (obj instanceof Element) ? ((Element) obj).getValue() : null;
    }

    public static String getElementValue(Object context, String xPath, String defaultValue) throws JDOMException {
        String str = getElementValue(context, xPath);
        return str == null || str.trim().length() == 0 ? defaultValue : str;
    }

    public static Optional<Set<String>> getAttributeValues(Object context, String xPath) throws JDOMException {
        List<?> obj = XPath.selectNodes(context, xPath);
        return Optional.of(obj.stream()
                .map(e -> ((Attribute) e).getValue())
                .collect(Collectors.toSet()));
    }

    public static String getAttributeValue(Object context, String xPath, String defaultValue) {
        try {
            return getAttributeValue(context, xPath);
        } catch (Exception e) {
            LOG.warn(e.getMessage());
        }
        return defaultValue;
    }

    public static String getAttributeValue(Object context, String xPath) throws JDOMException {

        Object obj = XPath.selectSingleNode(context, xPath);
        return (obj instanceof Attribute) ? ((Attribute) obj).getValue() : "";
    }

    public static String getText(Element element) {
        return element == null ? null : element.getText();
    }

    public static String getAttributeValue(Element element, String attribute) throws CmsException {
        return getAttributeValue(element, attribute, Namespace.NO_NAMESPACE);
    }

    public static String getXmlAttributeValue(Element element, String attribute) throws CmsException {
        return getAttributeValue(element, attribute, Namespace.XML_NAMESPACE);
    }

    public static String getAttributeValue(Element element, String attribute, Namespace namespace) throws CmsException {
        String ret = element == null ? null : element.getAttributeValue(attribute, namespace);
        if (ret == null) {
            throw new CmsException(attribute + " attribute is null");
        }
        return ret;
    }

    public static String getAttributeValue(Element element, String attribute, Namespace namespace,
                                           String defaultValue) {
        String ret = element == null ? null : element.getAttributeValue(attribute, namespace);
        return ret == null ? defaultValue : ret;
    }

    public static Date getRevmanDate(Element element) {
        if (element == null) {
            return null;
        }

        Date date;
        String day = element.getAttributeValue(DAY);
        String month = element.getAttributeValue(MONTH);
        String year = element.getAttributeValue(YEAR);
        try {
            Calendar calendar = GregorianCalendar.getInstance();

            calendar.set(Integer.parseInt(year), Integer.parseInt(month) - 1, Integer.parseInt(day), 0, 0, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            date = calendar.getTime();

        } catch (NumberFormatException e) {
            date = null;
            LOG.error(String.format("Error parsing to date values (%s, %s, %s", day, month, year), e);
        }

        return date;
    }

    public static Element getRevmanDate(String name, Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(date);

        Element element = new Element(name);
        element.setAttribute(DAY, String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
        element.setAttribute(MONTH, String.valueOf(calendar.get(Calendar.MONTH) + 1));
        element.setAttribute(YEAR, String.valueOf(calendar.get(Calendar.YEAR)));

        return element;
    }

    public static String getRevmanIssueAsString(Element element) {
        if (element == null) {
            return null;
        }

        String number = element.getAttributeValue(ISSUE_NUMBER);
        String year = element.getAttributeValue(YEAR);
        return number + "," + year;
    }

    public static int getRevmanIssueAsNumber(Element element) {
        int ret = 0;
        if (element != null) {
            try {
                int number = Integer.parseInt(element.getAttributeValue(ISSUE_NUMBER));
                int year = Integer.parseInt(element.getAttributeValue(YEAR));
                ret = CmsUtils.getIssueNumber(year, number);
            } catch (Exception e)  {
                LOG.error(e.getMessage());
            }
        }
        return ret;
    }

    public static Element getRevmanIssueAsElement(String name, int issue) {
        Element element = new Element(name);
        element.setAttribute(ISSUE_NUMBER, String.valueOf(CmsUtils.getIssueByIssueNumber(issue)));
        element.setAttribute(YEAR, String.valueOf(CmsUtils.getYearByIssueNumber(issue)));

        return element;
    }

    public static Element copyElementWithoutChildren(Element root) {

        Element e = new Element(root.getName());

        for (Object obj: root.getAttributes()) {

            Attribute attr =  (Attribute) ((Attribute) obj).clone();
            e.setAttribute(attr);
        }

        return e;
    }
}
