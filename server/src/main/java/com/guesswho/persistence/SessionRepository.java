package com.guesswho.persistence;

import com.guesswho.account.Account;
import java.time.Instant;
import java.util.Optional;

/**
 * Keeps track of who is logged in.
 *
 * <p>Stores the hash of each token rather than the token. A stolen database
 * then yields nothing usable, which is the same reason passwords are hashed —
 * except that a random token has nothing to guess at, so the hash does not need
 * to be slow.</p>
 */
public interface SessionRepository {
    /**
     * Records a new session.
     *
     * @param accountId whose session it is
     * @param tokenHash hash of the token handed to them; never the token itself
     * @param expiresAt when it stops working
     */
    void create(long accountId, String tokenHash, Instant expiresAt);

    /**
     * Finds whoever a token belongs to, if it is still good.
     *
     * @param tokenHash hash of the token presented on a request
     * @param now       the moment to judge expiry against
     * @return the account, or empty when the token is unknown or expired
     */
    Optional<Account> findAccount(String tokenHash, Instant now);

    /**
     * Ends one session, on logout.
     *
     * @param tokenHash hash of the token being given up
     */
    void delete(String tokenHash);

    /**
     * Ends every session belonging to one account.
     *
     * <p>What a password change should do, and what somebody who thinks they
     * have been compromised needs.</p>
     *
     * @param accountId whose sessions to end
     * @return how many were ended
     */
    int deleteAllFor(long accountId);

    /**
     * Removes sessions that have expired.
     *
     * <p>Expired sessions already fail to authenticate; this only stops the
     * table growing for ever.</p>
     *
     * @param now the moment to judge expiry against
     * @return how many were removed
     */
    int deleteExpired(Instant now);
}
