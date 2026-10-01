package org.jdbscript.examples.kotlindsl

import org.jdbscript.IDBSchema
import org.jdbscript.IDBSchema.IDBRecord
import org.jdbscript.RecordTools
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Type-safe schema interface for the SaaS billing and quota domain.
 *
 * Setter parameters use nullable types (`Long?`, `String?`, etc.) to support
 * optional columns, partial updates via `updateDB`, and seamless Java interop.
 *
 * Each record interface defines a `defaults(tools: RecordTools)` method to automatically
 * satisfy database constraints (e.g. `NOT NULL created_at`, audit fields, default statuses)
 * so individual tests only need to specify columns relevant to their scenario.
 */
interface ISaaSSchema : IDBSchema {
    fun organizations(): IOrganizationRecord
    fun users(): IUserRecord
    fun subscriptions(): ISubscriptionRecord
    fun usage_events(): IUsageEventRecord
    fun invoices(): IInvoiceRecord

    interface IOrganizationRecord : IDBRecord {
        fun id(id: Long?): IOrganizationRecord
        fun slug(slug: String?): IOrganizationRecord
        fun name(name: String?): IOrganizationRecord
        fun tier(tier: String?): IOrganizationRecord
        fun active(active: Boolean?): IOrganizationRecord
        fun created_at(createdAt: LocalDateTime?): IOrganizationRecord
        fun created_at(createdAt: String?): IOrganizationRecord

        fun defaults(tools: RecordTools) {
            slug(tools.strValue("org-\${id}"))
            tier("PRO")
            active(true)
            created_at("2026-01-01 00:00:00")
        }
    }

    interface IUserRecord : IDBRecord {
        fun id(id: Long?): IUserRecord
        fun org_id(orgId: Long?): IUserRecord
        fun email(email: String?): IUserRecord
        fun role(role: String?): IUserRecord
        fun status(status: String?): IUserRecord

        fun defaults(tools: RecordTools) {
            email(tools.strValue("user\${id}@example.com"))
            role("MEMBER")
            status("ACTIVE")
        }
    }

    interface ISubscriptionRecord : IDBRecord {
        fun id(id: Long?): ISubscriptionRecord
        fun org_id(orgId: Long?): ISubscriptionRecord
        fun plan(plan: String?): ISubscriptionRecord
        fun monthly_quota_credits(credits: Int?): ISubscriptionRecord
        fun status(status: String?): ISubscriptionRecord
        fun renews_on(renewsOn: LocalDate?): ISubscriptionRecord
        fun renews_on(renewsOn: String?): ISubscriptionRecord

        fun defaults(tools: RecordTools) {
            plan("PRO")
            monthly_quota_credits(10_000)
            status("ACTIVE")
            renews_on("2026-11-01")
        }
    }

    interface IUsageEventRecord : IDBRecord {
        fun id(id: Long?): IUsageEventRecord
        fun org_id(orgId: Long?): IUsageEventRecord
        fun user_id(userId: Long?): IUsageEventRecord
        fun event_type(eventType: String?): IUsageEventRecord
        fun credits_consumed(credits: Int?): IUsageEventRecord
        fun recorded_at(recordedAt: LocalDateTime?): IUsageEventRecord
        fun recorded_at(recordedAt: String?): IUsageEventRecord

        fun defaults(tools: RecordTools) {
            event_type("API_CALL")
            credits_consumed(100)
            recorded_at("2026-10-01 12:00:00")
        }
    }

    interface IInvoiceRecord : IDBRecord {
        fun id(id: Long?): IInvoiceRecord
        fun org_id(orgId: Long?): IInvoiceRecord
        fun amount_due(amountDue: BigDecimal?): IInvoiceRecord
        fun amount_due(amountDue: Double?): IInvoiceRecord
        fun status(status: String?): IInvoiceRecord
        fun due_date(dueDate: LocalDate?): IInvoiceRecord
        fun due_date(dueDate: String?): IInvoiceRecord

        fun defaults(tools: RecordTools) {
            status("UNPAID")
            due_date("2026-11-15")
        }
    }
}
