package com.wiley.cms.cochrane.cmanager.contentworker;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.entitymanager.IRecordManager;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.notification.INotificationManager;
import com.wiley.cms.notification.SuspendNotificationEntity;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.10.12
 */
public class ArchieResponseBuilder {
    private static final Logger LOG = Logger.getLogger(ArchieResponseBuilder.class);

    private static final String SUCCESS_ATTR = "success";
    private static final String ERROR_ATTR = "error";
    private static final String ID_ATTR = "id";
    private static final String CD_ATTR = "cd_number";
    private static final String YES_VALUE = "yes";

    private boolean onPublish = true;
    private boolean tr;
    private FlowProduct.SPDState spd = FlowProduct.SPDState.NONE;

    private final Element response;
    private final String process;
    private Integer packageId;
    private String errorMessage;
    private String cochrnaneNotification;
    private String packageName;
    private final Map<Boolean, List<ArchieEntry>> wrMap = new HashMap<>();

    public ArchieResponseBuilder(boolean isTranslations, String process) {
        this(true, isTranslations, process, null);
    }

    public ArchieResponseBuilder(boolean onPublish, boolean tr, String process, Integer packageId) {

        setOnPublish(onPublish);
        setForTranslations(tr);

        response = createRootElement();

        this.process  = process;
        setPackageId(packageId);
    }

    public static ArchieResponseBuilder createOnPublished(Date date, String postfix) {
        return new ArchieResponseBuilder(false, "pub_" + date.getTime() + "_" + postfix);
    }

    public void setPackageId(Integer packageId) {
        this.packageId = packageId;
    }

    public Integer getPackageId() {
        return packageId;
    }

