package com.agendapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.math.BigDecimal

/** Los montos se guardan como texto para no perder precisión. */
class Convertidores {
    @TypeConverter
    fun desdeBigDecimal(valor: BigDecimal?): String? = valor?.toPlainString()

    @TypeConverter
    fun aBigDecimal(valor: String?): BigDecimal? = valor?.let { BigDecimal(it) }
}

@Database(
    entities = [
        ProductoEntity::class,
        ParametroEntity::class,
        FeriadoEntity::class,
        TarifaEntity::class,
        HorarioEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun parametroDao(): ParametroDao
    abstract fun feriadoDao(): FeriadoDao
    abstract fun tarifaDao(): TarifaDao
    abstract fun horarioDao(): HorarioDao

    companion object {
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "agendapp.db").build()
    }
}
