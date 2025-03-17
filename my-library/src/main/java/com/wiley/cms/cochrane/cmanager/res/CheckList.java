package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;
import com.wiley.tes.util.res.SingletonRes;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/2/2015
 */
@XmlRootElement(name = CheckList.RES_NAME)
public class CheckList extends ResourceStrId implements Serializable {

    static final String RES_NAME = "checklist";
    private static final long serialVersionUID = 1L;

    private static final SingletonRes<CheckList> REF = new SingletonRes<>(CheckList.RES_NAME, null);
    private static final JaxbResourceFactory FACTORY = JaxbResourceFactory.create(CheckList.class, REF);

    private final Set<String> set = Collections.synchronizedSet(new HashSet<>());

    private Set<String> statuses = new HashSet<>();

    public CheckList() {
        setId(RES_NAME);
    }

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, FACTORY);
    }

    public static Res<CheckList> get() {
        return REF.getResource();
    }

    public void removeCdNumber(String value) {
        set.remove(value);
    }

    public void addCdNumber(String value) {
        set.add(value.trim().toUpperCase());
    }

    public boolean hasCdNumber(String value) {
        return set.contains(value);
    }

    public boolean hasCdNumber(String value, String status) {
        return statuses.contains(status) && hasCdNumber(value);
    }

    @XmlAttribute(name = "status")
    public String getValidStatus() {
        StringBuilder sb = new StringBuilder();
        for (String str : statuses) {
            sb.append(str).append("; ");
        }
        return sb.toString();
    }

    public void setValidStatus(String value) {
        Set<String> newList = new HashSet<>();
        String[] list = value.split(DELIMITER);
        for (String str : list) {

            str = str.trim().toUpperCase();
            if (str.length() > 0) {
                newList.add(str);
            }
        }
        statuses = newList;
    }

    @XmlElement(name = "set")
    public String getCheckList() {
        StringBuilder sb = new StringBuilder("\n");
        synchronized (set) {
            for (String rec : set) {
                sb.append(rec).append("\n");
            }
        }
        return sb.toString();
    }

    public void setCheckList(String value) {
        setCdNumbers(value.split(DELIMITER));
    }

    public void save() throws Exception {
        FACTORY.enableFormatter();
        FACTORY.saveResource(this, CmsResourceInitializer.getMainResourceFolder().getPath() + FilePathCreator.SEPARATOR
                    + "checklist.xml");
    }

    public List<String> getCdNumbers() {
        synchronized (set) {
            List<String> ret  = new ArrayList<>(set.size());
            ret.addAll(set);
            return ret;
        }
    }

    public void setCdNumbers(List<String> list) {
        setCdNumbers(list == null ? new String[0] : list.toArray(new String[list.size()]));
    }

    private void setCdNumbers(String[] list) {
        synchronized (set) {
            set.clear();
            for (String str : list) {

                str = str.trim();
                if (str.length() > 0) {
                    addCdNumber(str);
                }
            }
        }
    }
}
