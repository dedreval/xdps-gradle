package com.wiley.cms.cochrane.cmanager.publish;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.UUIDEntity;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsJTException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.PreviousVersionException;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO;
import com.wiley.cms.cochrane.cmanager.data.IContentRoom;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitywrapper.PublishWrapper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.event.LiteratumSentConfirm;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.res.CmsResourceInitializer;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.converter.ml3g.Ml3gAssetsManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.services.LiteratumEvent;
import com.wiley.cms.cochrane.services.PublishDate;
import com.wiley.cms.cochrane.services.WREvent;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.handler.NamedHandler;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.FileUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;
import com.wiley.tes.util.res.Property;
import com.wiley.tes.util.res.Res;
import com.wiley.tes.util.res.Settings;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 11.09.12
 */
public final class PublishHelper {

    private static final int MSG_PARAM_PUBLISH = 0;
    private static final int MSG_PARAM_DB_ID   = 1;
    private static final int MSG_PARAM_DB_NAME = 1;
    private static final int MSG_PARAM_DF_ID   = 2;

    private static final Logger LOG = Logger.getLogger(PublishHelper.class);

    private static final String[] EMPTY = new String[0];

    private static final Res<Property> PUB_DATES_THRESHOLD_VALIDATION
                = CochraneCMSPropertyNames.getActualPublicationDateValidated();
    private static final Res<Property> PUB_DATES_THRESHOLD_PAST
                = CochraneCMSPropertyNames.getActualPublicationDateThresholdPast();
    private static final Res<Property> PUB_DATES_THRESHOLD_FUTURE
                = CochraneCMSPropertyNames.getActualPublicationDateThresholdFuture();
    private static final Res<Property> PUB_DATES_SELF_CITATION_CHECK
                = CochraneCMSPropertyNames.getSelfCitationCheckForAmended();
    private static final Res<Property> PUB_DATES_FIRST_ONLINE_CHECK = CochraneCMSPropertyNames.getFirstOnlineCheck();
    private static final Res<Property> PUB_DATES_FINAL_ONLINE_CHECK = CochraneCMSPropertyNames.getFinalOnlineCheck();

    private static final Res<Property> PUB_DATES_SPD_VALIDATION = Property.get(
        "cms.cochrane.support-publication-dates.spd.validation", Boolean.TRUE.toString());

    private static final Res<Property> PUB_DATES_SPD_TIME_POSTFIX = Property.get(
        "cms.cochrane.support-publication-dates.spd.time", "");

    private static final Res<Settings> WOLLIT_DB_MAP = CmsResourceInitializer.getLiteratumDbMapping();
    private static final Res<Settings> WOLLIT_DOI_DB_MAP = CmsResourceInitializer.getWOLLITDoiDbMapping();
    private static final Res<Settings> HW_DOI_DB_MAP = CmsResourceInitializer.getHWDoiDbMapping();
    private static final Res<Settings> DS_DOI_DB_MAP = CmsResourceInitializer.getDsDoiDbMapping();
    private static final Res<Settings> UDW_DB_MAP = CmsResourceInitializer.getUdwDbMapping();

    private static final Map<String, Tail> UNIQUE_PACKAGE_TAILS_LIT = new HashMap<>();
    private static final Map<String, Tail> UNIQUE_PACKAGE_TAILS = new HashMap<>();

    static {
        UNIQUE_PACKAGE_TAILS_LIT.put(CochraneCMSPropertyNames.getCentralDbName(),
                new Tail(Tail.MAX_UNIQUE_PACKAGE_TAIL_LIT));
        UNIQUE_PACKAGE_TAILS_LIT.put(CochraneCMSPropertyNames.getCcaDbName(),
                new Tail(Tail.MAX_UNIQUE_PACKAGE_TAIL_LIT));
        UNIQUE_PACKAGE_TAILS_LIT.put(CochraneCMSPropertyNames.getCDSRDbName(),
                new Tail(Tail.MAX_UNIQUE_PACKAGE_TAIL_LIT));
        UNIQUE_PACKAGE_TAILS_LIT.put(CochraneCMSPropertyNames.getEditorialDbName(),
                new Tail(Tail.MAX_UNIQUE_PACKAGE_TAIL_LIT));

        UNIQUE_PACKAGE_TAILS.put(CochraneCMSPropertyNames.getCentralDbName(),
                new Tail(Tail.MAX_UNIQUE_PACKAGE_TAIL_HW));
        UNIQUE_PACKAGE_TAILS.put(CochraneCMSPropertyNames.getCcaDbName(),
                new Tail(Tail.MAX_UNIQUE_PACKAGE_TAIL_HW));
        UNIQUE_PACKAGE_TAILS.put(CochraneCMSPropertyNames.getCDSRDbName(),
                new Tail(Tail.MAX_UNIQUE_PACKAGE_TAIL_HW));
        UNIQUE_PACKAGE_TAILS.put(CochraneCMSPropertyNames.getEditorialDbName(),
                new Tail(Tail.MAX_UNIQUE_PACKAGE_TAIL_HW));
    }

    private PublishHelper() {
    }

