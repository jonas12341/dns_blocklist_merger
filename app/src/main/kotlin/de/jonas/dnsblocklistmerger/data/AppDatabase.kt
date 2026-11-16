package de.jonas.dnsblocklistmerger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import de.jonas.dnsblocklistmerger.core.DomainStatus

class Converters {
    @TypeConverter fun listStatusToString(value: ListStatus): String = value.name
    @TypeConverter fun stringToListStatus(value: String): ListStatus = ListStatus.valueOf(value)
    @TypeConverter fun domainStatusToString(value: DomainStatus): String = value.name
    @TypeConverter fun stringToDomainStatus(value: String): DomainStatus = DomainStatus.valueOf(value)
}

@Database(
    entities = [BlocklistEntity::class, ExceptionEntity::class, OutputDomainEntity::class, OutputStateEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blocklistDao(): BlocklistDao
    abstract fun exceptionDao(): ExceptionDao
    abstract fun outputDao(): OutputDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dns_blocklist_merger.db").build()
    }
}
