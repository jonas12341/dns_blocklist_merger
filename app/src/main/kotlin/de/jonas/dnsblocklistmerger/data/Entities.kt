package de.jonas.dnsblocklistmerger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.jonas.dnsblocklistmerger.core.DomainStatus

enum class ListStatus { PENDING, OK, ERROR }

/**
 * One blocklist source. The domains of its last good download live in a file of
 * [DomainFileStore], keyed by [id]; a list with 300,000 domains is too big for a table row.
 */
@Entity(tableName = "blocklist", indices = [Index(value = ["url"], unique = true)])
data class BlocklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val name: String,
    val priority: Int,
    /** Detected format of the last good download, null before the first one. */
    val format: String? = null,
    val status: ListStatus = ListStatus.PENDING,
    val errorMessage: String? = null,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long? = null,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    val etag: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null,
    @ColumnInfo(name = "domain_count") val domainCount: Int = 0,
)

/** A domain that is never written: excluded from the domain list or added by hand. */
@Entity(tableName = "exception")
data class ExceptionEntity(
    @PrimaryKey val domain: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Result of the last selection per domain; feeds the domain page and the output file. */
@Entity(
    tableName = "output_domain",
    indices = [Index(value = ["rank", "domain"]), Index("status")],
)
data class OutputDomainEntity(
    @PrimaryKey val domain: String,
    val frequency: Int,
    @ColumnInfo(name = "best_priority") val bestPriority: Int,
    val rank: Long,
    val status: DomainStatus,
)

/** Single row (id 0) with the numbers of the last selection and the last written file. */
@Entity(tableName = "output_state")
data class OutputStateEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "computed_at") val computedAt: Long? = null,
    val entries: Int = 0,
    val bytes: Long = 0,
    @ColumnInfo(name = "required_bytes") val requiredBytes: Long = 0,
    @ColumnInfo(name = "max_bytes") val maxBytes: Long = 0,
    @ColumnInfo(name = "top_priority") val topPriority: Int? = null,
    @ColumnInfo(name = "cut_by_limit") val cutByLimit: Int = 0,
    val exceptions: Int = 0,
    @ColumnInfo(name = "dropped_subdomains") val droppedSubdomains: Int = 0,
    @ColumnInfo(name = "total_domains") val totalDomains: Int = 0,
    @ColumnInfo(name = "written_at") val writtenAt: Long? = null,
    @ColumnInfo(name = "write_error") val writeError: String? = null,
)