    public static String checkSPD(BaseType bt, @NotNull String targetOnlinePubDate, LocalDate compareDate,
                                  boolean addOffset) throws CmsException {
        if (!targetOnlinePubDate.isEmpty()) {
            if (bt != null && bt.isCCA()) {
                Map<String, String> map = new HashMap<>();
                map.put(Constants.SPD, targetOnlinePubDate);
                //map.put(MessageSender.MSG_PARAM_DATABASE, bt.getShortName());
                throw new CmsException(CochraneCMSProperties.getProperty("spd_not_supported", map));
            }
            String dateWithOffset = targetOnlinePubDate;
            if (addOffset) {
                dateWithOffset = targetOnlinePubDate + PUB_DATES_SPD_TIME_POSTFIX.get().getValue();
            }
            OffsetDateTime ofdt = OffsetDateTime.parse(dateWithOffset, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            LocalDate ld = Date.from(ofdt.toInstant()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (PUB_DATES_SPD_VALIDATION.get().asBoolean() && !ld.isAfter(compareDate)) {
                Map<String, String> map = new HashMap<>();
                map.put(Constants.SPD, dateWithOffset);
                map.put("today", compareDate.toString());
                throw new CmsException(CochraneCMSProperties.getProperty("spd_not_valid", map));
            }
            return dateWithOffset;
        }
        return null;
    }

    public static String buildPublicationEventErrorMessage(String subj, String err, String event) {
        return buildPublicationEventErrorMessage(subj, err, event, "an error");
    }

    public static String buildPublicationEventWarningMessage(String subj, String err, String event) {
        return buildPublicationEventErrorMessage(subj, err, event, "a warning");
    }

    private static String buildPublicationEventErrorMessage(String subj, String err, String event, String errOrWarn) {
        return String.format(CochraneCMSProperties.getProperty("literatum.publishing.event.error"),
                subj, errOrWarn, err, event);
    }

    static boolean setOutputPublishDates(@NotNull RecordMetadataEntity rme, @NotNull Date startDate,
                                         @NotNull PublishDate dateOnlineFinalForm,
                                         PublishDate dateFirstOnline, PublishDate dateOnlineCitation,
                                         boolean firstOnline, @NotNull WREvent event) throws CmsJTException {
        if (PUB_DATES_THRESHOLD_VALIDATION.get().asBoolean()) {
            checkDateFromPast(dateOnlineFinalForm.date(), startDate, rme);
            checkDateFromFuture(dateOnlineFinalForm.date(), rme);
        }
        boolean changed = notEqualDatesOrNull(rme.getPublishedDate(), dateOnlineFinalForm.date());
        boolean spd = rme.isScheduled();

        rme.setPublishedOnlineFinalForm(dateOnlineFinalForm.get());

        int publishedIssue = !spd && rme.getPublishedIssue() == 0 ? rme.getIssue() : rme.getPublishedIssue();
        changed = changed || publishedIssue != dateOnlineFinalForm.issue();

        rme.setPublishedIssue(dateOnlineFinalForm.issue());
        rme.setPublishedDate(dateOnlineFinalForm.date());     

        changed = (rme.getVersion().isNewDoi()
                ? setOutputDatesForNewDoi(rme, dateFirstOnline, dateOnlineCitation, firstOnline)
                : setOutputDatesForExistingDoi(rme, dateFirstOnline, dateOnlineCitation, firstOnline)) || changed;

        if (spd) {
            rme.setSPDChanged(changed);
            if (changed) {
                MessageSender.sendReport(MessageSender.MSG_TITLE_PUBLISH_EVENT_WARN, event.getBaseType().getId(),
                    buildPublicationEventWarningMessage(RevmanMetadataHelper.buildPubName(
                        rme.getCdNumber(), rme.getPubNumber()), String.format(
                            "incoming SPD was %s, but event's first / final dates are %s / %s - they will be set",
                                rme.getScheduledDateCT(), dateFirstOnline.get(), dateOnlineFinalForm.get()),
                                    event.getRawData()));
            }
        }

        return changed;
    }

    private static void checkDateFromPast(Date eventDate, Date date, RecordMetadataEntity rme) throws CmsJTException {
        int threshold = PUB_DATES_THRESHOLD_PAST.get().asInteger() * Now.MS_IN_SEC;
        if (threshold >= 0  && eventDate.getTime() + threshold < date.getTime()) {
            throw new CmsJTException(String.format("%s: '%s'.%s is older than %s when it was taken",
                    rme.getPublisherId(), LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, eventDate, date));
        }
    }

    private static void checkDateFromFuture(Date eventDate, RecordMetadataEntity rme) throws CmsJTException {
        int threshold = PUB_DATES_THRESHOLD_FUTURE.get().asInteger() * Now.MS_IN_SEC;
        long now = System.currentTimeMillis();
        if (threshold >= 0 && eventDate.getTime() - threshold > now) {
            throw new CmsJTException(String.format("%s: '%s'.%s is newer than now: %s", rme.getPublisherId(),
                    LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, eventDate, new Date(now)));
        }
    }

    private static void checkPublishOnlineFinalForm(RecordMetadataEntity rme, PublishDate dateFirstOnline)
            throws CmsJTException {
        if (!dateFirstOnline.get().equals(rme.getPublishedOnlineFinalForm())) {
            throw new CmsJTException(String.format("%s: '%s'.%s is not equal to'%s'.%s", rme.getPublisherId(),
                LiteratumEvent.WRK_EVENT_FIRST_ONLINE, dateFirstOnline,
                LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, rme.getPublishedOnlineFinalForm()));
        }
    }

    private static boolean setOutputDatesForExistingDoi(RecordMetadataEntity rme,
            PublishDate dateFirstOnline, PublishDate dateOnlineCitation, boolean firstOnline) throws CmsJTException {

        boolean hasFirstOnline = dateFirstOnline != null && !dateFirstOnline.isEmpty();
        boolean hasOnlineCitation = dateOnlineCitation != null && !dateOnlineCitation.isEmpty();

        if (hasFirstOnline != hasOnlineCitation
                || (hasFirstOnline && !dateFirstOnline.get().equals(dateOnlineCitation.get()))) {
            throw new CmsJTException(String.format(
                "%s: '%s'.%s is not equal to '%s'.%s with 'firstPublishedOnline'.%s", rme.getPublisherId(),
                LiteratumEvent.WRK_EVENT_FIRST_ONLINE, dateFirstOnline,
                LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE, dateOnlineCitation, firstOnline));
        }

        boolean changed = false;
        if (hasFirstOnline) {
            checkPublishOnlineFinalForm(rme, dateFirstOnline);
            if (firstOnline) {
                // always replace pre-set values with an actual HW event
                changed = setOutputFirstOnlineDate(rme, dateFirstOnline);
            }
            logWarnForExistingDoi(rme, LiteratumEvent.WRK_EVENT_FIRST_ONLINE, firstOnline);
        }
        if (hasOnlineCitation) {
            // always replace re-set values with an actual HW event
            changed = setOutputCitationDate(rme, dateOnlineCitation) || changed;
            logWarnForExistingDoi(rme, LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE, firstOnline);
        }
        return changed;
    }

    private static boolean setOutputDatesForNewDoi(RecordMetadataEntity rme, PublishDate dateFirstOnline,
        PublishDate dateOnlineCitation, boolean firstOnline) throws CmsJTException {

        boolean hasFirstOnline = dateFirstOnline != null && !dateFirstOnline.isEmpty();
        boolean hasOnlineCitation = dateOnlineCitation != null && !dateOnlineCitation.isEmpty();

        if (hasFirstOnline != hasOnlineCitation || !hasFirstOnline
                || !dateFirstOnline.get().equals(dateOnlineCitation.get())) {
            throw new CmsJTException(String.format(
                "%s: '%s'.%s is empty or not equal to '%s'.%s with 'firstPublishedOnline'.%s for new DOI",
                    rme.getPublisherId(), LiteratumEvent.WRK_EVENT_FIRST_ONLINE, dateFirstOnline,
                    LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE, dateOnlineCitation, firstOnline));
        }
        checkPublishOnlineFinalForm(rme, dateFirstOnline);

        if (rme.getFirstOnline() != null && !rme.isScheduled()) {
            LOG.warn("%s: '%s'.%s of new DOI is not empty", rme.getPublisherId(),
                    LiteratumEvent.WRK_EVENT_FIRST_ONLINE, rme.getFirstOnline());
        }

        // always replace re-set values with an actual HW event
        boolean changed = setOutputFirstOnlineDate(rme, dateFirstOnline);
        return setOutputCitationDate(rme, dateOnlineCitation) || changed;
    }

    private static boolean setOutputFirstOnlineDate(RecordMetadataEntity rme, PublishDate dateFirstOnline) {
        rme.setFirstOnline(dateFirstOnline.get());
        if (dateFirstOnline.issue() != rme.getSelfCitationIssue()) {
            rme.setSelfCitationIssue(dateFirstOnline.issue());
            return true;
        }
        return false;
    }

    private static boolean setOutputCitationDate(RecordMetadataEntity rme, PublishDate dateOnlineCitation) {
        rme.setPublishedOnlineCitation(dateOnlineCitation.get());
        rme.setCitationIssue(dateOnlineCitation.issue());

        if (notEqualDatesOrNull(rme.getCitationLastChanged(), dateOnlineCitation.date())) {
            rme.setCitationLastChanged(dateOnlineCitation.date());
            return true;
        }
        return false;
    }

    private static void logWarnForExistingDoi(RecordMetadataEntity rme, String eventField, boolean firstOnline) {
        LOG.warn("%s: '%s' event date for existing DOI is not empty, 'firstPublishedOnline'.%s",
                rme.getPublisherId(), eventField, firstOnline);
    }

    private static void logWarnForExistingDoi(RecordMetadataEntity rme, String eventField, String cur, String prev) {
        LOG.warn(String.format("%s: '%s'.%s is different from existing %s",
                rme.getPublisherId(), eventField, cur, prev));
    }

    public static void setInputPublishDates(BaseType bt, CDSRMetaVO meta, RecordMetadataEntity cur,
        RecordMetadataEntity prev, boolean checkDate, PublishDate spdDate, boolean wr) throws PreviousVersionException {

        if (spdDate != null) {
            checkInputDatesForSPD(meta, cur, prev, spdDate);
            return;
        }
        if (!wr && !checkDate) {
            // it is a package for re-load  
            if (prev != null && prev.getVersion().getPubNumber() == prev.getVersion().getPubNumber()) {
                cur.getVersion().setNewDoi(prev.getVersion().isNewDoi());
                setDatesToReload(meta, cur, prev, true);
            } else {
                setDatesToReload(meta, cur, null, true);
            }
            return;
        }

        boolean cdsr = bt.isCDSR();
        boolean strongCheck = checkDate && (cur.isJats() || !cdsr);    // additional newly checks (XDPS-1646)

        if (cur.getPublishedDate() != null) {
            LOG.warn(String.format("%s: source dates for '%s' are not empty %s", cur.getPublisherId(),
                    LiteratumEvent.WRK_EVENT_ONLINE_FINAL_FORM, cur.getPublishDatesStr()));

            if (strongCheck && PUB_DATES_FINAL_ONLINE_CHECK.get().asBoolean()) {
                throw PreviousVersionException.createForMetadata(meta,
                    "an article cannot have a predefined 'final-online-form' publication date");
            }

        } else if (cur.getPublishedIssue() != 0) {
            LOG.warn(String.format("%s: source Volume/Issue are not empty %s", cur.getPublisherId(),
                    cur.getPublishDatesStr()));
            // as a source date was not set => need to reset source Volume/Issue to convert it with predicted value
            cur.setPublishedIssue(0);
            meta.setVersionPubDates(0, null, null);
        }

        if (cur.getCitationIssue() == 0) {
            // pre-set citation issue with self-citation | predict issue for better compatibility with a legacy approach
            cur.setCitationIssue(cur.getSelfCitationIssue() == 0 ? cur.getIssue() : cur.getSelfCitationIssue());
        }

        //if (cur.getPublishedIssue() != cur.getIssue()) {
        //    throw PreviousVersionException.createForMetadata(meta, String.format(
        //        "source Issue %d doesn't match Issue %d this article is uploading to", cur.getPublishedIssue(),
        //            cur.getIssue()));
        //}
        if (cur.getVersion().isNewDoi()) {
            checkInputDatesForNewDoi(meta, cur, strongCheck);
        } else {
            checkInputDatesForExistingDoi(bt, meta, cur, prev, checkDate);
        }
    }

    private static void setDatesToReload(CDSRMetaVO meta, RecordMetadataEntity cur, RecordMetadataEntity prev,
                                         boolean sameIssue) throws PreviousVersionException {
        if (prev != null) {
            if (prev.getPublishedOnlineFinalForm() != null) {
                cur.setPublishedOnlineFinalForm(prev.getPublishedOnlineFinalForm());
                cur.setPublishedDate(prev.getPublishedDate());
                cur.setPublishedIssue(prev.getPublishedIssue());
                meta.setVersionPubDates(prev.getPublishedIssue(), prev.getPublishedDate(),
                        prev.getPublishedOnlineFinalForm());
            }
            if (prev.getFirstOnline() != null) {
                cur.setFirstOnline(prev.getFirstOnline());
                cur.setPublishedOnlineCitation(prev.getPublishedOnlineCitation());
                cur.setCitationLastChanged(prev.getCitationLastChanged());
                cur.setCitationIssue(prev.getCitationIssue());
                cur.setSelfCitationIssue(prev.getSelfCitationIssue());
                meta.setCitationPubDates(prev.getCitationIssue(), prev.getCitationLastChanged(),
                        prev.getPublishedOnlineCitation(), prev.getFirstOnline(), prev.getSelfCitationIssue());
            }
        }
        if (cur.getVersion().isNewDoi()
                && cur.getSelfCitationIssue() != 0 && cur.getIssue() != cur.getSelfCitationIssue()) {
            throw PreviousVersionException.createForMetadata(meta, String.format(
                    "'self-citation' Issue/Volume %d doesn't match Issue/Volume %d this article is uploading to",
                    cur.getSelfCitationIssue(), cur.getIssue()));
        }
        if (sameIssue && cur.getPublishedIssue() != 0 && cur.getIssue() != cur.getPublishedIssue()) {
            throw PreviousVersionException.createForMetadata(meta, String.format(
                    "source Issue/Volume %d doesn't match Issue/Volume %d this article is uploading to",
                    cur.getPublishedIssue(), cur.getIssue()));
        }
    }

    private static void checkInputDatesForSPD(CDSRMetaVO meta, RecordMetadataEntity cur, RecordMetadataEntity prev,
                                              PublishDate spdDate) throws PreviousVersionException {
        if (prev != null && prev.isScheduled()) {
            throw PreviousVersionException.createForMetadata(meta, PreviousVersionException.msgAlreadySubmittedSpdLast(
                    prev.getPublisherId()));
        }
        if (!cur.getVersion().isNewDoi()) {
            throw PreviousVersionException.createForMetadata(meta, PreviousVersionException.msgAlreadyLifeSpd(
                    cur.getPublisherId()));
        }

        // In order to ensure that the correct issue details are delivered to HW and included in the PDF,
        // scheduled publication date should be used as the basis of the predicted issue data
        // (i.e. where SPD is present, the predicted publication date should derive from the SPD.
        // Under the Publication Date flow, the predicted date is passed to the convertor for use in XML generation,
        // and subsequent inclusion in the PDF.
        cur.setPublishedOnlineFinalForm(spdDate.get());
        cur.setPublishedOnlineCitation(spdDate.get());
        cur.setFirstOnline(spdDate.get());
        cur.setSelfCitationIssue(spdDate.issue());
        cur.setCitationIssue(spdDate.issue());
        cur.setCitationLastChanged(spdDate.date());
        cur.setPublishedIssue(spdDate.issue());
        cur.setPublishedDate(spdDate.date());
        cur.setScheduledDateCT(spdDate.get());
        meta.setVersionPubDates(spdDate);
        meta.setCitationPubDates(spdDate.issue(), spdDate.date(), spdDate.get(), spdDate.get(), spdDate.issue());
    }

    private static void checkInputDatesForNewDoi(CDSRMetaVO meta, RecordMetadataEntity cur, boolean check)
            throws PreviousVersionException {
        if (check && (cur.getPublishedOnlineCitation() != null || cur.getFirstOnline() != null
                || cur.getSelfCitationIssue() != 0 || cur.getCitationLastChanged() != null)) {

            LOG.warn(String.format("%s: source Issue/Volume/Date for '%s','%s' are not empty %s", cur.getPublisherId(),
                    LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE,
                    LiteratumEvent.WRK_EVENT_FIRST_ONLINE, cur.getPublishDatesStr()));
            checkInputFirstOnlineDate(meta, cur, true);
        }
    }

    private static void checkInputFirstOnlineDate(CDSRMetaVO meta, RecordMetadataEntity cur, boolean newDoi)
            throws PreviousVersionException {
        if (PUB_DATES_FIRST_ONLINE_CHECK.get().asBoolean()
                && (cur.getCitationLastChanged() != null || meta.getFirstOnline() != null)) {
            String msg = newDoi ? "a new article" : "an article";
            throw PreviousVersionException.createForMetadata(meta,
                    msg + " cannot have a pre-defined first-online publication date");
        }
    }

    public static boolean checkInputOnlineDates(ICDSRMeta meta) {
        boolean ret = !PUB_DATES_FIRST_ONLINE_CHECK.get().asBoolean()
                || (meta.getCitationLastChanged() == null && meta.getFirstOnline() == null);
        return ret && (!PUB_DATES_FINAL_ONLINE_CHECK.get().asBoolean() || meta.getPublishedDate() == null);
    }

    private static void checkInputDatesForExistingDoi(BaseType bt, CDSRMetaVO meta, RecordMetadataEntity cur,
        RecordMetadataEntity prev, boolean check) throws PreviousVersionException {

        if (cur.getCitationIssue() != 0 && cur.getSelfCitationIssue() != 0
                && cur.getCitationIssue() != cur.getSelfCitationIssue()) {
            LOG.warn(String.format("%s: citation Issue/Volume %d is different from self-citation Issue/Volume %d",
                    cur.getPublisherId(), cur.getCitationIssue(), cur.getSelfCitationIssue()));
        }
        if (check) {
            checkInputDatesForExistingDoi(bt, meta, cur, prev);
        }
    }

    private static void checkInputDatesForExistingDoi(BaseType bt, CDSRMetaVO meta, RecordMetadataEntity cur,
            RecordMetadataEntity prev) throws PreviousVersionException {
                      
        if (!bt.isCDSR()) {
            checkInputFirstOnlineDate(meta, cur, false);

        } else if (cur.getCitationLastChanged() == null) {
            throw PreviousVersionException.createForMetadata(meta,
                    PreviousVersionException.msgAmendedArticlePublicationDate());
        } 

        if (cur.isJats() && cur.getSelfCitationIssue() == 0) {
            if (PUB_DATES_SELF_CITATION_CHECK.get().asBoolean())  {
                throw PreviousVersionException.createForMetadata(meta, "no self-citation Issue/Volume provided");
            }
            LOG.warn(String.format("%s: no self-citation Issue/Volume provided", cur.getPublisherId()));

        } else if (prev != null && prev.getSelfCitationIssue() != 0   // todo check for CCA
                && prev.getSelfCitationIssue() != cur.getSelfCitationIssue()) {
            LOG.warn(String.format("%s: self-citation Issue/Volume %d is different from existing %d",
                    cur.getPublisherId(), cur.getSelfCitationIssue(), prev.getSelfCitationIssue()));
        }
        if (cur.isJats() && cur.getCitationIssue() == 0) {
            LOG.warn(String.format("%s: no citation Issue/Volume provided %d",
                    cur.getPublisherId(), cur.getCitationIssue()));

        } else if (prev != null && prev.getCitationIssue() != 0 && prev.getCitationIssue() != cur.getCitationIssue()) {
            LOG.error(String.format("%s: citation Issue/Volume %d is different from existing %d", //todo check for CCA
                    cur.getPublisherId(), cur.getCitationIssue(), prev.getCitationIssue()));
        }

        if (prev != null) {
            if (bt.isCDSR()) {
                checkInputDatesForExistingDoi(cur, prev, meta.getPublishedOnlineCitation(), meta.getFirstOnline());
            } else {
                setDatesToReload(meta, cur, prev, false);
            }
        }
    }

    private static void checkInputDatesForExistingDoi(RecordMetadataEntity cur, RecordMetadataEntity prev,
                                                      String curOnlineCitation, String curFirstOnline) {
        String onlineCitation = prev.getPublishedOnlineCitation();
        if (onlineCitation != null && curOnlineCitation != null && !onlineCitation.equals(curOnlineCitation)) {
            logWarnForExistingDoi(cur, LiteratumEvent.WRK_EVENT_ONLINE_CITATION_ISSUE,
                    curOnlineCitation, onlineCitation);
        }
        Date citationLastChanged = prev.getCitationLastChanged();
        if (notEqualDates(citationLastChanged, cur.getCitationLastChanged())) {
            LOG.warn(String.format("%s: 'cl-date-citation-last-changed' %s is different from existing %s",
                    cur.getPublisherId(), cur.getCitationLastChanged(), citationLastChanged));
        }
        String firstOnline = prev.getFirstOnline();
        if (firstOnline != null && curFirstOnline != null && !firstOnline.equals(curFirstOnline)) {
            logWarnForExistingDoi(cur, LiteratumEvent.WRK_EVENT_FIRST_ONLINE,
                    curFirstOnline, prev.getFirstOnline());
        }
    }

    private static boolean notEqualDates(Date first, Date second) {
        return first != null && second != null
                && TimeUnit.MILLISECONDS.toDays(first.getTime()) != TimeUnit.MILLISECONDS.toDays(second.getTime());
    }

    public static boolean notEqualDatesOrNull(Date first, @NotNull Date second) {
        return first == null
                || TimeUnit.MILLISECONDS.toDays(first.getTime()) != TimeUnit.MILLISECONDS.toDays(second.getTime());
    }

    public static boolean isIssueOutdated(int issueId, String dbName, Map<Integer, ClDbVO> lastDbs) {
        if (!BaseType.find(dbName).get().isCDSR() || CmsUtils.isSpecialIssue(issueId)) {
            return false; // currently only CDSR needs such a check
        }
        int max = 0;
        for (Map.Entry<Integer, ClDbVO> entry: lastDbs.entrySet()) {
            if (entry.getValue().getTitle().equals(dbName)) {
                max = Math.max(max, entry.getValue().getIssue().getId());
            }
        }
        return issueId < max;
    }

    public static List<String> getPublishContentUris(IContentRoom contentRoom, Integer version, String recordName,
        boolean outdated, boolean excludeRawData, ContentLocation contentLocation, StringBuilder errs) {

        String dbName = contentRoom.getDbName();
        Integer issueId = contentRoom.getIssueId();
        List<String> assetsUris = Ml3gAssetsManager.getAssetsUris(dbName, issueId, recordName, version,
                contentLocation, outdated, errs);
        if (assetsUris == null) {
            return null;
        }

        if (contentLocation.hasPdf(issueId, dbName, version, recordName)) {
            assetsUris.removeIf(
                    uri -> (excludeRawData && FilePathBuilder.containsRawData(uri)) || uri.endsWith(Extensions.PDF));
            contentLocation.addPdfs(issueId, dbName, version, recordName, assetsUris);

        } else if (excludeRawData) {

            Iterator<String> it = assetsUris.iterator();
            while (it.hasNext()) {
                String uri = it.next();
                if (FilePathBuilder.containsRawData(uri)) {
                    it.remove();
                    break;
                }
            }
        }
        return assetsUris;
    }

    static void onSendDSSuccess(int dfId, IResultsStorage rs) {
        if (dfId != DbEntity.NOT_EXIST_ID) {
            if (rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_WR_PUBLISHING,
                    RecordEntity.STATE_DS_PUBLISHED, dfId) == 0) {
                rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_WR_PUBLISHED,
                        RecordEntity.STATE_DS_PUBLISHED, dfId);
            }
        }
    }

