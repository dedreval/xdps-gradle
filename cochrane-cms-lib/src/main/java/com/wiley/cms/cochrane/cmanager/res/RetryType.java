package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/19/2016
 */
@XmlRootElement(name = RetryType.RES_NAME)
public class RetryType extends ResourceStrId implements Serializable {
    static final String RES_NAME = "retry";

    private static final long serialVersionUID = 1L;

    private static final DataTable<String, RetryType> DT = new DataTable<String, RetryType>(RES_NAME);

    @XmlAttribute(name = "schedule")
    private String schedule = "";

    @XmlAttribute(name = "batch")
    private int limit = -1;

    @XmlAttribute(name = "max")
    private int max = -1;

    private Set<String> errors;
    private Set<String> controlledErrors;

    public static void register(ResourceManager loader) {
        loader.register(RES_NAME, JaxbResourceFactory.create(RetryType.class, DT));
    }

    public static Res<RetryType> find(String sid) {
        return DT.findResource(sid);
    }

    static Res<RetryType> get(String sid) {
        return DT.get(sid);
    }

    public final int getLimit() {
        return limit;
    }

    public final void setLimit(int value)  {
        limit = value;
    }

    public final int getMaxCount() {
        return max;
    }

    public final void setMaxCount(int value)  {
        max = value;
    }

    public final String getSchedule() {
        return schedule;
    }

    public final void setSchedule(String value)  {
        schedule = value;
    }

    public final String getRetryError(String reason) {
        if (reason != null && errors != null) {
            Set<String> tmp = errors;
            for (String keyword : tmp) {
                if (reason.contains(keyword)) {
                    return keyword;
                }
            }
        }
        return errors == null ? reason : null;
    }

    public final String getControlledError(String reason) {
        if (controlledErrors != null) {
            Set<String> tmp = controlledErrors;
            for (String keyword : tmp) {
                if (reason.contains(keyword)) {
                    return keyword;
                }
            }
        }
        return null;
    }

    @XmlAttribute(name = "errors")
    public final void setErrors(String value) {
        if (value == null || value.isEmpty()) {
            errors = null;
            return;
        }
        errors = new HashSet<>(Arrays.asList(value.split(PHRASE_DELIMITER)));
    }


    @XmlAttribute(name = "controlled-errors")
    public final void setControlledErrors(String value) {
        if (value == null || value.isEmpty()) {
            controlledErrors = null;
            return;
        }
        controlledErrors = new HashSet<>(Arrays.asList(value.split(PHRASE_DELIMITER)));
    }

    @Override
    public String toString() {
        return String.format("%d in %s, max tries=%d [%s]", limit, schedule, max, getId());
    }
}
