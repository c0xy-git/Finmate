package finmate.domain;

import java.util.UUID;

/**
 * An immutable savings target with a manually entered saved amount.
 * Savings amounts are independent of transaction balances.
 */
public final class SavingsGoal extends NamedEntity {
    private final long target;
    private final long saved;

    /**
     * Creates a savings goal without performing aggregate validation.
     * @param id stable goal identifier
     * @param name goal name
     * @param target target amount in whole KRW; validated as positive before service persistence
     * @param saved saved amount in whole KRW; validated as nonnegative before service persistence
     */
    public SavingsGoal(UUID id, String name, long target, long saved) {
        super(id, name);
        this.target = target;
        this.saved = saved;
    }

    /**
     * Returns the target amount.
     * @return target amount in whole KRW
     */
    public long target() { return target; }
    /**
     * Returns the manually recorded savings.
     * @return saved amount in whole KRW
     */
    public long saved() { return saved; }
    /**
     * Calculates saved amount divided by target, multiplied by 100.
     * The result may exceed 100; this method assumes a validated positive target.
     * @return progress percentage
     */
    public double progress() { return saved * 100.0 / target; }
    /**
     * Checks whether recorded savings meet or exceed the target.
     * @return true when saved is at least target
     */
    public boolean achieved() { return saved >= target; }
}
