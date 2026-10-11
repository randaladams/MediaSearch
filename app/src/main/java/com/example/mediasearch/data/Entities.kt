package com.example.mediasearch.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "movies", indices = [Index("title")])
data class Movie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val year: Int?,
    val folderName: String,
)

@Entity(tableName = "shows", indices = [Index("title")])
data class Show(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val year: Int?,
    val folderName: String,
)

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = Show::class,
            parentColumns = ["id"],
            childColumns = ["showId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("showId")],
)
data class Season(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val showId: Long,
    val name: String,
    val number: Int?,
)

data class ShowWithSeasons(
    @Embedded val show: Show,
    @Relation(parentColumn = "id", entityColumn = "showId")
    val seasons: List<Season>,
)
