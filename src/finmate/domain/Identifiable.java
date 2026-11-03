package finmate.domain;

import java.util.UUID;

/**
 * Provides the stable UUID used to identify a domain entity.
 */
public interface Identifiable {
    /**
     * Returns the stable identifier.
     * @return the entity UUID
     */
    UUID id();
}