    public Element getResponse() {
        return response;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int size() {
        return response.getChildren().size();
    }

    public Map<Boolean, List<ArchieEntry>> getWrMap() {
        return wrMap;
    }

    public boolean addContent(Element el) {
        if (el != null) {
            response.addContent(el);
            return true;
        }
        return false;
    }

    private void setOnPublish(boolean onPublish) {
        this.onPublish = onPublish;
    }

    boolean isOnPublish() {
        return onPublish;
    }

    public void setForTranslations(boolean value) {
        this.tr = value;
    }

    public String getCochrnaneNotification() {
        return cochrnaneNotification;
    }

    public void setCochrnaneNotification(String cochrnaneNotification) {
        this.cochrnaneNotification = cochrnaneNotification;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void sPD(boolean spd, boolean cancelled) {
        this.spd = cancelled ? FlowProduct.SPDState.OFF : spd ? FlowProduct.SPDState.ON : FlowProduct.SPDState.NONE;
    }

    public FlowProduct.SPDState sPD() {
        return spd;
    }

    public Element asSuccessfulElement(TranslatedAbstractVO vo, String publishdate) {
        Element ret = vo.wasNotified() ? null : asSuccessfulElement(vo.getSid(), vo.getCochraneVersion(), true,
                publishdate);
        setWhenReady(ret, vo, Boolean.TRUE);
        return ret;
    }

    public Element asSuccessfulElement(TranslatedAbstractVO vo) {
        return asSuccessfulElement(vo, null);
    }

    public Element asSuccessfulElement(ArchieEntry ae, String publishdate) {
        if (spd.off())  {
            return asErrorElement(ae, ErrorInfo.SPD_CANCELED);
        }

        Element ret = ae.wasNotified() ? null : asSuccessfulElement(ae.getSid(), ae.getCochraneVersion(), tr,
                publishdate);
        setWhenReady(ret, ae, Boolean.TRUE);
        return ret;
    }

    Element asSuccessfulElement(ArchieEntry ae) {
        return asSuccessfulElement(ae, null);
    }

    Element asSuccessfulElement(String name) {
        return asSuccessfulElement(name, null, tr, null);
    }

    private static Element asSuccessfulElement(String name, String version, boolean ta, String publishDate) {
        return asElement(name, version, YES_VALUE, "", ta, publishDate);
    }

    public Element asErrorElement(TranslatedAbstractVO vo, ErrorInfo err) {
        Element ret = vo.wasNotified() ? null : asErrorElement(vo.getSid(), vo.getCochraneVersion(), err, true);
        setWhenReady(ret, vo, Boolean.FALSE);
        return ret;
    }

    Element asErrorElement(ArchieEntry el, ErrorInfo err) {
        Element ret = el.wasNotified() ? null : asErrorElement(el.getSid(), el.getCochraneVersion(), err, tr);
        setWhenReady(ret, el, Boolean.FALSE);
        return ret;
    }

    private static Element asErrorElement(String sid, String version, ErrorInfo errInfo, String errMsg,
                                          boolean ta) {
        if (errInfo.isCommonFlowRecordValidationError() && version != null) {
            return asErrorElement(sid, version, errMsg == null ? errInfo.getErrorType().getMsg() : errMsg, ta);
        }
        return null;
    }

    Element asErrorElement(String sid, ErrorInfo err) {
        return asErrorElement(sid, null, err, err.getShortMessage(), tr);
    }

    private static Element asErrorElement(String sid, String version, ErrorInfo err, boolean ta) {
        return asErrorElement(sid, version, err, err.getShortMessage(), ta);
    }

    static Element asErrorElement(String name, String version, String err, boolean ta) {
        return asElement(name, version, "no", err, ta, null);
    }

    //public Element removeContent(String sid) {
    //    Element el = findElement(sid);
    //    if (el != null) {
    //        el.getParentElement().removeContent(el);
    //    }
    //    return el;
    //}

    public String getProcess() {
        return process;
    }

    private Element removeElement(String sid, boolean ta) {
        try {
            Object obj = XPath.selectSingleNode(response, String.format(
                ta ? "translation[@id='%s']" : "review[@cd_number='%s']", sid));
            if (obj instanceof Element) {

                Element ret = (Element) obj;
                Element parent = ret.getParentElement();
                parent.removeContent(ret);
                return ret;
            }
        } catch (Exception e) {
            LOG.error(e);
        }
        return null;
    }

    public String getPrettyBody() {
        XMLOutputter xout = new XMLOutputter(Format.getPrettyFormat());
        return xout.outputString(getResponse());
    }

    void suspendWhenReady(String reason) {
        XMLOutputter xout = new XMLOutputter(Format.getPrettyFormat());
        INotificationManager im = CochraneCMSBeans.getNotificationManager();

        wrMap.forEach((success, list)-> {
                for (ArchieEntry re: list) {
                    Element el = removeElement(re.getSid(), re.getLanguage() != null);
                    if (el != null) {
                        im.suspendArchieNotification("" + re.getId(), xout.outputString(createRootElement().addContent(
                                el)), reason, SuspendNotificationEntity.TYPE_UNDEFINED_ERROR);
                    }
                }
            });
    }

    void cancelWhenReady(IRecordManager rm) {
        if (packageId != null) {
            wrMap.forEach((success, list) -> rm.updateWhenReadyOnReceived(list, packageId, false, true, sPD().is()));
        }
    }

    public void commitWhenReady(IRecordManager rm) {
        if (packageId == null) {
            return;
        }
        if (isOnPublish()) {
            wrMap.forEach((success, list)-> rm.updateWhenReadyOnPublished(list, packageId, success));

        } else {
            wrMap.forEach((success, list)-> rm.updateWhenReadyOnReceived(list, packageId, success, false, sPD().is()));
        }
    }

    private void setWhenReady(Element wasAdd, ArchieEntry re, Boolean success) {
        if (wasAdd == null && !re.wasNotified()) {
            return;
        }
        List<ArchieEntry> list = wrMap.computeIfAbsent(success, f -> new ArrayList<>());
        list.add(re);
    }

    private static void setElement(String version, String success, String err, Element el) {

        el.setAttribute("version_no", (version == null || version.isEmpty())
                ? ArchieEntry.NONE_VERSION : version);
        el.setAttribute(SUCCESS_ATTR, success);
        el.setAttribute(ERROR_ATTR, err);
    }

    private static Element asElement(String sid, String version, String success, String err, boolean ta,
                                     String publishDate) {
        if (sid == null) {
            return null;
        }
        Element el = new Element(ta ? "translation" : "review");
        el.setAttribute(ta ? ID_ATTR : CD_ATTR, sid);
        if (publishDate != null) {
            el.setAttribute("published", publishDate);
        }
        setElement(version, success, err, el);
        return el;
    }

    private static Element createRootElement() {
        Element ret = new Element("content");
        ret.setAttribute(SUCCESS_ATTR, YES_VALUE);
        ret.setAttribute(ERROR_ATTR, "");
        return ret;
    }
}
