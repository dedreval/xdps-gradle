package com.wiley.cms.cochrane.cmanager.publish.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumSentConfirm.JsonRequired;
import com.wiley.cms.cochrane.services.LiteratumEvent;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;

import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 17.07.2017
 */
public class LiteratumResponseParser implements JsonDeserializer<LiteratumSentConfirm> {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LiteratumSentConfirm.class, new LiteratumResponseParser())
            .create();

    @Override
    public LiteratumSentConfirm deserialize(JsonElement elem,
                                            Type type,
                                            JsonDeserializationContext ctx) {
        LiteratumSentConfirm response = new Gson().fromJson(elem, type);
        validateResponse(response);
        return response;
    }

    private static void validateResponse(LiteratumSentConfirm response) {
        checkMandatoryFieldsNotEmpty(response);
        checkFieldsValues(response);
    }

    private static void checkMandatoryFieldsNotEmpty(LiteratumSentConfirm response) {
        Field[] fields = response.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f.getAnnotation(JsonRequired.class) == null) {
                continue;
            }

            try {
                f.setAccessible(true);
                if (f.get(response) == null || StringUtils.isEmpty(f.get(response).toString())) {
                    throw new JsonParseException(f.getName() + " property undefined or contains no value");
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new JsonParseException(
                        "Failed to obtain value of property " + f.getName() + " of deserialized object", e);
            }
        }
    }

    private static void checkFieldsValues(LiteratumSentConfirm response) {
        String sourceSystem = response.getSourceSystem();
        checkFieldValue(sourceSystem,
                CochraneCMSPropertyNames.getLiteratumSourceSystemFilterWol(),
                CochraneCMSPropertyNames.getLiteratumSourceSystemFilterSemantico());
        checkFieldValue(response.getEventType(),
                CochraneCMSPropertyNames.getLiteratumEventOnlineFilter(),
                CochraneCMSPropertyNames.getLiteratumEventOnLoadFilter(),
                CochraneCMSPropertyNames.getLiteratumEventOfflineFilter());

        if (LiteratumResponseHandler.isHWContentOnline(response)) {
            try {
                checkWorkflowEventGroup(response);

            } catch (JsonParseException jpe) {
                MessageSender.sendReport(MessageSender.MSG_TITLE_PUBLISH_EVENT_ERROR, "",
                        PublishHelper.buildPublicationEventErrorMessage(
                                response.getDeliveryId(), jpe.getMessage(), response.getRawData()));
                throw jpe;
            }
        }
    }

    private static void checkWorkflowEventGroup(LiteratumSentConfirm response) {
        List<LiteratumSentConfirm.WorkflowEventGroup> workflowEventGroup = response.getWorkflowEventGroup();
        if (workflowEventGroup.isEmpty()) {
            throw new JsonParseException("event field \"workflowEventGroup\" can`t be empty to support publish dates");
        }

        checkWorkflowEventValue(LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, response);
        if (response.isFirstPublishedOnline()) {
            checkWorkflowEventValue(LiteratumEvent.WRK_EVENT_FIRST_ONLINE, response);
        }
    }

    private static void checkFieldValue(String actualValue, String... expectedValues) {
        if (!ArrayUtils.contains(expectedValues, actualValue)) {
            throw new JsonParseException(String.format("Unexpected value %s, has to be %s",
                    actualValue, Arrays.toString(expectedValues)));
        }
    }

    private static void checkWorkflowEventValue(String eventDateType, LiteratumSentConfirm response) {
        String workflowEventValue = response.getWorkflowEventValue(eventDateType);
        if (workflowEventValue == null || workflowEventValue.isEmpty()) {
            throw new JsonParseException(String.format("\"workflowEventValue\" for \"%s\" is not found or empty",
                                                       eventDateType));
        }
    }

    public static LiteratumSentConfirm parse(String rawResponse) {
        LiteratumSentConfirm response = GSON.fromJson(rawResponse, LiteratumSentConfirm.class);
        response.setRawData(rawResponse);
        return response;
    }

    public static String asJsonString(LiteratumSentConfirm response) {
        return GSON.toJson(response, LiteratumSentConfirm.class);
    }
}
