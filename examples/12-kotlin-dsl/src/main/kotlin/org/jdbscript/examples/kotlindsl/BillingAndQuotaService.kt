package org.jdbscript.examples.kotlindsl

import java.math.BigDecimal
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * Business service managing multi-tenant SaaS quota tracking, health inspection, and billing.
 *
 * Implemented in clean, modular Kotlin to serve as a realistic system-under-test for test fixture examples.
 */
class BillingAndQuotaService(private val dataSource: DataSource) {

    fun recordUsage(orgId: Long, userId: Long, eventType: String, credits: Int): RecordedUsage =
        dataSource.connection.use { conn ->
            conn.requireActiveOrganization(orgId)

            val subscription = conn.requireActiveSubscription(orgId)
            val currentConsumed = conn.fetchTotalCreditsConsumed(orgId)

            if (currentConsumed + credits > subscription.monthlyQuotaCredits) {
                throw QuotaExceededException(
                    "Quota exceeded for org $orgId: consumed $currentConsumed + requested $credits > quota ${subscription.monthlyQuotaCredits}"
                )
            }

            val nextEventId = conn.fetchNextUsageEventId()
            val recordedAt = LocalDateTime.now()

            conn.insertUsageEvent(nextEventId, orgId, userId, eventType, credits, recordedAt)

            RecordedUsage(
                eventId = nextEventId,
                organizationId = orgId,
                userId = userId,
                eventType = eventType,
                creditsConsumed = credits,
                recordedAt = recordedAt
            )
        }

    fun calculateWorkspaceHealth(orgId: Long): WorkspaceHealth =
        dataSource.connection.use { conn ->
            val org = conn.requireOrganization(orgId)
            val subscription = conn.findSubscription(orgId) ?: SubInfo(plan = "NONE", monthlyQuotaCredits = 0, status = "NONE")
            val activeMembers = conn.countActiveMembers(orgId)
            val totalCredits = conn.fetchTotalCreditsConsumed(orgId)
            val unpaidInvoices = conn.countUnpaidInvoices(orgId)

            val remainingCredits = (subscription.monthlyQuotaCredits - totalCredits).coerceAtLeast(0)
            val isQuotaExceeded = totalCredits > subscription.monthlyQuotaCredits || !org.active

            WorkspaceHealth(
                organizationId = orgId,
                organizationName = org.name,
                plan = subscription.plan,
                activeMemberCount = activeMembers,
                totalCreditsConsumed = totalCredits,
                remainingCredits = remainingCredits,
                isQuotaExceeded = isQuotaExceeded,
                unpaidInvoiceCount = unpaidInvoices
            )
        }

    fun generateMonthlyInvoice(orgId: Long, invoiceId: Long, dueDate: LocalDate): InvoiceSummary =
        dataSource.connection.use { conn ->
            val planName = conn.findSubscription(orgId)?.plan ?: "PRO"
            val basePlan = runCatching { SubscriptionPlan.valueOf(planName) }.getOrDefault(SubscriptionPlan.PRO)
            val baseFee = basePlan.monthlyBaseFee

            conn.insertInvoice(invoiceId, orgId, baseFee, dueDate)

            InvoiceSummary(
                invoiceId = invoiceId,
                organizationId = orgId,
                amountDue = baseFee,
                status = "UNPAID",
                dueDate = dueDate
            )
        }

    // =========================================================================
    // Private Domain Queries & Operations
    // =========================================================================

    private fun Connection.requireOrganization(orgId: Long): OrgInfo =
        queryOne("SELECT name, active FROM organizations WHERE id = ?", orgId) { rs ->
            OrgInfo(name = rs.getString("name"), active = rs.getBoolean("active"))
        } ?: throw IllegalArgumentException("Organization $orgId does not exist")

    private fun Connection.requireActiveOrganization(orgId: Long) {
        val org = requireOrganization(orgId)
        if (!org.active) {
            throw InactiveWorkspaceException("Organization $orgId is inactive")
        }
    }

    private fun Connection.findSubscription(orgId: Long): SubInfo? =
        queryOne("SELECT plan, monthly_quota_credits, status FROM subscriptions WHERE org_id = ?", orgId) { rs ->
            SubInfo(
                plan = rs.getString("plan"),
                monthlyQuotaCredits = rs.getInt("monthly_quota_credits"),
                status = rs.getString("status")
            )
        }

    private fun Connection.requireActiveSubscription(orgId: Long): SubInfo {
        val sub = findSubscription(orgId) ?: throw IllegalStateException("No subscription found for organization $orgId")
        if (sub.status != "ACTIVE") {
            throw InactiveWorkspaceException("Subscription for $orgId is ${sub.status}")
        }
        return sub
    }

    private fun Connection.fetchTotalCreditsConsumed(orgId: Long): Int =
        queryScalar("SELECT COALESCE(SUM(credits_consumed), 0) FROM usage_events WHERE org_id = ?", orgId) { it.getInt(1) } ?: 0

    private fun Connection.countActiveMembers(orgId: Long): Int =
        queryScalar("SELECT COUNT(*) FROM users WHERE org_id = ? AND status = 'ACTIVE'", orgId) { it.getInt(1) } ?: 0

    private fun Connection.countUnpaidInvoices(orgId: Long): Int =
        queryScalar("SELECT COUNT(*) FROM invoices WHERE org_id = ? AND status = 'UNPAID'", orgId) { it.getInt(1) } ?: 0

    private fun Connection.fetchNextUsageEventId(): Long =
        queryScalar("SELECT COALESCE(MAX(id), 0) + 1 FROM usage_events") { it.getLong(1) } ?: 1L

    private fun Connection.insertUsageEvent(
        id: Long,
        orgId: Long,
        userId: Long,
        eventType: String,
        credits: Int,
        recordedAt: LocalDateTime
    ) {
        execute(
            "INSERT INTO usage_events (id, org_id, user_id, event_type, credits_consumed, recorded_at) VALUES (?, ?, ?, ?, ?, ?)",
            id, orgId, userId, eventType, credits, Timestamp.valueOf(recordedAt)
        )
    }

    private fun Connection.insertInvoice(id: Long, orgId: Long, amount: BigDecimal, dueDate: LocalDate) {
        execute(
            "INSERT INTO invoices (id, org_id, amount_due, status, due_date) VALUES (?, ?, ?, 'UNPAID', ?)",
            id, orgId, amount, Date.valueOf(dueDate)
        )
    }

    // =========================================================================
    // Lightweight JDBC Helpers
    // =========================================================================

    private fun <T> Connection.queryOne(sql: String, vararg params: Any, mapper: (ResultSet) -> T): T? =
        prepareStatement(sql).use { ps ->
            params.forEachIndexed { idx, p -> ps.setObject(idx + 1, p) }
            ps.executeQuery().use { rs ->
                if (rs.next()) mapper(rs) else null
            }
        }

    private fun <T> Connection.queryScalar(sql: String, vararg params: Any, mapper: (ResultSet) -> T): T? =
        prepareStatement(sql).use { ps ->
            params.forEachIndexed { idx, p -> ps.setObject(idx + 1, p) }
            ps.executeQuery().use { rs ->
                if (rs.next()) mapper(rs) else null
            }
        }

    private fun Connection.execute(sql: String, vararg params: Any) {
        prepareStatement(sql).use { ps ->
            params.forEachIndexed { idx, p -> ps.setObject(idx + 1, p) }
            ps.executeUpdate()
        }
    }

    private data class OrgInfo(val name: String, val active: Boolean)
    private data class SubInfo(val plan: String, val monthlyQuotaCredits: Int, val status: String)
}
