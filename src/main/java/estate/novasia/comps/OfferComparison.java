package estate.novasia.comps;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Compares two identifiable condominium asks only when they share documented price and area boundaries.
 *
 * <p>Pass a district or building-specific cohort key supplied by the caller. The reviewer leaves
 * cross-currency, stale, ambiguous and mismatched offers open. Asking prices are compared on the
 * named measurement basis. The output does not estimate a sale price or attribute a premium.</p>
 *
 * <p>For the source rationale see the
 * <a href="https://novasia.estate/en/cambodia/property-insights/phnom-penh-condo-asking-prices/">
 * NovAsia Estate guide to comparable condo asks</a>.</p>
 */
public final class OfferComparison {
    private OfferComparison() {}

    /** A price and area are accepted only when both rows use the same documented convention. */
    public record Offer(
            String unitKey,
            String cohortKey,
            Stage stage,
            String areaDefinition,
            BigDecimal areaSquareMetres,
            BigDecimal askingPrice,
            Currency currency,
            LocalDate quoteDate,
            LocalDate expires
    ) {}

    /** Keep offers at different delivery stages in separate cohorts. */
    public enum Stage { READY_RESALE, READY_DEVELOPER, OFF_PLAN }

    /** Age and quote gap are inclusive, measured in local calendar days. */
    public record Window(LocalDate asOf, long maxAgeDays, long maxGapDays) {
        /** Validates the selected calendar bounds. */
        public Window {
            Objects.requireNonNull(asOf, "asOf");
            if (maxAgeDays < 0 || maxGapDays < 0) {
                throw new IllegalArgumentException("Comparison windows must be nonnegative.");
            }
        }
    }

    /** Reasons to preserve alongside an offer that cannot enter the comparison. */
    public enum Reason {
        UNIT_ID_UNCONFIRMED, SAME_UNIT, COHORT_UNCONFIRMED, DIFFERENT_COHORT,
        STAGE_UNCONFIRMED, DIFFERENT_STAGE,
        AREA_BASIS_UNCONFIRMED, AREA_BASIS_MISMATCH, INVALID_AREA, INVALID_ASK,
        CURRENCY_UNCONFIRMED, CURRENCY_MISMATCH, QUOTE_DATE_UNCONFIRMED,
        FUTURE_QUOTE, STALE_QUOTE, QUOTE_DATES_TOO_FAR_APART, INVALID_EXPIRY, EXPIRED_QUOTE
    }

    /** Amounts are denominated in both quotes' common currency. */
    public record Prices(BigDecimal leftPerSquareMetre, BigDecimal rightPerSquareMetre,
                         BigDecimal rightMinusLeftTotal, BigDecimal rightMinusLeftPerSquareMetre) {}

    /** Public result retains rejection reasons. Amounts exist only for an eligible pair. */
    public record Review(Set<Reason> reasons, Optional<Prices> prices) {
        /** Keeps the review immutable and enforces the eligible-result invariant. */
        public Review {
            reasons = Set.copyOf(reasons);
            Objects.requireNonNull(prices, "prices");
            if (prices.isPresent() != reasons.isEmpty()) {
                throw new IllegalArgumentException("Amounts require an eligible pair.");
            }
        }

        /** True when no evidence requirement blocks the pair. */
        public boolean eligible() { return reasons.isEmpty(); }
    }

    /**
     * Reviews both offers and returns all known blockers rather than stopping at the first one.
     * Empty metadata in an offer represents a field that still needs evidence.
     *
     * @param left first offer
     * @param right second offer
     * @param window review date and permitted quote age and gap
     * @return a review and, for eligible quotes, consistently denominated prices
     */
    public static Review review(Offer left, Offer right, Window window) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(window, "window");
        var reasons = EnumSet.noneOf(Reason.class);
        if (blank(left.unitKey()) || blank(right.unitKey())) reasons.add(Reason.UNIT_ID_UNCONFIRMED);
        else if (left.unitKey().equals(right.unitKey())) reasons.add(Reason.SAME_UNIT);
        compareBoundary(left.cohortKey(), right.cohortKey(),
                Reason.COHORT_UNCONFIRMED, Reason.DIFFERENT_COHORT, reasons);
        compareBoundary(left.stage(), right.stage(),
                Reason.STAGE_UNCONFIRMED, Reason.DIFFERENT_STAGE, reasons);
        compareBoundary(left.areaDefinition(), right.areaDefinition(),
                Reason.AREA_BASIS_UNCONFIRMED, Reason.AREA_BASIS_MISMATCH, reasons);
        compareBoundary(left.currency(), right.currency(),
                Reason.CURRENCY_UNCONFIRMED, Reason.CURRENCY_MISMATCH, reasons);
        if (!positive(left.areaSquareMetres()) || !positive(right.areaSquareMetres())) reasons.add(Reason.INVALID_AREA);
        if (!positive(left.askingPrice()) || !positive(right.askingPrice())) reasons.add(Reason.INVALID_ASK);
        checkDate(left, window, reasons);
        checkDate(right, window, reasons);
        if (left.quoteDate() != null && right.quoteDate() != null
                && Math.abs(ChronoUnit.DAYS.between(left.quoteDate(), right.quoteDate())) > window.maxGapDays()) {
            reasons.add(Reason.QUOTE_DATES_TOO_FAR_APART);
        }
        if (!reasons.isEmpty()) return new Review(reasons, Optional.empty());
        BigDecimal leftRate = left.askingPrice().divide(left.areaSquareMetres(), 2, RoundingMode.HALF_UP);
        BigDecimal rightRate = right.askingPrice().divide(right.areaSquareMetres(), 2, RoundingMode.HALF_UP);
        return new Review(reasons, Optional.of(new Prices(leftRate, rightRate,
                right.askingPrice().subtract(left.askingPrice()), rightRate.subtract(leftRate))));
    }

    private static void checkDate(Offer offer, Window w, EnumSet<Reason> reasons) {
        if (offer.quoteDate() == null) reasons.add(Reason.QUOTE_DATE_UNCONFIRMED);
        else {
            long age = ChronoUnit.DAYS.between(offer.quoteDate(), w.asOf());
            if (age < 0) reasons.add(Reason.FUTURE_QUOTE);
            else if (age > w.maxAgeDays()) reasons.add(Reason.STALE_QUOTE);
        }
        if (offer.expires() != null) {
            if (offer.quoteDate() != null && offer.expires().isBefore(offer.quoteDate())) reasons.add(Reason.INVALID_EXPIRY);
            if (offer.expires().isBefore(w.asOf())) reasons.add(Reason.EXPIRED_QUOTE);
        }
    }

    private static boolean blank(Object value) {
        return value == null || value instanceof String s && s.isBlank();
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static void compareBoundary(Object a, Object b, Reason missing, Reason mismatch, EnumSet<Reason> out) {
        if (blank(a) || blank(b)) out.add(missing);
        else if (!a.equals(b)) out.add(mismatch);
    }
}
