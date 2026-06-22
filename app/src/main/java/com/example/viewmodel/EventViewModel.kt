package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.EventRepository
import com.example.model.ScheduledEvent
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EventRepository

    val allEvents: StateFlow<List<ScheduledEvent>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = EventRepository(database.eventDao())
        allEvents = repository.allEvents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun addEvent(
        title: String,
        description: String,
        timeMillis: Long,
        useVoice: Boolean,
        useFlash: Boolean,
        announcementText: String
    ) {
        viewModelScope.launch {
            val newEvent = ScheduledEvent(
                title = title,
                description = description,
                timeMillis = timeMillis,
                enabled = true,
                useVoice = useVoice,
                useFlash = useFlash,
                announcementText = announcementText
            )
            val eventId = repository.insert(newEvent)
            
            // Re-schedule with correct database ID
            val savedEvent = newEvent.copy(id = eventId)
            AlarmScheduler.schedule(getApplication(), savedEvent)
        }
    }

    fun toggleEventStatus(event: ScheduledEvent) {
        viewModelScope.launch {
            val updatedEvent = event.copy(enabled = !event.enabled)
            repository.update(updatedEvent)
            
            if (updatedEvent.enabled) {
                AlarmScheduler.schedule(getApplication(), updatedEvent)
            } else {
                AlarmScheduler.cancel(getApplication(), updatedEvent)
            }
        }
    }

    fun deleteEvent(event: ScheduledEvent) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication(), event)
            repository.delete(event)
        }
    }
}
