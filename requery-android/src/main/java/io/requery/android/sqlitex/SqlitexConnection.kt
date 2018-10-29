/*
 * Copyright 2018 requery.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.requery.android.sqlitex

import android.database.sqlite.SQLiteException
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.sqlite.BaseConnection

import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.Statement

internal class SqlitexConnection(val database: SQLiteDatabase) : BaseConnection() {
    private val metaData: SqlitexMetaData
    private var enteredTransaction: Boolean = false

    init {
        autoCommit = true
        metaData = SqlitexMetaData(this)
    }

    override fun ensureTransaction() {
        if (!autoCommit) {
            if (!database.inTransaction()) {
                database.beginTransaction()
                enteredTransaction = true
            }
        }
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String) {
        try {
            database.execSQL(sql)
        } catch (e: SQLiteException) {
            BaseConnection.throwSQLException(e)
        }

    }

    @Throws(SQLException::class)
    override fun commit() {
        if (autoCommit) {
            throw SQLException("commit called while in autoCommit mode")
        }
        if (database.inTransaction() && enteredTransaction) {
            try {
                database.setTransactionSuccessful()
            } catch (e: IllegalStateException) {
                throw SQLException(e)
            } finally {
                database.endTransaction()
                enteredTransaction = false
            }
        }
    }

    override fun createStatement(): Statement {
        return SqlitexStatement(this)
    }

    @Throws(SQLException::class)
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement {
        return createStatement(resultSetType,
                resultSetConcurrency, ResultSet.HOLD_CURSORS_OVER_COMMIT)
    }

    @Throws(SQLException::class)
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int,
                                 resultSetHoldability: Int): Statement {
        if (resultSetConcurrency == ResultSet.CONCUR_UPDATABLE) {
            throw SQLFeatureNotSupportedException("CONCUR_UPDATABLE not supported")
        }
        return SqlitexStatement(this)
    }

    override fun getMetaData(): DatabaseMetaData {
        return metaData
    }

    override fun isClosed(): Boolean {
        return !database.isOpen
    }

    override fun isReadOnly(): Boolean {
        return database.isReadOnly
    }

    @Throws(SQLException::class)
    override fun prepareStatement(sql: String, autoGeneratedKeys: Int): PreparedStatement {
        return SqlitexPreparedStatement(this, sql, autoGeneratedKeys)
    }

    @Throws(SQLException::class)
    override fun prepareStatement(sql: String,
                                  resultSetType: Int,
                                  resultSetConcurrency: Int,
                                  resultSetHoldability: Int): PreparedStatement {
        return SqlitexPreparedStatement(this, sql, Statement.NO_GENERATED_KEYS)
    }

    @Throws(SQLException::class)
    override fun prepareStatement(sql: String, columnNames: Array<String>): PreparedStatement {
        return SqlitexPreparedStatement(this, sql, Statement.RETURN_GENERATED_KEYS)
    }

    @Throws(SQLException::class)
    override fun rollback() {
        if (autoCommit) {
            throw SQLException("commit called while in autoCommit mode")
        }
        database.endTransaction()
    }
}