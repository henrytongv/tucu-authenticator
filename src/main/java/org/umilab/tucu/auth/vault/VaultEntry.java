package org.umilab.tucu.auth.vault;

import java.util.Arrays;

/**
 * A single TOTP entry. The secret is stored raw as bytes so it can be
 * wiped from memory on lock/exit.
 */
public final class VaultEntry {

    private final String uuid;
    private final String name;
    private final String issuer;
    private final byte[] secret; // decoded base32
    private final int period;    // seconds, default 30
    private boolean favorite;

    public VaultEntry(String uuid, String name, String issuer, byte[] secret, int period) {
        this(uuid, name, issuer, secret, period, false);
    }

    public VaultEntry(String uuid, String name, String issuer, byte[] secret, int period, boolean favorite) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        this.issuer = issuer == null ? "" : issuer;
        this.secret = secret;
        this.period = period > 0 ? period : 30;
        this.favorite = favorite;
    }

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public String getIssuer() { return issuer; }
    public byte[] getSecret() { return secret; }
    public int getPeriod() { return period; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public void wipe() {
        Arrays.fill(secret, (byte) 0);
    }
}
