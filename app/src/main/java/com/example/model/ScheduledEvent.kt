package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_events")
data class ScheduledEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val timeMillis: Long,
    val enabled: Boolean = true,
    val useVoice: Boolean = true,
    val useFlash: Boolean = true,
    val announcementText: String = ""
)