    static void onSendWhenReadySuccess(int dfId, boolean skipEpoch, IResultsStorage rs) {
        if (dfId != DbEntity.NOT_EXIST_ID) {
            rs.setWhenReadyPublishStateByDeliveryFile(RecordEntity.STATE_WR_PUBLISHING,
                skipEpoch ? RecordEntity.STATE_WR_PUBLISHED : RecordEntity.STATE_WAIT_WR_PUBLISHED_NOTIFICATION, dfId);
        }
    }

    public static String buildNowParam(String dbName, Date pubDate, boolean shortFormat) {
        if (shortFormat) {
            return Now.SHORT_DATE_FORMATTER.format(pubDate);
        }
        String ret = Now.LONG_DATE_FORMATTER.format(pubDate);
        return ret.substring(0, ret.length() - 2) + String.format("%02d", UNIQUE_PACKAGE_TAILS.get(dbName).get());
    }

    public static void addCommonAttrs(String nowParamVal, Map<String, String> replaceList) {
        replaceList.put("now", nowParamVal);
    }

    private static String[] getLiteratumAttrs(String dbName) {
        String str = WOLLIT_DB_MAP.get().getStrSetting(dbName);
        return str == null ? EMPTY : str.split("#");
    }

    public static void addLiteratumAttrs(String dbName, boolean newContent, Map<String, String> replaceList) {
        String[] attrs = getLiteratumAttrs(dbName);
        if (attrs.length < 2) {
            return;
        }
        replaceList.put("litname", attrs[0]);
        replaceList.put("litnumber", attrs[1]);

        replaceList.put("sbn",  BaseType.find(dbName).get().getPubInfo(PubType.TYPE_LITERATUM).getSBN());
        replaceList.put("tail", String.format("%04d", UNIQUE_PACKAGE_TAILS_LIT.get(dbName).get()));

        replaceList.put("new", newContent ? "N" : "U");
    }

