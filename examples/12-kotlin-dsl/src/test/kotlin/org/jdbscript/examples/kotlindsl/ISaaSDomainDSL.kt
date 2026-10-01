package org.jdbscript.examples.kotlindsl

import java.math.BigDecimal

data class MemberSpec(
    val id: Long,
    val email: String,
    val role: String = "MEMBER",
    val status: String = "ACTIVE"
)

data class UsageSpec(
    val id: Long,
    val userId: Long,
    val eventType: String,
    val credits: Int,
    val recordedAt: String = "2026-10-01 12:00:00"
)

data class InvoiceSpec(
    val id: Long,
    val amount: BigDecimal,
    val status: String = "UNPAID",
    val dueDate: String = "2026-11-15"
)

/**
 * Domain-specific fixture mixin extending [ISaaSSchema].
 *
 * Demonstrates how Kotlin default arguments, data classes, and varargs
 * create expressive domain scenarios with zero boilerplate.
 */
interface ISaaSDomainDSL : ISaaSSchema {

    fun createWorkspaceWithSubscription(
        orgId: Long,
        name: String,
        plan: SubscriptionPlan = SubscriptionPlan.PRO,
        quotaOverride: Int? = null,
        members: List<MemberSpec> = emptyList()
    ) {
        val slug = name.lowercase().replace(" ", "-")
        organizations().id(orgId).slug(slug).name(name).tier(plan.name)

        val quota = quotaOverride ?: plan.defaultMonthlyQuota
        subscriptions().id(orgId * 100).org_id(orgId).plan(plan.name).monthly_quota_credits(quota)

        members.forEach { member ->
            users().id(member.id).org_id(orgId).email(member.email).role(member.role).status(member.status)
        }
    }

    fun seedUsageEvents(orgId: Long, vararg events: UsageSpec) {
        events.forEach { event ->
            usage_events().id(event.id)
                .org_id(orgId)
                .user_id(event.userId)
                .event_type(event.eventType)
                .credits_consumed(event.credits)
                .recorded_at(event.recordedAt)
        }
    }

    fun seedInvoices(orgId: Long, vararg invoices: InvoiceSpec) {
        invoices.forEach { inv ->
            invoices().id(inv.id)
                .org_id(orgId)
                .amount_due(inv.amount)
                .status(inv.status)
                .due_date(inv.dueDate)
        }
    }
}
