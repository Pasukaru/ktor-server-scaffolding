package my.company.app.lib.tx

import com.zaxxer.hikari.pool.HikariPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import my.company.app.lib.eager
import my.company.app.lib.logger
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.sql.Connection
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class TransactionContext(
    val connection: Connection,
    val dialect: SQLDialect = SQLDialect.POSTGRES_9_5
) : AbstractCoroutineContextElement(TransactionContext) {

    companion object Key : CoroutineContext.Key<TransactionContext> {
        val logger = logger<TransactionContext>()
    }

    val id = UUID.randomUUID()!!

    private val mutex: Mutex = Mutex()
    var committed: Boolean = false
        private set

    var rolledBack: Boolean = false
        private set

    private val beforeCommitHooks = mutableListOf<() -> Unit>()
    private val afterCommitHooks = mutableListOf<() -> Unit>()
    private val afterRollbackHooks = mutableListOf<() -> Unit>()
    private val afterCompletionHooks = mutableListOf<() -> Unit>()

    suspend fun beginTransaction() {
        execute {
            logger.trace("$this: BEGIN")
            DSL.using(connection, dialect).execute("BEGIN TRANSACTION")
        }
    }

    suspend fun commit() {
        beforeCommitHooks.forEach { it.invoke() }
        execute {
            logger.trace("$this: COMMIT")
            DSL.using(connection, dialect).execute("COMMIT")
            committed = true
        }
        afterCommitHooks.forEach { it.invoke() }
        afterCompletionHooks.forEach { it.invoke() }
    }

    suspend fun rollback() {
        execute {
            logger.trace("$this: ROLLBACK")
            DSL.using(connection, dialect).execute("ROLLBACK")
            rolledBack = true
        }
        afterRollbackHooks.forEach { it.invoke() }
        afterCompletionHooks.forEach { it.invoke() }
    }

    fun expectActive() {
        if (committed || rolledBack) throw IllegalStateException("Transaction is already completed")
    }

    suspend fun <T> execute(op: suspend Connection.() -> T): T {
        logger.trace("$this: LOCK ${coroutineContext[TransactionContext]}")
        // mutex.lock()
        logger.trace("$this: LOCKED ${coroutineContext[TransactionContext]}")

        return try {
            expectActive()
            op(connection)
        } finally {
            logger.trace("$this: UNLOCKED ${coroutineContext[TransactionContext]}")
            // mutex.unlock()
        }
    }

    fun beforeCommit(block: () -> Unit) = beforeCommitHooks.add(block)
    fun afterCommit(block: () -> Unit) = afterCommitHooks.add(block)
    fun afterRollback(block: () -> Unit) = afterRollbackHooks.add(block)
    fun afterCompletion(block: () -> Unit) = afterCompletionHooks.add(block)

    override fun toString(): String = "TransactionContext[$id]"
}

suspend fun <T> transaction(block: suspend CoroutineScope.() -> T): T {
    val pool = eager<HikariPool>()
    val tx = TransactionContext(pool.connection)

    tx.beginTransaction()

    try {
        val result = withContext(tx, block)

        tx.commit()

        return result
    } catch (e: Throwable) {
        tx.rollback()
        throw e
    } finally {
        pool.evictConnection(tx.connection)
    }
}

suspend fun <T> databaseConnectionWithoutTransaction(block: suspend CoroutineScope.() -> T): T {
    val pool = eager<HikariPool>()
    val tx = TransactionContext(pool.connection)
    try {
        return withContext(tx, block)
    } catch (e: Throwable) {
        throw e
    } finally {
        pool.evictConnection(tx.connection)
    }
}
