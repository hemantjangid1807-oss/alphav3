package com.alphasim.brain.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlphaListEntity>>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observe(id: Long): Flow<AlphaListEntity?>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun get(id: Long): AlphaListEntity?

    @Query("SELECT * FROM lists")
    suspend fun all(): List<AlphaListEntity>

    @Insert
    suspend fun insert(list: AlphaListEntity): Long

    @Update
    suspend fun update(list: AlphaListEntity)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT listId,
               COUNT(*) AS total,
               SUM(CASE WHEN status = 'COMPLETE' THEN 1 ELSE 0 END) AS complete,
               SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) AS running,
               SUM(CASE WHEN status = 'QUEUED' THEN 1 ELSE 0 END) AS queued,
               SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) AS errors,
               SUM(CASE WHEN status = 'COMPLETE' AND failedChecks = 0 THEN 1 ELSE 0 END) AS passing,
               MAX(sharpe) AS bestSharpe
        FROM alphas GROUP BY listId
        """
    )
    fun observeStats(): Flow<List<ListStats>>
}

@Dao
interface AlphaDao {
    @Query("SELECT * FROM alphas WHERE listId = :listId ORDER BY position")
    fun observeByList(listId: Long): Flow<List<AlphaEntity>>

    @Query("SELECT * FROM alphas WHERE listId = :listId ORDER BY position")
    suspend fun byList(listId: Long): List<AlphaEntity>

    @Query("SELECT * FROM alphas WHERE id = :id")
    fun observe(id: Long): Flow<AlphaEntity?>

    @Query("SELECT * FROM alphas WHERE id = :id")
    suspend fun get(id: Long): AlphaEntity?

    @Insert
    suspend fun insertAll(items: List<AlphaEntity>)

    @Update
    suspend fun update(item: AlphaEntity)

    @Query("DELETE FROM alphas WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM alphas WHERE listId = :listId")
    suspend fun deleteByList(listId: Long)

    @Query("SELECT MAX(position) FROM alphas WHERE listId = :listId")
    suspend fun maxPosition(listId: Long): Int?

    @Query("SELECT expression FROM alphas WHERE listId = :listId")
    suspend fun expressions(listId: Long): List<String>

    @Query("SELECT COUNT(*) FROM alphas WHERE status = :status")
    fun observeCount(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM alphas WHERE status = :status")
    suspend fun count(status: String): Int

    /** Queued work, with interrupted simulations (that already have a URL) first. */
    @Query("SELECT * FROM alphas WHERE status = 'QUEUED' ORDER BY (simulationUrl IS NULL), queuedAt, listId, position LIMIT 1")
    suspend fun nextQueued(): AlphaEntity?

    @Query("SELECT * FROM alphas WHERE status = 'QUEUED' AND simulationUrl IS NULL AND listId = :listId ORDER BY queuedAt, position LIMIT :limit")
    suspend fun nextFreshInList(listId: Long, limit: Int): List<AlphaEntity>

    @Query("SELECT * FROM alphas WHERE simulationUrl = :url AND status = 'QUEUED' ORDER BY multiIndex")
    suspend fun queuedByUrl(url: String): List<AlphaEntity>

    @Query("UPDATE alphas SET status = 'RUNNING', startedAt = :now, progress = 0, message = NULL WHERE id IN (:ids)")
    suspend fun markRunning(ids: List<Long>, now: Long)

    @Query("UPDATE alphas SET status = 'QUEUED', queuedAt = :now, attempts = 0, message = NULL WHERE id IN (:ids) AND status NOT IN ('RUNNING', 'QUEUED')")
    suspend fun queueIds(ids: List<Long>, now: Long)

    @Query("UPDATE alphas SET status = 'IDLE', queuedAt = NULL, simulationUrl = NULL, multiIndex = NULL WHERE status = 'QUEUED'")
    suspend fun unqueueAll()

    @Query("UPDATE alphas SET status = 'IDLE', queuedAt = NULL, simulationUrl = NULL, multiIndex = NULL WHERE status = 'QUEUED' AND listId = :listId")
    suspend fun unqueueList(listId: Long)

    /** Called whenever the engine stops: anything mid-flight goes back to the queue (and resumes polling later). */
    @Query("UPDATE alphas SET status = 'QUEUED', progress = NULL WHERE status = 'RUNNING'")
    suspend fun parkRunning()

    @Query("UPDATE alphas SET simulationUrl = :url, multiIndex = :multiIndex WHERE id = :id")
    suspend fun setSimulationUrl(id: Long, url: String?, multiIndex: Int?)

    @Query("UPDATE alphas SET progress = :progress WHERE id IN (:ids)")
    suspend fun setProgress(ids: List<Long>, progress: Double?)

    @Query("UPDATE alphas SET status = 'ERROR', message = :message, progress = NULL, simulationUrl = NULL, multiIndex = NULL, finishedAt = :now WHERE id = :id")
    suspend fun markError(id: Long, message: String, now: Long)

    @Query("UPDATE alphas SET message = :message WHERE id = :id")
    suspend fun setMessage(id: Long, message: String?)

    @Query(
        """
        UPDATE alphas SET status = 'IDLE', progress = NULL, simulationUrl = NULL, multiIndex = NULL, queuedAt = NULL,
            alphaId = NULL, message = NULL, attempts = 0, sharpe = NULL, fitness = NULL, turnover = NULL, returns = NULL,
            drawdown = NULL, margin = NULL, pnl = NULL, bookSize = NULL, longCount = NULL, shortCount = NULL, grade = NULL,
            alphaStage = NULL, alphaStatus = NULL, alphaName = NULL, tags = NULL, checksJson = NULL, failedChecks = NULL,
            pendingChecks = NULL, selfCorr = NULL, prodCorr = NULL, startedAt = NULL, finishedAt = NULL
        WHERE id IN (:ids) AND status != 'RUNNING'
        """
    )
    suspend fun resetIds(ids: List<Long>)

    @Query("UPDATE alphas SET status = 'IDLE', queuedAt = NULL WHERE status = 'ERROR' AND id IN (:ids)")
    suspend fun clearErrors(ids: List<Long>)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key")
    suspend fun get(key: String): CacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntity)

    @Query("DELETE FROM cache WHERE `key` LIKE :prefix || '%'")
    suspend fun deletePrefix(prefix: String)

    @Query("DELETE FROM cache")
    suspend fun clear()
}
