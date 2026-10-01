# Example 12: Idiomatic Kotlin DSL & Reified Extensions

Demonstrates using **JDBScript in Kotlin** to achieve clean, declarative test fixture setups with zero boilerplate.

---

## Key Highlights

### 1. Receiver Lambdas (`T.() -> Unit`) & Schema Defaults
By defining lightweight Kotlin extension functions on `IJDBEngine<T>`, `db -> db.table()` transforms into direct, type-safe DSL calls. Incidental database constraints (timestamps, slugs) are populated via `defaults(tools)`, keeping test arrangements focused strictly on the variables under test:

```kotlin
engine.reset {
    organizations().id(1L).name("Alpha Team")
    subscriptions().id(101L).org_id(1L).plan("PRO").monthly_quota_credits(10_000)

    users().id(11L).org_id(1L).role("ADMIN").status("ACTIVE")
    users().id(12L).org_id(1L).role("MEMBER").status("ACTIVE")
    users().id(13L).org_id(1L).role("MEMBER").status("INVITED")
}
```

### 2. In-Place Row Mutation (`update { ... }`)
Modify specific row columns in place using receiver lambdas without re-seeding the entire schema:

```kotlin
engine.update {
    organizations().id(1L).active(false)
}
```

### 3. Reified Generic Helper & One-Liner DSL (`insertDSL<D>()`)
Switch smoothly into a domain-specific sub-interface DSL without passing Java `.class` tokens or creating intermediate variables:

```kotlin
engine.insertDSL<ISaaSDomainDSL> {
    createWorkspaceWithSubscription(
        orgId = 10L,
        name = "Delta Enterprise",
        plan = SubscriptionPlan.ENTERPRISE,
        members = listOf(
            MemberSpec(id = 101L, email = "ceo@delta.com", role = "ADMIN"),
            MemberSpec(id = 102L, email = "lead@delta.com", role = "MEMBER")
        )
    )
}
```

### 4. Kotlin Compiler Configuration Note (`-Xjvm-default=all`)
When defining custom sub-interface helper methods in Kotlin for use with `engine.as(...)`, configure `-Xjvm-default=all` in `kotlin-maven-plugin` (or `@JvmDefaultWithCompatibility`) so Kotlin generates native JVM interface default methods rather than synthetic `DefaultImpls` helper classes:

```xml
<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <configuration>
        <jvmTarget>17</jvmTarget>
        <args>
            <arg>-Xjvm-default=all</arg>
        </args>
    </configuration>
</plugin>
```
