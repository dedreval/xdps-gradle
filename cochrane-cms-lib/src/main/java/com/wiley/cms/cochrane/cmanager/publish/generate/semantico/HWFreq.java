package com.wiley.cms.cochrane.cmanager.publish.generate.semantico;

import java.util.Collection;
import java.util.LinkedHashMap;

import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.process.IProcessManager;
import com.wiley.tes.util.res.Settings;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 6/30/2021
 */
public enum HWFreq {

    // do not change ordering
    NONE,

    BULK {
        @Override
        public int getProcessPriority() {
            return IProcessManager.LOW_PRIORITY;
        }
    },
    
    REAL_TIME,

    HIGH {
        @Override
        public int getProcessPriority() {
            return IProcessManager.HIGHEST_PRIORITY;
        }
    };

    public String getValue() {
        return CmsResourceInitializer.getHWFrequency().get().getStrSetting(name());
    }

    public static boolean isHighPriority(String value) {
        return HIGH.getValue().equals(value);
    }

    public static boolean isBulkPriority(String value) {
        return BULK.getValue().equals(value);
    }

    public static HWFreq getHWFreq(String value) {
        return value == null || value.isEmpty() ? NONE
                : (isBulkPriority(value) ? BULK : (isHighPriority(value) ? HIGH : REAL_TIME));
    }

    public static HWFreq getHWFreq(int ordinal) {
        HWFreq[] values = values();
        return ordinal < values.length ? values[ordinal] : NONE;
    }

    public static LinkedHashMap<String, String> getMap4UI(HWFreq freq) {
        Collection<Settings.Setting> list = CmsResourceInitializer.getHWFrequency().get().getSettings();
        LinkedHashMap<String, String> values = new LinkedHashMap<>(list.size());
        String defaultFrequency = null;
        if (freq != null) {
            defaultFrequency = freq.getValue();
            values.put(freq.name(), defaultFrequency);
        }
        for (Settings.Setting setting: list) {
            String value = setting.getValue();
            if (!value.equals(defaultFrequency)) {
                values.put(setting.getLabel(), value);
            }
        }
        return values;
    }

    public int getProcessPriority() {
        return IProcessManager.USUAL_PRIORITY;
    }
}
