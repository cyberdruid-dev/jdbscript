package org.jdbscript.examples.kotlindsl

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class SubscriptionPlan(val defaultMonthlyQuota: Int, val monthlyBaseFee: BigDecimal) {
    FREE(1_000, BigDecimal("0.00")),
    PRO(10_000, BigDecimal("49.00")),
    ENTERPRISE(100_000, BigDecimal("299.00"))
}

data class WorkspaceHealth(
    val organizationId: Long,
    val organizationName: String,
    val plan: String,
    val activeMemberCount: Int,
    val totalCreditsConsumed: Int,
    val remainingCredits: Int,
    val isQuotaExceeded: Boolean,
    val unpaidInvoiceCount: Int
)

data class RecordedUsage(
    val eventId: Long,
    val organizationId: Long,
    val userId: Long,
    val eventType: String,
    val creditsConsumed: Int,
    val recordedAt: LocalDateTime
)

data class InvoiceSummary(
    val invoiceId: Long,
    val organizationId: Long,
    val amountDue: BigDecimal,
    val status: String,
    val dueDate: LocalDate
)

class QuotaExceededException(message: String) : RuntimeException(message)
class InactiveWorkspaceException(message: String) : RuntimeException(message)
