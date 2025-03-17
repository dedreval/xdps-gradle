package com.wiley.cms.cochrane.activitylog.snowflake;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wiley.cms.cochrane.activitylog.FlowLogEntity;
import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.activitylog.UUIDEntity;
import com.wiley.tes.util.BitValue;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 4/5/2021
 */
public enum SFType {
    // do not change ordering
    FLOW("ProductPackageEvent", "flow") {

        @Override
        public SFEvent createEvent(@NotNull FlowLogEntity flowLog, String packageName, String errorMessage,
                                   @NotNull ISFPackageProduct product, FlowProduct.State state,
                                   @NotNull Date eventDate, String transactionId) {
            Map<String, String> ret = product.getRawData();
            String date = Now.formatDateUTC(eventDate);
            ret.put(ISFProductFlowEvent.P_EVENT_DATE, date);
            setFlowEventId(flowLog.getId().toString(), ret);
            setFlowEventRecords(product.getEventRecords(), product.getTotalRecords(), ret);
            setDOI(product.getDOI(), ret);
            
            ret.put(ISFProductFlowEvent.P_EVENT_NAME, flowLog.getEvent().getName());
            ret.put(ISFProductFlowEvent.P_EVENT_PACKAGE_NAME, packageName);
            setErrorMessage(errorMessage, ret);

            ret.put(ISFProductFlowEvent.P_EVENT_COMPLETED, date);
            if (TRANSACTION_ID_EVENTS.contains(flowLog.getEvent().getId())) {
                if (flowLog.getEvent().getId() == ILogEvent.PRODUCT_SENT_DS) {
                    ret.put(ISFProductFlowEvent.P_EVENT_TRANSACTION_ID, UUIDEntity.extractUUID(packageName));
                } else {
                    ret.put(ISFProductFlowEvent.P_EVENT_TRANSACTION_ID, transactionId);
                }
            } else {
                ret.put(ISFProductFlowEvent.P_EVENT_TRANSACTION_ID, null);
            }
            return new SFEvent(flowLog.getId(), flowLog.getPackageId(), GSON.toJson(ret), this);
        }

    }, PRODUCT("Product", "product") {

        @Override
        public SFEvent createEvent(@NotNull FlowLogEntity flowLog, String packageName, String errorMessage,
                                   @NotNull ISFPackageProduct product, @NotNull FlowProduct.State state,
                                   @NotNull Date eventDate, String transactionId) {
            Map<String, String> ret = new LinkedHashMap<>();
            setType(eventDate, ret);

            ret.put(ISFProduct.P_PRODUCT_TYPE, product.getType());
            ret.put(ISFProduct.P_PRODUCT_STATE, state.label());
            ret.put(ISFProduct.P_PUBLICATION_TYPE, getEmpty4Null(product.getPublicationType()));
            setDOI(product.getDOI(), ret);
            ret.put(ISFProduct.P_PRODUCT_CODE, product.getPubCode());
            ret.put(ISFProduct.P_PRODUCT_TITLE, getNull4Empty(product.getTitle()));
            if (!product.sPD().off()) {
                ret.put(ISFProduct.P_PRODUCT_FIRST_ONLINE_DATE, getNull4Empty(product.getFirstOnlineDate()));
            }
            ret.put(ISFProduct.PARENT_DOI, product.getParentDOI());

            return new SFEvent(flowLog.getId(), flowLog.getPackageId(), GSON.toJson(ret), this);
        }


    }, TRANSLATION("Translation", "tr") {

        @Override
        public SFEvent createEvent(@NotNull FlowLogEntity flowLog, String packageName, String errorMessage,
                                   @NotNull ISFPackageProduct product, @NotNull FlowProduct.State state,
                                   @NotNull Date eventDate, String transactionId) {
            Map<String, String> ret = new LinkedHashMap<>();
            setType(eventDate, ret);
            ret.put(ISFProductPart.P_TR_STATE, state.label());
            setDOI(product.getDOI(), ret);
            setLanguage(product.getLanguage(), ret);

            return new SFEvent(flowLog.getId(), flowLog.getPackageId(), GSON.toJson(ret), this);
        }

    }, PACKAGE_PRODUCT("ProductPackage", "package") {


        @Override
        public SFEvent createEvent(@NotNull FlowLogEntity flowLog, String packageName, String errorMessage,
                                   @NotNull ISFPackageProduct product, FlowProduct.State state,
                                   @NotNull Date eventDate, String transactionId) {
            Map<String, String> ret = new LinkedHashMap<>();
            setType(eventDate, ret);

            setPackageId(product.getSourcePackageId(), ret);
            setDOI(product.getDOI(), ret);
            setPackageType(product.getSourcePackageType(), ret);
            if (product.sPD().on()) {
                ret.put(ISFPackageProduct.P_PRODUCT_SPD, getNull4Empty(product.getSPDDate()));
            }
            ret.put(ISFPackageProduct.P_PRODUCT_OR_TRANSLATION_TITLE, getNull4Empty(product.getTitle()));
            ret.put(ISFPackageProduct.P_PRODUCT_PUBLICATION_STATUS, getNull4Empty(product.getSourceStatus()));
            ret.put(ISFPackageProduct.P_PACKAGE_VENDOR, getEmpty4Null(product.getVendor()));

            ret.put(ISFPackageProduct.P_PACKAGE_PACKAGE_FREQUENCY, product.getFrequency());
            if (product.isHighProfile()) {
                ret.put(ISFPackageProduct.P_PACKAGE_HIGH_PROFILE_PUBLICATION, Boolean.TRUE.toString());
            }

            setLanguage(getNull4Empty(product.getLanguage()), ret);

            return new SFEvent(flowLog.getId(), flowLog.getPackageId(), GSON.toJson(ret), this);
        }
    };