    public static void addUdwAttrs(String dbName, Map<String, String> replaceList) {
        String udwDbName = UDW_DB_MAP.get().getStrSetting(dbName);
        replaceList.put("udwname", udwDbName == null ? dbName.toUpperCase() : udwDbName);
    }

    public static void checkUUID(int event, PublishWrapper pw, IFlowLogger flowLogger) {
        String currentSid = pw.getTransactionId();
        if (UUIDEntity.UUID.equals(currentSid)) {
            pw.setTransactionId(flowLogger.getTransactionId(event, pw.getId()));
        }
    }

    public static String checkUUID(int event, String oldExportName, String exportFolder, boolean sent,
                                  PublishWrapper pw, IFlowLogger flowLogger, IRepository rps) {
        String oldSid = UUIDEntity.extractUUID(oldExportName);
        String currentSid = pw.getTransactionId();
        if (oldSid == null || (!UUIDEntity.UUID.equals(oldSid) && !sent)) {
            return oldExportName;   // no UUID expected or it was already generated and has not been sent yet
        }
        if (currentSid == null || UUIDEntity.UUID.equals(currentSid)) {  // generate new uuid
            currentSid = flowLogger.getTransactionId(event, pw.getId());
        }
        String exportName = oldExportName;
        if (!oldSid.equals(currentSid)) {
            exportName = exportName.replace(oldSid, currentSid);
            File parent = new File(rps.getRealFilePath(exportFolder));
            File oldFile = new File(parent, oldExportName);
            if (oldFile.exists()) {
                FileUtils.renameFile(oldFile, new File(parent, exportName));
            }
            pw.setTransactionId(currentSid);
        }
        pw.setNewPackageName(exportName);
        return exportName;
    }

