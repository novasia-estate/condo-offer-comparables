# Condo Offer Comparables

A Java 17 library for comparing dated asking prices for two condominium units. Use one explicit definition of area, one buyer-defined cohort and the same currency and sale stage. Enter dates and unit IDs from verified evidence. If an offer is incomplete, the library returns all identifiable blockers and omits the price-per-square-metre result.

The worked case compares USD 99,000 for 45 m² with USD 88,200 for 42 m² on the same exclusive-area basis. The rates are USD 2,200 and USD 2,100 per m². Changing the second basis to gross area leaves neither rate eligible for the pair. The figures are sample asks and cannot value a real unit. [NovAsia Estate's comparison method](https://novasia.estate/en/cambodia/property-insights/phnom-penh-condo-asking-prices/).

## Usage

```java
import static estate.novasia.comps.OfferComparison.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

var day = LocalDate.of(2026, 9, 29);
var usd = Currency.getInstance("USD");
var a = new Offer("B:401", "building-B", Stage.READY_RESALE, "exclusive-v1",
    new BigDecimal("45"), new BigDecimal("99000"), usd, day.minusDays(14), null);
var b = new Offer("B:502", "building-B", Stage.READY_RESALE, "exclusive-v1",
    new BigDecimal("42"), new BigDecimal("88200"), usd, day, day);
var outcome = review(a, b, new Window(day, 30, 14));
System.out.println(outcome.prices().orElseThrow());
```

Canonical unit and cohort keys need to come from your own document review. No similarity rule merges two listings. An omitted expiry is recorded as unknown to the caller. A supplied expiry older than the review date rejects the quote. The per-square-metre amounts are rounded to two decimals before their difference is shown.

## Build

Run `mvn verify` with Java 17 or newer. The verification exercises inclusive date bounds, quote expiry, multiple blockers on one offer, incompatible area bases and correct asking price calculations. No runtime dependencies.

Add `https://jitpack.io` to your repositories. The Maven coordinates are `com.github.novasia-estate:condo-offer-comparables:1.0.0`.
