package com.winhub.hernandez.store.xbox

import java.io.Closeable
import java.util.UUID

/** Launch leases (ported from DroidDeck, GPL-3.0): one live launch per game, bound to account and session, expiring without renewal. */
internal class XboxLaunchLeases(private val now: () -> Long) {
    private data class Lease(val id: String, val account: String, val session: String, var expires: Long, var child: Closeable? = null)
    private val leases = HashMap<String, Lease>()

    @Synchronized fun active(id: String) = leases.values.any { it.id == id }
    @Synchronized fun begin(id: String, account: String, session: String): String {
        check(!active(id)) { "This Xbox game is already starting or running." }
        val token = UUID.randomUUID().toString()
        leases[token] = Lease(id, account, session, now() + 180_000)
        return token
    }
    @Synchronized fun attach(token: String, child: Closeable): Boolean {
        val lease = leases[token] ?: return false
        lease.child = child
        return true
    }
    @Synchronized fun renew(token: String, id: String, account: String?, session: String?): Boolean {
        val lease = leases[token] ?: return false
        if (lease.id != id || lease.account != account || lease.session != session || lease.expires <= now()) return false
        lease.expires = now() + 60_000
        return true
    }
    @Synchronized fun release(token: String): Closeable? = leases.remove(token)?.child
    @Synchronized fun stale(account: String?, session: String?, suspended: Boolean): List<Closeable> {
        if (suspended) leases.values.filter { it.account == account && it.session == session }
            .forEach { it.expires = maxOf(it.expires, now() + 60_000) }
        val expired = leases.filterValues { it.account != account || it.session != session || (!suspended && it.expires <= now()) }.keys
        return expired.mapNotNull { leases.remove(it)?.child }
    }
    @Synchronized fun clear(): List<Closeable> {
        return leases.values.mapNotNull { it.child }.also { leases.clear() }
    }
}