    public static String renameLiteratumPackage(boolean toNewContent, String oldPackageName) {
        return oldPackageName.replace(toNewContent ? "U" : "N", toNewContent ? "N" : "U");
    }

    public static String defineLiteratumDoiPrefixByDatabase(String dbName) {
        return WOLLIT_DOI_DB_MAP.get().getSetting(dbName).getValue();
    }

    public static String defineDsDoiPrefixByDatabase(String dbName) {
        return DS_DOI_DB_MAP.get().getSetting(dbName).getValue();
    }

    public static BaseType defineBaseTypeByWolLitDoi(@NotNull String doi) {
        return defineBaseTypeByDoi(WOLLIT_DOI_DB_MAP.get(), doi);
    }

    public static boolean isCentralHWDoi(@NotNull String doi) {
        return isCentralRootDoi(HW_DOI_DB_MAP.get(), doi);
    }

    private static boolean isCentralRootDoi(Settings dbMap, @NotNull String doi) {
        String dbName = dbMap.getStrSetting(doi);
        if (dbName != null) {
            Res<BaseType> bt = BaseType.find(dbName);
            return Res.valid(bt) && bt.get().isCentral();
        }
        return false;
    }

    public static BaseType defineBaseTypeByHWDoi(@NotNull String doi) {
        return defineBaseTypeByDoi(HW_DOI_DB_MAP.get(), doi);
    }