    public static final int COMPLETED = 1 << values().length;

    public static final Set<Integer> TRANSACTION_ID_EVENTS = new HashSet<>(Arrays.asList(
            ILogEvent.PRODUCT_RECEIVED,
            ILogEvent.PRODUCT_SENT_WOLLIT,
            ILogEvent.PRODUCT_SENT_HW,
            ILogEvent.PRODUCT_SENT_DS));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String label;
    private final String shortName;


    SFType(String label, String shortName) {
        this.label = label;
        this.shortName = shortName;
    }

    public abstract SFEvent createEvent(FlowLogEntity flowLog, String packageName, String errorMessage,
                                        ISFPackageProduct product, FlowProduct.State state, Date eventDate,
                                        String transactionId);

    public String label() {
        return label;
    }

    public String shortName() {
        return shortName;
    }

    public boolean has(int value) {
        return value > 0 && BitValue.getBit(ordinal(), value);
    }

    public boolean completed(int value) {
        return value > 0 && BitValue.getBit(ordinal() + SFType.values().length, value);
    }

    public int set(int value) {
        return BitValue.setBit(ordinal(), value);
    }

    public int complete(int value) {
        return BitValue.setBit(ordinal() + SFType.values().length, value);
    }

    public void setType(@NotNull Date date, @NotNull Map<String, String> ret) {
        setType(Now.formatDateUTC(date), ret);
    }

    public void setType(@NotNull String date, @NotNull Map<String, String> ret) {
        setType(ret);
        ret.put(ISFProductFlowEvent.P_EVENT_DATE, date);
    }

    public void setType(@NotNull Map<String, String> ret) {
        ret.put(ISFProductFlowEvent.P_EVENT_TYPE, label());
    }

    public static void setErrorMessage(@NotNull String err, @NotNull Map<String, String> ret) {
        ret.put(ISFProductFlowEvent.P_ERROR_MESSAGE, getNull4Empty(err));
    }

    private static void setDOI(@NotNull String doi, @NotNull Map<String, String> ret) {
        ret.put(ISFProductPart.P_PRODUCT_DOI, doi);
    }

    public static void setLanguage(@NotNull String language, @NotNull Map<String, String> ret) {
        ret.put(ISFProductPart.P_TR_LANGUAGE, getNull4Empty(language));
    }

    public static void setPackageId(@NotNull Integer packageId, @NotNull Map<String, String> ret) {
        ret.put(ISFPackageProduct.P_PACKAGE_ID, packageId.toString());
    }

    public static void setPackageType(String packageType, @NotNull Map<String, String> ret) {
        ret.put(ISFPackageProduct.P_PACKAGE_TYPE, getNull4Empty(packageType));
    }

    public static void setFlowEventId(String eventId, @NotNull Map<String, String> ret) {
        ret.put(ISFProductFlowEvent.P_EVENT_ID, eventId);
    }

    public static void setFlowEventRecords(Integer eventCount, Integer totalCount, @NotNull Map<String, String> ret) {
        ret.put(ISFProductFlowEvent.P_EVENT_RECORDS, eventCount == null ? null : eventCount.toString());
        ret.put(ISFProductFlowEvent.P_TOTAL_PHASE_RECORDS, totalCount == null ? null : totalCount.toString());
    }

    private static String getNull4Empty(String value) {
        return value != null && value.isEmpty() ? null : value;
    }

    private static String getEmpty4Null(String value) {
        return value == null ? "" : value;
    }
}
