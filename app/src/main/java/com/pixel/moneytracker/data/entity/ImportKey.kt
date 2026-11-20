package com.pixel.moneytracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "import_keys", foreignKeys = [ForeignKey(
    entity = Record::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["recordId"])])
data class ImportKey(@PrimaryKey val key: String, val recordId: Long)