    private static BaseType defineBaseTypeByDoi(Settings dbMap, @NotNull String doi) {
        String dbName = dbMap.getStrSetting(doi);
        if (dbName == null) {
            Collection<Settings.Setting> list = WOLLIT_DOI_DB_MAP.get().getSettings();
            for (Settings.Setting setting : list) {
                if (doi.matches(setting.getLabel())) {
                    dbName = setting.getValue();
                    break;
                }
            }
        }
        Res<BaseType> bt = BaseType.find(dbName);
        return Res.valid(bt) ? bt.get() : null;
    }

    public static String getControlFileContent() throws IOException {
        return FileUtils.readStream(new URL(CochraneCMSProperties.getProperty("cms.resources.control_file.xml")));
    }

    public static File[] getControlFileContent(String dbName, String pubType) {
        try {
            return new File(new URI(buildResourceControlFolder(dbName, pubType))).listFiles();
        } catch (URISyntaxException ue) {
            LOG.error(ue);
            return new File[0];
        }
    }

    public static String getControlFileContent(String dbName, String pubType, String controlName)
            throws IOException {
        String path = buildResourceControlFolder(dbName, pubType) + FilePathCreator.SEPARATOR + controlName
                + Extensions.XML;
        return FileUtils.readStream(new URL(path), "\n");
    }

