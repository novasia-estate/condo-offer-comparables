package estate.novasia.comps;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Set;

import static estate.novasia.comps.OfferComparison.Reason.*;
import static estate.novasia.comps.OfferComparison.Stage.*;

public final class OfferComparisonChecks {
    private static final LocalDate REVIEWED = LocalDate.of(2026, 9, 29);
    private static final Currency USD = Currency.getInstance("USD");
    private static final OfferComparison.Window WINDOW = new OfferComparison.Window(REVIEWED, 30, 14);
    private static final OfferComparison.Offer ANCHOR = offer("B:401", "same-building", "exclusive-v1",
            READY_RESALE, "45", "99000", USD, REVIEWED.minusDays(14), null);

    private static OfferComparison.Offer offer(String unit, String cohort, String basis,
            OfferComparison.Stage stage, String area, String ask, Currency currency, LocalDate quote, LocalDate expiry) {
        return new OfferComparison.Offer(unit, cohort, stage, basis, decimal(area), decimal(ask),
                currency, quote, expiry);
    }

    private static BigDecimal decimal(String v) { return v == null ? null : new BigDecimal(v); }

    private static OfferComparison.Offer comparable(String basis, LocalDate quote, LocalDate expires) {
        return offer("B:502", "same-building", basis, READY_RESALE, "42", "88200", USD, quote, expires);
    }

    private static void require(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    private static void reject(OfferComparison.Offer offer, OfferComparison.Reason... reasons) {
        var review = OfferComparison.review(ANCHOR, offer, WINDOW);
        require(!review.eligible() && review.prices().isEmpty(), "rejected offers must have no calculated rate");
        require(review.reasons().equals(Set.of(reasons)), "unexpected reasons: " + review.reasons());
    }

    public static void main(String[] args) {
        var good = OfferComparison.review(ANCHOR, comparable("exclusive-v1", REVIEWED, REVIEWED), WINDOW);
        var p = good.prices().orElseThrow();
        require(good.eligible() && p.leftPerSquareMetre().equals(decimal("2200.00"))
              && p.rightPerSquareMetre().equals(decimal("2100.00")), "comparable price per square metre");
        require(p.rightMinusLeftTotal().equals(decimal("-10800"))
              && p.rightMinusLeftPerSquareMetre().equals(decimal("-100.00")), "signed price differences");
        reject(comparable("gross-v1", REVIEWED, null), AREA_BASIS_MISMATCH);
        reject(comparable(null, REVIEWED, null), AREA_BASIS_UNCONFIRMED);
        reject(comparable("exclusive-v1", null, null), QUOTE_DATE_UNCONFIRMED);
        reject(comparable("exclusive-v1", REVIEWED.minusDays(40), null), STALE_QUOTE, QUOTE_DATES_TOO_FAR_APART);
        reject(comparable("exclusive-v1", REVIEWED.plusDays(1), null), FUTURE_QUOTE, QUOTE_DATES_TOO_FAR_APART);
        reject(comparable("exclusive-v1", REVIEWED.minusDays(4), REVIEWED.minusDays(1)), EXPIRED_QUOTE);
        reject(comparable("exclusive-v1", REVIEWED.minusDays(4), REVIEWED.minusDays(5)), INVALID_EXPIRY, EXPIRED_QUOTE);
        reject(offer("B:401","same-building","exclusive-v1",READY_RESALE,"45","99000",USD,REVIEWED,null), SAME_UNIT);
        reject(offer("B:502","other-building","exclusive-v1",OFF_PLAN,"0","-1",Currency.getInstance("THB"),REVIEWED,null),
                DIFFERENT_COHORT, DIFFERENT_STAGE, INVALID_AREA, INVALID_ASK, CURRENCY_MISMATCH);
        reject(offer(null,null,"",null,"40","11000",null,REVIEWED,null),
                UNIT_ID_UNCONFIRMED, COHORT_UNCONFIRMED, AREA_BASIS_UNCONFIRMED, STAGE_UNCONFIRMED, CURRENCY_UNCONFIRMED);
        System.out.println("12 documented pricing and boundary checks passed.");
    }
}
