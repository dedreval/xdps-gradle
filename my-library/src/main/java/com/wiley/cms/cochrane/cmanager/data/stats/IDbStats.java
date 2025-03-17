package com.wiley.cms.cochrane.cmanager.data.stats;

import java.beans.Transient;
import java.time.ZoneId;
import java.util.Date;

import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.tes.util.Now;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 9/18/2020
 */
public interface IDbStats {

    Date getGenerationDate();

    int getTotal();

    int getTotalReviews();

    int getTotalProtocols();

    int getTotalNew();

    int getReviewsNew();

    int getProtocolsNew();

    int getTotalUpdated();

    int getReviewsUpdated();

    int getProtocolsUpdated();

    int getReviewsWithdrawn();

    int getProtocolsWithdrawn();

    int getOnlyNS();

    int getNSAndCC();

    int getOnlyCC();

    int getTranslations();

    int getMeshUpdated();

    @Transient
    String getDbName();

    @Transient
    default String getGenerationDateStr() {
        return Now.DATE_TIME_FORMATTER.format(getGenerationDate().toInstant().atZone(
                ZoneId.systemDefault()).toLocalDateTime());
    }

    @Transient
    default String getShortDbName() {
        return BaseType.find(getDbName()).get().getShortName();
    }
}