    private static String buildResourceControlFolder(String dbName, String pubType) {
        return CochraneCMSProperties.getProperty("cms.resources.control_file_folder") + pubType
                + FilePathCreator.SEPARATOR + dbName;
    }

    public static ProcessVO createRecordPublishProcess(String type, Integer[] recordIds) throws Exception {
        return ProcessHelper.createIdPartsProcess(new NamedHandler("PublishingRecords_" + type),
                ProcessType.empty(), IProcessManager.USUAL_PRIORITY, recordIds);
    }

    private static List<PublishWrapper> generatePublishListForDSCentralAut(BaseType bt, int dbId) throws Exception {
        List<PublishWrapper> list = new ArrayList<>();
        Date startDate = new Date();
        PublishWrapper prev = null;
        boolean[] options = {true, false, true};
        BaseType.PubInfo pi = bt.getPubInfo(PubType.TYPE_DS);
        if (pi == null) {
            LOG.error(String.format("no %s configured for %s", PubType.TYPE_DS, bt.getShortName()));
            return list;
        }
        PubType pt = pi.getType();
        List<DeliveryFileVO> dfVOs = ResultStorageFactory.getFactory().getInstance().getDeliveryFiles(dbId);
        IFlowLogger flowLogger = CochraneCMSBeans.getFlowLogger();
        for (DeliveryFileVO dfVO: dfVOs) {
            PublishWrapper pw = PublishWrapper.createIssuePublishWrapperEx(pt.getId(), bt.getId(), dbId, dfVO.getId(),
                    startDate, options);
            pw.setGenerate(true);
            pw.setSend(true);

            pw.setTransactionId(flowLogger.findTransactionId(dfVO.getId()));

            addToPublishList(pw, prev, null, list);
            prev = pw;
        }
        return list;
    }

