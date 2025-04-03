package com.wiley.cms.cochrane.cmanager.res;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.DataTable;
import com.wiley.tes.util.res.JaxbResourceFactory;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.ResourceManager;
import com.wiley.tes.util.res.ResourceStrId;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 03.03.2020
 */
@XmlRootElement(name = HookType.RES_NAME)
public class HookType extends ResourceStrId implements Serializable {
    static final String RES_NAME = "hook";

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(HookType.class);

    private static final DataTable<String, HookType> DT = new DataTable<>(HookType.RES_NAME);

    private Map<Class, List<Object>> hooks;

    public static void register(ResourceManager loader) {
        loader.register(HookType.RES_NAME, JaxbResourceFactory.create(HookType.class));
    }

    public static Res<HookType> find(String sid) {
        return DT.findResource(sid);
    }

    static Res<HookType> get(String sid) {
        return DT.get(sid);
    }

    @Override
    protected void populate() {
        DT.publish(this);
    }

    @XmlElement(name = "entry")
    public Entry[] getEntries() {
        // do nothing
        return null;
    }

    public void setEntries(Entry[] entries) {
        if (entries == null) {
            return;
        }
        Map<Class, List<Object>> tmp = new HashMap<>();

        for (Entry entry: entries) {
            Class type = entry.checkClass(entry.type);
            String[] implNames = entry.impl == null ? null : entry.impl.split(PHRASE_DELIMITER);
            if (type == null || implNames == null) {
                continue;
            }
            for (String implName: implNames) {
                Object impl = entry.checkImpl(implName, type);
                if (impl != null) {
                    tmp.computeIfAbsent(type, f -> new ArrayList<>()).add(impl);
                }
            }
        }
        hooks = tmp;
    }

    public <I> List<I> getImplementations(Class<I> cl) {
        return hooks == null || !hooks.containsKey(cl) ? Collections.emptyList() : (List<I>) hooks.get(cl);
    }

    /**
     * A hook entry
     */
    private static class Entry implements Serializable {
        private static final long serialVersionUID = 1L;

        @XmlAttribute(name = "type")
        private String type;

        @XmlAttribute(name = "impl")
        private String impl;

        private Object checkImpl(String value, Class type) {
            Class cl = checkClass(value);
            if (cl != null) {
                try {
                    return type.cast(cl.newInstance());
                } catch (Exception e) {
                    LOG.error(e.getMessage());
                }
            }
            return null;
        }

        private Class checkClass(String value) {
            try {
                return Class.forName(value);
            } catch (ClassNotFoundException e) {
                LOG.error(e.getMessage());
            }
            return null;
        }
    }
}

