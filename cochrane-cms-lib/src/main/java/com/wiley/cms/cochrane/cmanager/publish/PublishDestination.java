package com.wiley.cms.cochrane.cmanager.publish;

import java.util.Collection;
import java.util.LinkedHashMap;

import com.wiley.cms.cochrane.cmanager.data.PublishTypeEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 5/27/2016
 *
 *  It has fixed oredering.
 */
public enum PublishDestination {

    NONE, WOL(PubType.TYPE_WOL) {
        // 1
        @Override
        public boolean isMandatoryPubType(String pubType) {
            return PubType.TYPE_WOL.equals(pubType);
        }

        @Override
        public String getLastType() {
            return PubType.TYPE_WOL;
        }

        @Override
        public boolean hasPubType(String pubType) {
            return isMandatoryPubType(pubType);
        }

        @Override
        public int getWhenReadyTypeId() {
            return getWhenReadyTypeId(PubType.TYPE_WOL);
        }

        @Override
        public String getShortStr() {
            return "WOL";
        }

        @Override
        protected String getAwaitingStr() {
            return "awaiting EPOCH";
        }

    }, SEMANTICO(PubType.TYPE_SEMANTICO) {
        // 2
        @Override
        public boolean isMandatoryPubType(String pubType) {
            return PubType.TYPE_SEMANTICO.equals(pubType);
        }

        @Override
        public boolean hasPubType(String pubType) {
            return SEMANTICO.isMandatoryPubType(pubType) || WOL.isMandatoryPubType(pubType);
        }

        @Override
        public int getWhenReadyTypeId() {
            return getWhenReadyTypeId(PubType.TYPE_SEMANTICO);
        }

        @Override
        public Collection<Integer> getOnPubEventTypeIds(boolean realTimeOnly) {
            return realTimeOnly ? PubType.SEMANTICO_RT_TYPES : PubType.SEMANTICO_DB_TYPES;
        }

        @Override
        public String getShortStr() {
            return "HW";
        }

    }, SEMANTICO_WOL(PubType.TYPE_SEMANTICO, PubType.TYPE_WOL) {
        // 3
        @Override
        public boolean isMandatoryPubType(String pubType) {
            return SEMANTICO.isMandatoryPubType(pubType) || WOL.isMandatoryPubType(pubType);
        }

        @Override
        public boolean hasPubType(String pubType) {
            return isMandatoryPubType(pubType) || PubType.TYPE_SEMANTICO_TOPICS.equals(pubType);
        }

        @Override
        public int checkNotified(int currentNotified, int pubType) {
            return checkNotifiedFor2Systems(currentNotified, pubType, SEMANTICO, PubType.TYPE_SEMANTICO, WOL,
                PubType.TYPE_WOL);
        }

        @Override
        public String getAwaitingStr(int notified) {
            return getAwaitingStrFor2Systems(notified, WOL, SEMANTICO, getAwaitingStr());
        }

        @Override
        public boolean hasDelayedPubType(int currentNotified) {
            return currentNotified == WOL.ordinal();
        }

    }, WOLLIT(PubType.TYPE_LITERATUM) {
        // 4
        @Override
        public boolean isMandatoryPubType(String pubType) {
            return PubType.TYPE_LITERATUM.equals(pubType);
        }

        @Override
        public int getWhenReadyTypeId() {
            return getWhenReadyTypeId(PubType.TYPE_LITERATUM);
        }

        @Override
        public Collection<Integer> getOnPubEventTypeIds(boolean realTimeOnly) {
            return PubType.LITERATUM_RT_TYPES;
        }

        @Override
        public String getShortStr() {
            return "WOLLIT";
        }

        @Override
        public String getLastType() {
            return PubType.TYPE_LITERATUM;
        }
    },

    LITERATUM_WOL(PubType.TYPE_LITERATUM, PubType.TYPE_WOL), // it's don't use

    LITERATUM_HW(PubType.TYPE_LITERATUM, PubType.TYPE_SEMANTICO) {
        // 6
        @Override
        public boolean isMandatoryPubType(String pubType) {
            return SEMANTICO.isMandatoryPubType(pubType) || WOLLIT.isMandatoryPubType(pubType);
        }

        @Override
        public boolean hasPubType(String pubType) {
            return isMandatoryPubType(pubType) || PubType.TYPE_SEMANTICO_TOPICS.equals(pubType)
                    || PubType.TYPE_SEMANTICO_DELETE.equals(pubType);
        }

        @Override
        public int checkNotified(int currentNotified, int pubType) {
            return checkNotifiedFor2Systems(currentNotified, pubType, SEMANTICO, PubType.TYPE_SEMANTICO, WOLLIT,
                PubType.TYPE_LITERATUM);
        }

        @Override
        public String getAwaitingStr(int notified) {
            return getAwaitingStrFor2Systems(notified, WOLLIT, SEMANTICO, getAwaitingStr());
        }

        @Override
        public boolean hasDelayedPubType(int currentNotified) {
            return currentNotified == WOLLIT.ordinal();
        }
    };

