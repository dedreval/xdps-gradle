package com.wiley.cms.cochrane.test;

import java.util.Collection;
import java.util.List;

import com.wiley.cms.cochrane.cmanager.res.HookType;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/9/2020
 */
public class Hooks {
    public static final Logger LOG = Logger.getLogger("Tester");

    public static final String UPLOAD_START = "upload_start";
    public static final String UPLOAD_END_SUCCESS = "upload_end_success";
    public static final String UPLOAD_END_FAILURE = "upload_end_failure";
    public static final String CLEAR_DB_START = "clear_db_start";
    public static final String CLEAR_DB_END = "clear_db_end";
    public static final String DELETE_START = "delete_start";
    public static final String DELETE_END = "delete_end";

    private static final Res<HookType> RECORD_HOOKS = HookType.find("record_hook");

    private Hooks() {
    }

    public static void captureRecords(Integer clDbId, Collection<String> cdNumbers, String operation) {
        if (!Res.valid(RECORD_HOOKS) || cdNumbers == null || cdNumbers.isEmpty()) {
            return;
        }
        List<IRecordHook> list = RECORD_HOOKS.get().getImplementations(IRecordHook.class);
        list.forEach(h -> h.capture(clDbId, cdNumbers, operation));
    }

    public static void captureRecords(Integer dfId, String operation) {
        if (!Res.valid(RECORD_HOOKS)) {
            return;
        }
        List<IRecordHook> list = RECORD_HOOKS.get().getImplementations(IRecordHook.class);
        list.forEach(h -> h.capture(dfId, operation));
    }
}
