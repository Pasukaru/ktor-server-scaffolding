package my.company.app.db

import java.sql.Connection

enum class IsolationLevel(val sqlStr: String, val sqlInt: Int) {
    NONE("NONE", Connection.TRANSACTION_NONE),
    SERIALIZABLE("SERIALIZABLE", Connection.TRANSACTION_SERIALIZABLE),
    REPEATABLE_READ("REPEATABLE READ", Connection.TRANSACTION_REPEATABLE_READ),
    READ_COMMITTED("READ COMMITTED", Connection.TRANSACTION_READ_COMMITTED),
    READ_UNCOMMITTED("READ UNCOMMITTED", Connection.TRANSACTION_READ_UNCOMMITTED)
}
