package org.jdbscript.examples.kotlindsl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jdbscript.IJDBEngine
import org.jdbscript.JDBEngine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KotlinDslTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var engine: IJDBEngine<ISaaSSchema>
    private lateinit var billingService: BillingAndQuotaService

    @BeforeAll
    fun initDatabase() {
        dataSource = createDataSource()
        dataSource.createTables(SCHEMA_DDL)
        engine = JDBEngine.builder(ISaaSSchema::class.java).dataSource(dataSource).build()
        billingService = BillingAndQuotaService(dataSource)
    }

    @BeforeEach
    fun cleanDatabase() {
        engine.cleanupDB()
    }

    @AfterAll
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `receiver lambdas provide clean declarative fixture setup without repeating db prefix`() {
        // Arrange: Kotlin receiver lambda allows calling schema methods directly inside the block.
        // Columns under test (roles, statuses, credit amounts) are explicit; incidental metadata (timestamps, slugs) use schema defaults.
        engine.reset {
            organizations().id(1L).name("Alpha Team")
            subscriptions().id(101L).org_id(1L).plan("PRO").monthly_quota_credits(10_000)

            users().id(11L).org_id(1L).role("ADMIN").status("ACTIVE")
            users().id(12L).org_id(1L).role("MEMBER").status("ACTIVE")
            users().id(13L).org_id(1L).role("MEMBER").status("INVITED")

            usage_events().id(501L).org_id(1L).user_id(11L).credits_consumed(1_200)
            usage_events().id(502L).org_id(1L).user_id(12L).credits_consumed(800)
        }

        // Act
        val health = billingService.calculateWorkspaceHealth(1L)

        // Assert
        assertEquals("Alpha Team", health.organizationName)
        assertEquals("PRO", health.plan)
        assertEquals(2, health.activeMemberCount) // charlie is INVITED, not ACTIVE
        assertEquals(2_000, health.totalCreditsConsumed)
        assertEquals(8_000, health.remainingCredits)
        assertFalse(health.isQuotaExceeded)
    }

    @Test
    fun `update receiver allows in-place mutation to test edge cases without re-seeding`() {
        // Arrange baseline workspace
        engine.reset {
            organizations().id(1L).name("Beta Corp")
            subscriptions().id(101L).org_id(1L).monthly_quota_credits(10_000)
            users().id(11L).org_id(1L)
        }

        // Verify normal operation succeeds
        val usage = billingService.recordUsage(orgId = 1L, userId = 11L, eventType = "DATA_SYNC", credits = 500)
        assertEquals(500, usage.creditsConsumed)

        // Mutate single row in place: deactivate workspace
        engine.update {
            organizations().id(1L).active(false)
        }

        // Verify business service rejects operations for deactivated workspace
        assertFailsWith<InactiveWorkspaceException> {
            billingService.recordUsage(orgId = 1L, userId = 11L, eventType = "DATA_SYNC", credits = 100)
        }
    }

    @Test
    fun `kotlin collections and ranges enable expressive procedural batch generation`() {
        engine.reset {
            organizations().id(1L).name("Gamma Labs")
            subscriptions().id(101L).org_id(1L).plan("FREE").monthly_quota_credits(1_000)
            users().id(11L).org_id(1L)

            // Generate 9 distinct simulated events of 100 credits each (total 900 / 1000 credits)
            (1..9).forEach { i ->
                usage_events().id(100L + i)
                    .org_id(1L)
                    .user_id(11L)
                    .event_type("MICRO_QUERY_$i")
                    .credits_consumed(100)
            }
        }

        val healthBefore = billingService.calculateWorkspaceHealth(1L)
        assertEquals(900, healthBefore.totalCreditsConsumed)
        assertEquals(100, healthBefore.remainingCredits)

        // Recording 50 credits succeeds (900 + 50 <= 1000)
        billingService.recordUsage(orgId = 1L, userId = 11L, eventType = "FINAL_QUERY", credits = 50)

        // Recording 60 more credits exceeds quota (950 + 60 > 1000)
        assertFailsWith<QuotaExceededException> {
            billingService.recordUsage(orgId = 1L, userId = 11L, eventType = "OVERFLOW_QUERY", credits = 60)
        }
    }

    @Test
    fun `reified asDSL helper with Kotlin default arguments creates declarative multi-table domain fixtures`() {
        // Arrange multi-table aggregate using custom mixin DSL with default parameters and insertDSL one-liner
        engine.insertDSL<ISaaSDomainDSL> {
            createWorkspaceWithSubscription(
                orgId = 10L,
                name = "Delta Enterprise",
                plan = SubscriptionPlan.ENTERPRISE,
                members = listOf(
                    MemberSpec(id = 101L, email = "ceo@delta.com", role = "ADMIN"),
                    MemberSpec(id = 102L, email = "lead@delta.com", role = "MEMBER"),
                    MemberSpec(id = 103L, email = "contractor@delta.com", role = "GUEST", status = "ACTIVE")
                )
            )

            seedUsageEvents(
                10L,
                UsageSpec(id = 2001L, userId = 101L, eventType = "AI_TRAINING", credits = 25_000),
                UsageSpec(id = 2002L, userId = 102L, eventType = "BATCH_INFERENCE", credits = 15_000)
            )

            seedInvoices(
                10L,
                InvoiceSpec(id = 901L, amount = BigDecimal("299.00"), status = "PAID", dueDate = "2026-09-15"),
                InvoiceSpec(id = 902L, amount = BigDecimal("299.00"), status = "UNPAID", dueDate = "2026-10-15")
            )
        }

        // Act
        val health = billingService.calculateWorkspaceHealth(10L)

        // Assert
        assertEquals("Delta Enterprise", health.organizationName)
        assertEquals("ENTERPRISE", health.plan)
        assertEquals(3, health.activeMemberCount)
        assertEquals(40_000, health.totalCreditsConsumed)
        assertEquals(60_000, health.remainingCredits)
        assertEquals(1, health.unpaidInvoiceCount)
    }

    @Test
    fun `generate monthly invoice creates billable record with correct base fee`() {
        engine.insertDSL<ISaaSDomainDSL> {
            createWorkspaceWithSubscription(
                orgId = 20L,
                name = "Omega Solutions",
                plan = SubscriptionPlan.PRO
            )
        }

        val dueDate = LocalDate.of(2026, 11, 15)
        val invoice = billingService.generateMonthlyInvoice(orgId = 20L, invoiceId = 888L, dueDate = dueDate)

        assertEquals(888L, invoice.invoiceId)
        assertEquals(20L, invoice.organizationId)
        assertEquals(BigDecimal("49.00"), invoice.amountDue)
        assertEquals("UNPAID", invoice.status)
        assertEquals(dueDate, invoice.dueDate)

        val health = billingService.calculateWorkspaceHealth(20L)
        assertEquals(1, health.unpaidInvoiceCount)
    }

    // =========================================================================
    // Database Setup & DDL Fixture
    // =========================================================================

    private fun createDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:kotlindsl_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
            username = "sa"
            password = "sa"
            maximumPoolSize = 5
        }
        return HikariDataSource(config)
    }

    private fun DataSource.createTables(ddl: String) {
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(ddl)
            }
        }
    }

    companion object {
        private val SCHEMA_DDL = """
            CREATE TABLE organizations (
                id BIGINT PRIMARY KEY,
                slug VARCHAR(100) NOT NULL,
                name VARCHAR(255) NOT NULL,
                tier VARCHAR(50) NOT NULL,
                active BOOLEAN NOT NULL,
                created_at TIMESTAMP NOT NULL
            );
            CREATE TABLE users (
                id BIGINT PRIMARY KEY,
                org_id BIGINT NOT NULL,
                email VARCHAR(255) NOT NULL,
                role VARCHAR(50) NOT NULL,
                status VARCHAR(50) NOT NULL,
                FOREIGN KEY (org_id) REFERENCES organizations(id)
            );
            CREATE TABLE subscriptions (
                id BIGINT PRIMARY KEY,
                org_id BIGINT NOT NULL UNIQUE,
                plan VARCHAR(50) NOT NULL,
                monthly_quota_credits INT NOT NULL,
                status VARCHAR(50) NOT NULL,
                renews_on DATE NOT NULL,
                FOREIGN KEY (org_id) REFERENCES organizations(id)
            );
            CREATE TABLE usage_events (
                id BIGINT PRIMARY KEY,
                org_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                event_type VARCHAR(100) NOT NULL,
                credits_consumed INT NOT NULL,
                recorded_at TIMESTAMP NOT NULL,
                FOREIGN KEY (org_id) REFERENCES organizations(id),
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
            CREATE TABLE invoices (
                id BIGINT PRIMARY KEY,
                org_id BIGINT NOT NULL,
                amount_due DECIMAL(10, 2) NOT NULL,
                status VARCHAR(50) NOT NULL,
                due_date DATE NOT NULL,
                FOREIGN KEY (org_id) REFERENCES organizations(id)
            );
        """.trimIndent()
    }
}
