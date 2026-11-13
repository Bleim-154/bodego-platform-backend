package com.bodego.platform.iam.infrastructure.security.login;

import com.bodego.platform.iam.domain.model.valueobjects.EmailAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded, atomic fixed-window admission before credential lookup/BCrypt. Process-local only. */
public final class LoginRateLimiter {
    private record Key(boolean account, String digest) {}
    private static final class Bucket {
        final long started;
        int attempts;
        Bucket(long started) { this.started=started; }
    }
    private final LoginRateLimitPolicy policy;
    private final LongSupplier nanoTime;
    // Insertion order is also expiry order: denied attempts never renew an entry.
    private final LinkedHashMap<Key, Bucket> buckets=new LinkedHashMap<>();
    private Bucket total;
    private int inFlight;

    public LoginRateLimiter(LoginRateLimitPolicy policy, LongSupplier nanoTime) {
        this.policy=Objects.requireNonNull(policy); this.nanoTime=Objects.requireNonNull(nanoTime);
    }

    public synchronized Permit acquire(String socketPeer, EmailAddress email) {
        long now=nanoTime.getAsLong(), duration=policy.window().toNanos();
        if(total==null || now-total.started>=duration) total=new Bucket(now);
        if(inFlight>=policy.maxConcurrent() || total.attempts>=policy.totalAttempts()) throw limited();
        var iterator=buckets.values().iterator();
        while(iterator.hasNext()) {
            if(now-iterator.next().started<duration) break;
            iterator.remove();
        }
        // Only caller-supplied *socket* metadata, never HTTP forwarding headers; null peers share a bucket.
        Key origin=new Key(false,digest(socketPeer==null || socketPeer.isBlank()?"unknown":socketPeer));
        Key account=new Key(true,digest(email.value()));
        Bucket byOrigin=buckets.get(origin), byAccount=buckets.get(account);
        if((byOrigin!=null && byOrigin.attempts>=policy.originAttempts())
                || (byAccount!=null && byAccount.attempts>=policy.accountAttempts())
                || buckets.size()+(byOrigin==null?1:0)+(byAccount==null?1:0)>policy.maxKeys()) throw limited();
        if(byOrigin==null) { byOrigin=new Bucket(now); buckets.put(origin,byOrigin); }
        if(byAccount==null) { byAccount=new Bucket(now); buckets.put(account,byAccount); }
        byOrigin.attempts++; byAccount.attempts++; total.attempts++; inFlight++;
        return new Permit();
    }

    private LoginRateLimitExceededException limited() {
        // Same conservative delay for every reason; no account existence or quota details.
        return new LoginRateLimitExceededException(policy.window().getSeconds()+(policy.window().getNano()>0?1:0));
    }
    private static String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("LOGIN_RATE_LIMIT_UNAVAILABLE"); }
    }

    public final class Permit implements AutoCloseable {
        private boolean closed;
        private Permit() {}
        @Override public void close() {
            synchronized(LoginRateLimiter.this) {
                if(!closed) { inFlight--; closed=true; }
            }
        }
    }
}
