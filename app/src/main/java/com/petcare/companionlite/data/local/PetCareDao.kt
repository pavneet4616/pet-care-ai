package com.petcare.companionlite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.petcare.companionlite.data.model.ActivityEntity
import com.petcare.companionlite.data.model.HealthLogEntity
import com.petcare.companionlite.data.model.NoteEntity
import com.petcare.companionlite.data.model.ExpenseEntity
import com.petcare.companionlite.data.model.WaterLogEntity
import com.petcare.companionlite.data.model.PetEntity
import com.petcare.companionlite.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM pets ORDER BY name")
    fun observeAll(): Flow<List<PetEntity>>

    @Query("SELECT * FROM pets ORDER BY name")
    suspend fun getAll(): List<PetEntity>

    @Query("SELECT * FROM pets WHERE id = :id")
    fun observeById(id: Long): Flow<PetEntity?>

    @Query("SELECT * FROM pets WHERE id = :id")
    suspend fun getById(id: Long): PetEntity?

    @Query("SELECT COUNT(*) FROM pets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pet: PetEntity): Long

    @Update
    suspend fun update(pet: PetEntity)

    @Query("DELETE FROM pets WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE petId = :petId ORDER BY timestamp DESC")
    fun observeForPet(petId: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE petId = :petId AND type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForType(petId: Long, type: String): ActivityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(activity: ActivityEntity): Long

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM activities WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    suspend fun getActivitiesSince(sinceMillis: Long): List<ActivityEntity>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE petId = :petId ORDER BY hour, minute")
    fun observeForPet(petId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT COUNT(*) FROM reminders WHERE petId = :petId")
    suspend fun countForPet(petId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HealthLogDao {
    @Query("SELECT * FROM health_logs WHERE petId = :petId ORDER BY timestamp DESC")
    fun observeForPet(petId: Long): Flow<List<HealthLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HealthLogEntity): Long

    @Query("DELETE FROM health_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface WaterLogDao {
    @Query("SELECT * FROM water_logs WHERE petId = :petId ORDER BY timestamp DESC")
    fun observeForPet(petId: Long): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM water_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WaterLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WaterLogEntity): Long

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE petId = :petId ORDER BY timestamp DESC")
    fun observeForPet(petId: Long): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long
    
    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE petId = :petId ORDER BY timestamp DESC")
    fun observeForPet(petId: Long): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
