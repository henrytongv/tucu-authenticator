package org.umilab.tucu.auth.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory decrypted vault. Entries keyed by UUID for replace-on-import.
 * Must be wipe()d when the app locks or exits.
 */
public final class Vault {

    private final Map<String, VaultEntry> entries = new LinkedHashMap<>();

    public List<VaultEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public VaultEntry get(String uuid) {
        return entries.get(uuid);
    }

    /** Replaces an existing entry with the same UUID, or appends. */
    public void put(VaultEntry entry) {
        VaultEntry old = entries.put(entry.getUuid(), entry);
        if (old != null) {
            old.wipe();
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void wipe() {
        for (VaultEntry e : entries.values()) {
            e.wipe();
        }
        entries.clear();
    }
}
