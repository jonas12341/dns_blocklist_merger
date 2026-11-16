package de.jonas.dnsblocklistmerger.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.jonas.dnsblocklistmerger.core.DomainStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist ORDER BY priority, id")
    fun observeAll(): Flow<List<BlocklistEntity>>

    @Query("SELECT * FROM blocklist ORDER BY priority, id")
    suspend fun getAll(): List<BlocklistEntity>

    @Query("SELECT * FROM blocklist WHERE id = :id")
    suspend fun get(id: Long): BlocklistEntity?

    @Query("SELECT MAX(priority) FROM blocklist")
    suspend fun maxPriority(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(list: BlocklistEntity): Long

    @Update
    suspend fun update(list: BlocklistEntity)

    @Query("DELETE FROM blocklist WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExceptionDao {
    @Query("SELECT * FROM exception ORDER BY domain")
    fun observeAll(): Flow<List<ExceptionEntity>>

    @Query("SELECT domain FROM exception")
    suspend fun allDomains(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exception: ExceptionEntity)

    @Query("DELETE FROM exception WHERE domain = :domain")
    suspend fun delete(domain: String)
}

@Dao
interface OutputDao {
    @Query("SELECT * FROM output_state WHERE id = 0")
    fun observeState(): Flow<OutputStateEntity?>

    @Query("SELECT * FROM output_state WHERE id = 0")
    suspend fun getState(): OutputStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: OutputStateEntity)

    @Query("DELETE FROM output_domain")
    suspend fun clearDomains()

    @Insert
    suspend fun insertDomains(domains: List<OutputDomainEntity>)

    @Transaction
    suspend fun replaceDomains(domains: List<OutputDomainEntity>, chunk: Int = 5_000) {
        clearDomains()
        domains.chunked(chunk).forEach { insertDomains(it) }
    }

    /** Domains written to the file, in output order. */
    @Query("SELECT domain FROM output_domain WHERE status IN ('REQUIRED', 'INCLUDED') ORDER BY domain")
    suspend fun writtenDomains(): List<String>

    @Query(
        """
        SELECT * FROM output_domain
        WHERE (:query = '' OR domain LIKE '%' || :query || '%')
        ORDER BY rank DESC, domain
        """,
    )
    fun pageDomains(query: String): PagingSource<Int, OutputDomainEntity>

    @Query("SELECT COUNT(*) FROM output_domain WHERE status = :status")
    suspend fun countByStatus(status: DomainStatus): Int
}
