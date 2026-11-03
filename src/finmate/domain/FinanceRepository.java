package finmate.domain;

import java.io.IOException;

/**
 * Storage contract used by the application service.
 * A failed save must preserve the previously stored contents.
 */
public interface FinanceRepository {
    /**
     * Loads the stored snapshot.
     * @return loaded data, or repository-defined initial data when no stored file exists
     * @throws IOException if data cannot be read or decoded
     */
    FinanceData load() throws IOException;
    /**
     * Persists a snapshot. The service validates the snapshot before invoking this method.
     * @param data snapshot to persist
     * @throws IOException if saving fails; previously stored contents must remain intact
     */
    void save(FinanceData data) throws IOException;
}