    public static List<PublishWrapper> generatePublishList(BaseType bt, int dbId, Collection<String> types,
            boolean entire, boolean dsMonthlyCentralAut, Set<String> cdNumbers) throws Exception {

        boolean canUnpack = bt.canUnpack();
        List<PublishWrapper> list = new ArrayList<>();
        Date startDate = new Date();

        PublishWrapper prev = null;

        boolean skipHW = CochraneCMSPropertyNames.isPublishToSemanticoAfterLiteratum();
        PublishWrapper pwLit4HW = null;
        PublishWrapper pwHW = null;

        if (dsMonthlyCentralAut) {
            return generatePublishListForDSCentralAut(bt, dbId);
        }

        boolean[] options = {false, true, false};
        for (BaseType.PubInfo pi: bt.getPubInfo()) {

            PubType pt = pi.getType();
            String majorType = pt.getMajorType();
            String type = pt.getId();

            if (!pt.canSelective() || !types.contains(type)) {
                continue;
            }

            boolean del = pt.canDelete();
            boolean gen = pt.canGenerate();
            boolean unpack = canUnpack && pt.canUnpack();

            PublishWrapper pw = entire ? PublishWrapper.createEntirePublishWrapper(
                    pt.getId(), majorType, bt.getId(), true, startDate)
                : PublishWrapper.createIssuePublishWrapperEx(
                    pt.getId(), bt.getId(), dbId, DbEntity.NOT_EXIST_ID, startDate, options);

            pw.setDelete(del);
            pw.setGenerate(gen);
            pw.setSend(true);
            pw.setUnpack(unpack);

            pw.setStaticContentDisabled(false);
            pw.setHWFrequency(entire ? bt.getEntireHWFrequency().getValue() : bt.getIssueHWFrequency().getValue());

            boolean isLit = pt.isLiteratum();
            boolean isHw = PubType.TYPE_SEMANTICO.equals(type);

            if (skipHW) {
                if (isLit) {
                    pwLit4HW = pw;
                } else if (isHw) {
                    pwHW = pw;
                    continue;   // don't include HW feed to the list for publishing
                }
            }

            addToPublishList(pw, prev, cdNumbers, list);
            prev = pw;
        }

        addHWPublish(pwHW, pwLit4HW, prev, cdNumbers, list);

        return list;
    }

    private static void addHWPublish(PublishWrapper pwHW, PublishWrapper pwLit4HW, PublishWrapper prev,
                                     Set<String> cdNumbers, Collection<PublishWrapper> list) {
        if (pwHW != null && (pwLit4HW == null || !pwLit4HW.setPublishToAwait(pwHW))) {
            addToPublishList(pwHW, prev, cdNumbers, list);
        }
    }

    private static void addToPublishList(PublishWrapper pw, PublishWrapper prev, Set<String> cdNumbers,
                                         Collection<PublishWrapper> list) {
        if (prev == null) {
            list.add(pw);
        } else {
            prev.setNext(pw);
        }
        pw.setCdNumbers(cdNumbers);
    }

    public static ObjectMessage createAcceptPublishMessage(LiteratumSentConfirm response, Session session)
            throws JMSException {
        ObjectMessage msg = session.createObjectMessage();
        msg.setObject(response);
        return msg;
    }

    static ObjectMessage createPublishMessage(PublishWrapper publish, int dbId, Session session)
        throws JMSException {

        ObjectMessage msg = session.createObjectMessage();
        Object[] param = new Object[MSG_PARAM_DB_ID + 1];
        param[MSG_PARAM_PUBLISH] = publish;
        param[MSG_PARAM_DB_ID] = dbId;
        msg.setObject(param);

        if (publish.isPublishEntityExist()) {
            msg.setStringProperty(JMSSender.GROUP_PARAM, "" + publish.getId());
        }
        return msg;
    }

    static ObjectMessage createPublishMessage(PublishWrapper publish, String dbName, Session session)
        throws JMSException {

        ObjectMessage msg = session.createObjectMessage();
        Object[] param = new Object[MSG_PARAM_DB_NAME + 1];
        param[MSG_PARAM_PUBLISH] = publish;
        param[MSG_PARAM_DB_NAME] = dbName;
        msg.setObject(param);

        if (publish.isPublishEntityExist()) {
            msg.setStringProperty(JMSSender.GROUP_PARAM, "" + publish.getId());
        }
        return msg;
    }

    static ObjectMessage createPublishMessage(PublishWrapper publish, int dbId, int dfId, Session session)
        throws JMSException {

        ObjectMessage msg = session.createObjectMessage();
        Object[] param = new Object[MSG_PARAM_DF_ID + 1];
        param[MSG_PARAM_PUBLISH] = publish;
        param[MSG_PARAM_DB_ID] = dbId;
        param[MSG_PARAM_DF_ID] = dfId;
        msg.setObject(param);

        if (publish.isPublishEntityExist()) {
            msg.setStringProperty(JMSSender.GROUP_PARAM, "" + publish.getId());
        }
        return msg;
    }

    static PublishWrapper getPublishWrapper(Message message) throws Exception {
        return (PublishWrapper) getParam(message, MSG_PARAM_PUBLISH);
    }

    static int getDataBaseId(Message message) throws Exception {
        return (Integer) getParam(message, MSG_PARAM_DB_ID);
    }

    static String getDataBaseName(Message message) throws Exception {
        return (String) getParam(message, MSG_PARAM_DB_NAME);
    }

    static int getDeliveryFileId(Message message) throws Exception {
        return (Integer) getParam(message, MSG_PARAM_DF_ID);
    }

    private static Object getParam(Message message, int idx) throws Exception {
        if (!(message instanceof ObjectMessage)) {
            throw new Exception("ObjectMessage expected!");
        }
        Serializable obj = ((ObjectMessage) message).getObject();
        return ((Object[]) obj)[idx];
    }

    private static class Tail {

        private static final short MAX_UNIQUE_PACKAGE_TAIL_LIT = 9999;
        private static final short MAX_UNIQUE_PACKAGE_TAIL_HW = 59;

        private final short maxValue;
        private short value;

        Tail(short maxValue) {
            this.maxValue = maxValue;
        }

        synchronized short get() {

            short ret = value;
            if (value == maxValue) {
                value = 0;
            } else {
                value++;
            }
            return ret;
        }
    }
}
