package com.example.data

import com.example.model.ScheduledEvent
import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {
    val allEvents: Flow<List<ScheduledEvent>> = eventDao.getAllEvents()

    suspend fun insert(event: ScheduledEvent): Long {
        return eventDao.insertEvent(event)
    }

    suspend fun update(event: ScheduledEvent) {
        eventDao.updateEvent(event)
    }

    suspend fun delete(event: ScheduledEvent) {
        eventDao.deleteEvent(event)
    }

    suspend fun updateStatus(id: Long, enabled: Boolean) {
        eventDao.updateEventStatus(id, enabled)
    }

    suspend fun getEventById(id: Long): ScheduledEvent? {
        return eventDao.getEventById(id)
    }
}
