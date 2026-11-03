package finmate.domain;

import java.util.UUID;

/**
 * A named category referenced by transactions and optional category budgets.
 * References use the UUID so renaming a category preserves its relationships.
 */
public final class Category extends NamedEntity {
    /**
     * Creates a category; aggregate validation is performed before service persistence.
     * @param id stable category identifier
     * @param name category display name
     */
    public Category(UUID id, String name) { super(id, name); }
}
