package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.ScheduledEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM scheduled_events ORDER BY timeMillis ASC")
    fun getAllEvents(): Flow<List<ScheduledEvent>>

    @Query("SELECT * FROM scheduled_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): ScheduledEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ScheduledEvent): Long

    @Update
    suspend fun updateEvent(event: ScheduledEvent)

    @Delete
    suspend fun deleteEvent(event: ScheduledEvent)

    @Query("UPDATE scheduled_events SET enabled = :enabled WHERE id = :id")
    suspend fun updateEventStatus(id: Long, enabled: Boolean)
}
