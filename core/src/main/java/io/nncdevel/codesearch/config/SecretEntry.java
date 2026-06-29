package io.nncdevel.codesearch.config;

/**
 * One {@code secrets.<id>} entry (spec §7.2).
 *
 * @param authType the authentication type
 * @param token present when {@code authType == TOKEN}; otherwise {@code null}
 * @param sshKeyPath present when {@code authType == SSH_KEY}; otherwise {@code null}
 */
public record SecretEntry(AuthType authType, String token, String sshKeyPath) {

    /**
     * Returns an entry representing anonymous access.
     *
     * @return a {@link AuthType#NONE} entry with no credentials
     */
    public static SecretEntry none() {
        return new SecretEntry(AuthType.NONE, null, null);
    }
}
