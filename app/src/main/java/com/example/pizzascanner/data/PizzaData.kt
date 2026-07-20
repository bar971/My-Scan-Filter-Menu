package com.example.pizzascanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "pizze")
data class Pizza(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val ingredienti: List<String>,
    val fonte: String = "fonte sconosciuta",
    val telefoni: List<String> = emptyList()
)

class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toList(value: String): List<String> = Json.decodeFromString(value)
}

@Dao
interface PizzaDao {
    @Insert
    suspend fun insertAll(pizze: List<Pizza>)

    @Query("SELECT * FROM pizze ORDER BY fonte, nome")
    fun getAll(): Flow<List<Pizza>>

    @Query("SELECT * FROM pizze")
    suspend fun getAllOnce(): List<Pizza>

    @Query("UPDATE pizze SET fonte = :nuova WHERE fonte = :vecchia")
    suspend fun rinominaFonte(vecchia: String, nuova: String)

    @Query("DELETE FROM pizze")
    suspend fun clear()

    @Query("DELETE FROM pizze WHERE fonte = :fonte")
    suspend fun deleteFonte(fonte: String)
}

@Database(entities = [Pizza::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pizzaDao(): PizzaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: android.content.Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pizze.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}