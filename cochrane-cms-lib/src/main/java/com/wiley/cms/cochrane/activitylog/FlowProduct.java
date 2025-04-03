package com.wiley.cms.cochrane.activitylog;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.record.IKibanaRecord;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.tes.util.Pair;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 3/13/2021
 */
public class FlowProduct extends FlowProductPackage {
    private static final long serialVersionUID = 1L;

    private String publicationStatus;
    private String sourceStatus;
    private String stage;
    private String onlineDate;
    private String firstOnlineDate;
    private String spdDate;
    private SPDState spd = SPDState.NONE;
    private State flowState = State.UNDEFINED;
    private int highPriority;

    private Map<String, IFlowProduct> translations;
    private Map<String, Pair<FlowLogEntity, IFlowProduct>> oldTranslations;

    private final IKibanaRecord kbRecord;

    public FlowProduct(String pubName, String doi, Integer dfId, BaseType bt, String vendor, IKibanaRecord record) {
        super(pubName, doi, dfId, bt, vendor);

        kbRecord = record;
    }

    @Override
    public void sPD(boolean value) {
        spd = value ? SPDState.ON : SPDState.OFF;
    }

    @Override
    public FlowProduct.SPDState sPD() {
        return spd;
    }

    @Override
    public String getPublicationType() {
        return publicationStatus;
    }

    @Override
    public void setPublicationType(String value) {
        this.publicationStatus = getNull4Empty(value);
    }

    public IFlowProduct getTranslation(String language) {
        return translations == null ? null : translations.get(language);
    }

    @Override
    public Collection<IFlowProduct> getTranslations() {
        return translations == null ? Collections.emptyList() : translations.values();
    }

    public Set<String> getLanguages() {
        return translations == null ? Collections.emptySet() : translations.keySet();
    }

    public boolean hasLanguages() {
        return translations != null && !translations.isEmpty();
    }

    public IFlowProduct addTranslation(String doi, String language, boolean retracted) {
        if (translations == null) {
            translations = new HashMap<>();
        }
        IFlowProduct ret = createTranslation(doi, language, retracted);
        translations.put(language, ret);
        return ret;
    }

    @Override
    public Collection<Pair<FlowLogEntity, IFlowProduct>> getDeletedTranslations() {
        return oldTranslations != null ? oldTranslations.values() : Collections.emptyList();
    }

    public void addDeletedTranslation(FlowLogEntity flowLog, IFlowProduct translation) {
        if (oldTranslations == null) {
            oldTranslations = new HashMap<>();
        }
        oldTranslations.put(translation.getLanguage(), new Pair<>(flowLog, translation));
    }

    public IFlowProduct createTranslation(String externalDoi, String language, boolean retracted) {
        String doi = externalDoi != null ? externalDoi : getDOI();
        String publisherId = externalDoi != null ? RevmanMetadataHelper.parsePubName(externalDoi) : getPubCode();
        String type = getType();
        String vendor = getVendor();
        Integer dfId = getSourcePackageId();

        return new FlowProductPart(FilePathBuilder.buildTAName(language, publisherId), doi, language, dfId) {
                private static final long serialVersionUID = 1L;

                @Override
                public String getLanguage() {
                    return language;
                }

                @Override
                public String getType() {
                    return type;
                }

                @Override
                public String getDOI() {
                    return doi;
                }

                @Override
                public int getDbType() {
                    return DatabaseEntity.CDSR_TA_KEY;
                }

                @Override
                public String getStage() {
                    return retracted ? RETRACTED : null;
                }

                @Override
                public Integer getSourcePackageId() {
                    return dfId;
                }

                @Override
                public String getVendor() {
                    return vendor;
                }
        };
    }

    @Override
    public String getStage() {
        return stage;
    }

    @Override
    public void setStage(String stage) {
        this.stage = stage;
    }

    @Override
    public String getSourceStatus() {
        return sourceStatus;
    }

    @Override
    public void setSourceStatus(String value) {
        this.sourceStatus = getNull4Empty(value);
    }

    @Override
    public boolean isHighProfile() {
        return AriesHelper.isHighProfile(getHighPriority());
    }

    @Override
    public void setHighProfile(boolean highProfile) {
        setHighPriority(AriesHelper.addHighProfile(highProfile, getHighPriority()));
    }

    @Override
    public String getFrequency() {
        return AriesHelper.isHighFrequency(getHighPriority()) ? HWFreq.HIGH.getValue() : HWFreq.REAL_TIME.getValue();
    }

    @Override
    public boolean isHighFrequency() {
        return AriesHelper.isHighFrequency(getHighPriority());
    }

    public void setHighFrequency(boolean highFrequency) {
        setHighPriority(AriesHelper.addHighFrequency(highFrequency, getHighPriority()));
    }

    @Override
    public int getHighPriority() {
        return highPriority;
    }

    @Override
    public void setHighPriority(int hp) {
        highPriority = hp;
    }

    @Override
    public FlowProduct.State getFlowState() {
        return flowState;
    }

    public void setFlowState(FlowProduct.State flowState) {
        this.flowState = flowState;
    }

    void setProductState(FlowProduct.State productState, String language) {
        if (language != null) {
            IFlowProduct ta = getTranslation(language);
            if (ta != null) {
                ta.getState().setState(ta, productState);
            }
        } else {
            getState().setState(this, productState);
        }
    }

