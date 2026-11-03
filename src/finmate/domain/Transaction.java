package finmate.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An immutable income or expense entry, linked to a category by UUID.
 * Monetary and reference rules are checked by {@link FinanceData#validate()}.
 * @param id stable transaction UUID
 * @param date transaction date
 * @param type income or expense
 * @param categoryId UUID of an existing category
 * @param amount positive amount in whole KRW
 * @param payment optional payment description, stored as a non-null string
 * @param note optional note, stored as a non-null string
 */
public record Transaction(UUID id, LocalDate date, TransactionType type, UUID categoryId,
                          long amount, String payment, String note) implements Identifiable { }
