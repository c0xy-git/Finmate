package finmate.domain;

/**
 * Distinguishes income and expense while providing Korean display labels.
 */
public enum TransactionType {
    /** Money received. */
    INCOME("수입"),
    /** Money spent. */
    EXPENSE("지출");
    private final String label;
    TransactionType(String label) { this.label = label; }
    /**
     * Returns the label used by the current Korean user interface.
     * @return display label
     */
    @Override public String toString() { return label; }
}
