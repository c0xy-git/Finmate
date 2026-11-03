package finmate.domain;

import java.time.YearMonth;
import java.util.UUID;

/**
 * An immutable monthly spending limit.
 * A null category identifies the overall monthly budget. A month and category pair
 * is unique within validated finance data; overall and category limits are independent.
 * @param month budget month
 * @param categoryId category UUID, or null for the overall monthly budget
 * @param amount positive limit in whole KRW
 */
public record Budget(YearMonth month, UUID categoryId, long amount) { }