    public void setFlowAndProductState(FlowProduct.State state, String language) {
        setFlowState(state);
        setProductState(state, language);
    }

    @Override
    public String getFirstOnlineDate() {
        return firstOnlineDate;
    }

    @Override
    public void setFirstOnlineDate(String date) {
        firstOnlineDate = getNull4Empty(date);
    }

    @Override
    public String getOnlineDate() {
        return onlineDate;
    }

    @Override
    public void setOnlineDate(String date) {
        onlineDate = getNull4Empty(date);
    }

    @Override
    public String getSPDDate() {
        return spdDate;
    }

    @Override
    public void setSPDDate(String date) {
        spdDate = getNull4Empty(date);
    }

    /**
     * A state of SPD product
     */
    public enum SPDState {
        NONE {
            @Override
            public boolean is() {
                return false;
            }
        },
        ON {
            @Override
            public boolean on() {
                return true;
            }
        },
        OFF {
            @Override
            public boolean off() {
                return true;
            }
        };

        public static SPDState get(int value) {
            SPDState[] values = values();
            return value >= 0 && values.length > value ? values[value] : NONE;
        }

        public boolean off() {
            return false;
        }

        public boolean on() {
            return false;
        }

        public boolean is() {
            return true;
        }
    }

    /**
     *  Statuses that are determined the current product and flow state
     *  TR:      UNDEFINED -> RECEIVED -> [CREATED | DELETED | RETRACTED(DELETED] -> PUBLISHED
     *  ARTICLE: UNDEFINED -> RECEIVED -> CREATED -> [PUBLISHED | DELETED (SPD)]
     *  FLOW:    UNDEFINED -> RECEIVED -> PUBLISHED -> PUBLISHED_DS
     *                                  
     */
    public enum State {
        NONE(Constants.NA) {
            @Override
            boolean canSet(IFlowProduct product, State newState) {
                return UNDEFINED == newState;
            }
        },
        UNDEFINED(Constants.NA) {
            @Override
            boolean canSet(IFlowProduct product, State newState) {
                return RECEIVED == newState;
            }
        },
        RECEIVED("Received"),
        CREATED("Created"),
        DELETED("Deleted"),
        PUBLISHED("Published") {
            @Override
            boolean canSet(IFlowProduct product, State newState) {
                return PUBLISHED_DS != newState;
            }
        },
        PUBLISHED_DS(PUBLISHED.label);

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        void setState(IFlowProduct product, State newState) {
            if (canSet(product, newState)) {
                product.setState(newState);
            }
        }

        boolean canSet(IFlowProduct product, State newState) {
            return true;
        }

        static State byEvent(int event, boolean retracted, boolean newDoi, boolean offline, boolean error) {
            return error ? byError(event, retracted, newDoi, offline) : byEvent(event, retracted, newDoi, offline);
        }

        private static State byError(int event, boolean retracted, boolean newDoi, boolean offline) {
            State state;
            switch (event) {
                case ILogEvent.PRODUCT_VALIDATED:
                case ILogEvent.PRODUCT_UNPACKED:
                    state = NONE;
                    break;
                default:
                    state = byEvent(event, retracted, newDoi, offline);
                    break;
            }
            return state;
        }

        private static State byEvent(int event, boolean retracted, boolean newDoi, boolean offline) {
            State state;
            switch (event) {
                case ILogEvent.PRODUCT_NOTIFIED_ON_RECEIVED:
                case ILogEvent.PRODUCT_ARIES_ACK_ON_RECEIVED:
                case ILogEvent.PRODUCT_UNPACKED:
                case ILogEvent.PRODUCT_CONVERTED:
                case ILogEvent.PRODUCT_RENDERED:
                    state = RECEIVED;
                    break;
                case ILogEvent.PRODUCT_DELETED:
                case ILogEvent.PRODUCT_RETRACTED:
                case ILogEvent.PRODUCT_OFFLINE_HW:
                    state = DELETED;
                    break;
                case ILogEvent.PRODUCT_CREATED:
                    state = CREATED;
                    break;
                case ILogEvent.PRODUCT_SAVED:
                case ILogEvent.PRODUCT_PUBLISHING_STARTED:
                case ILogEvent.PRODUCT_SENT_WOLLIT:
                case ILogEvent.PRODUCT_PUBLISHED_WOLLIT:
                case ILogEvent.PRODUCT_SENT_HW:
                    state = afterSaved(retracted, newDoi);
                    break;
                case ILogEvent.PRODUCT_NOTIFIED_ON_PUBLISHED:
                    state = offline ? DELETED : PUBLISHED;
                    break;
                case ILogEvent.PRODUCT_SENT_DS:
                    state = PUBLISHED_DS;
                    break;
                case ILogEvent.PRODUCT_PUBLISHED_HW:
                case ILogEvent.PRODUCT_ARIES_ACK_ON_PUBLISHED:
                    state = onPublished(retracted);
                    break;
                default:
                    state = onReceived(offline);
            }
            return state;
        }

        static State onReceived(boolean offline) {
            return offline ? RECEIVED : UNDEFINED;
        }

        private static State afterSaved(boolean retracted, boolean newDoi) {
            return retracted ? DELETED : (newDoi ? CREATED : RECEIVED);
        }

        private static State onPublished(boolean retracted) {
            return retracted ? DELETED : PUBLISHED;
        }
    }
}