    private LinkedHashMap<String, Integer> mainTypes = new LinkedHashMap<>();

    PublishDestination(String... types) {
        for (String type: types) {
            mainTypes.put(type, null);
        }
    }

    public boolean hasPubType(String pubType) {
        return isMandatoryPubType(pubType);
    }

    public Collection<String> getMainTypes() {
        return mainTypes.keySet();
    }

    public String getLastType() {
        return PubType.TYPE_SEMANTICO;
    }

    public final boolean isWhenReadyType(String keyType) {
        return mainTypes.containsKey(keyType);
    }

    public int getWhenReadyTypeId(String keyType) {
        if (!isWhenReadyType(keyType)) {
            return DbEntity.NOT_EXIST_ID;
        }
        Integer wrTypeId = mainTypes.get(keyType);
        if (wrTypeId == null) {
            wrTypeId = PublishTypeEntity.getNamedEntityId(PublishProfile.buildExportDbName(keyType, true, false));
            mainTypes.put(keyType, wrTypeId);
        }
        return wrTypeId;
    }

    public Collection<Integer> getWhenReadyTypeIds() {
        return initWhenReadyTypeIds();
    }

    public Collection<Integer> getOnPubEventTypeIds(boolean realTimeOnly) {
        return getWhenReadyTypeIds();
    }

    public int getWhenReadyTypeId() {
        return DbEntity.NOT_EXIST_ID;
    }

    public boolean isMandatoryPubType(String type) {
        return false;
    }

    /**
     * It checks if a publication type and a current notified value correspond this destination type to add
     * the publication degree to the notified value in case of success.
     * 
     * @param currentNotified  the current notified value
     * @param pubType          the specified publication type
     * @return                 0 if the pub type doesn't correspond this destination;
     *                         the value updated with the publication degree if the pub type and the initial value are
     *                                                                                     valid for this destination;
     *                         the initial value not changed in other cases;
     */
    public int checkNotified(int currentNotified, int pubType) {

        if (getWhenReadyTypeId() != pubType) {
            return 0;
        }

        boolean notPublished = !PublishedAbstractEntity.isNotifiedOnPublished(currentNotified);
        int ordinal = ordinal();

        int ret = currentNotified;
        return notPublished && ret == ordinal ? ordinal + PublishedAbstractEntity.PUB_NOTIFIED : ret;
    }

    public boolean hasDelayedPubType(int currentNotified) {
        return false;
    }

    public String getAwaitingStr(int notified) {
        return getAwaitingStr() + ": " + toString();
    }

    public String getShortStr() {
        return "";
    }

    protected String getAwaitingStr() {
        return "awaiting publication";
    }

    private static String getAwaitingStrFor2Systems(int notified, PublishDestination pd1,
                                                    PublishDestination pd2, String s) {
        return pd1.ordinal() == notified ? s + ": " + pd2.getShortStr()
                : (pd2.ordinal() == notified ? s + ": " + pd1.getShortStr() : s);
    }

    private Collection<Integer> initWhenReadyTypeIds() {
        Collection<String> list = getMainTypes();
        for (String type: list) {
            Integer wrTypeId = mainTypes.get(type);
            if (wrTypeId == null) {
                wrTypeId = PublishTypeEntity.getNamedEntityId(PublishProfile.buildExportDbName(type, true, false));
                mainTypes.put(type, wrTypeId);
            }
        }
        return mainTypes.values();
    }

    protected int checkNotifiedFor2Systems(int currentNotified, int pubType, PublishDestination pd1, String type1,
                                           PublishDestination pd2, String type2) {
        int ret = 0;

        int ordinal = ordinal();
        boolean notPublished = !PublishedAbstractEntity.isNotifiedOnPublished(currentNotified);

        if (pubType == getWhenReadyTypeId(type1)) {
            ret = notPublished && currentNotified != pd1.ordinal() ? currentNotified + pd1.ordinal() : currentNotified;
            ret = ret == LITERATUM_HW.ordinal() + SEMANTICO.ordinal() ? LITERATUM_HW.ordinal() : ret;
        } else if (pubType == getWhenReadyTypeId(type2)) {
            ret = notPublished && currentNotified != pd2.ordinal() ? currentNotified + pd2.ordinal() : currentNotified;
        }
        ret = (ret == ordinal) ? ordinal + PublishedAbstractEntity.PUB_NOTIFIED : ret;

        return ret;
    }
}
