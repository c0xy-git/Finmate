package finmate.domain;

import java.util.UUID;

/**
 * Immutable base class for entities that share an identifier and a name.
 * Constructors store the supplied values; {@link FinanceData#validate()} enforces data rules.
 */
public abstract class NamedEntity implements Identifiable {
    private final UUID id;
    private final String name;

    /**
     * Creates a named entity without performing aggregate validation.
     * @param id stable entity identifier
     * @param name display name
     */
    protected NamedEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Returns this entity identifier.
     * @return stable UUID
     */
    @Override public final UUID id() { return id; }
    /**
     * Returns this entity name.
     * @return display name
     */
    public final String name() { return name; }
    /**
     * Returns the name for display in Swing controls.
     * @return display name
     */
    @Override public final String toString() { return name; }
}
