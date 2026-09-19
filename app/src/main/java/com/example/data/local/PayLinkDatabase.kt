package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.CachedInvoiceDao
import com.example.data.local.dao.NotifiedInvoiceDao
import com.example.data.local.dao.ProcessedPaymentDao
import com.example.data.local.dao.RejectedInvoiceDao
import com.example.data.local.entity.CachedInvoiceEntity
import com.example.data.local.entity.NotifiedInvoiceEntity
import com.example.data.local.entity.ProcessedPaymentEntity
import com.example.data.local.entity.RejectedInvoiceEntity

@Database(
    entities = [
        ProcessedPaymentEntity::class,
        CachedInvoiceEntity::class,
        NotifiedInvoiceEntity::class,
        RejectedInvoiceEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class PayLinkDatabase : RoomDatabase() {

    abstract fun processedPaymentDao(): ProcessedPaymentDao
    abstract fun cachedInvoiceDao(): CachedInvoiceDao
    abstract fun notifiedInvoiceDao(): NotifiedInvoiceDao
    abstract fun rejectedInvoiceDao(): RejectedInvoiceDao

    companion object {
        @Volatile
        private var INSTANCE: PayLinkDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Compatibility-only migration: schema is unchanged.
            }
        }

        fun getInstance(context: Context): PayLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PayLinkDatabase::class.java,
                    "paylink_database"
                )
                    .addMigrations(MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
