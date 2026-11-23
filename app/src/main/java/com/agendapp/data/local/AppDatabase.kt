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
        FichajeEntity::class,
        RetiroEntity::class,
        PagoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun parametroDao(): ParametroDao
    abstract fun feriadoDao(): FeriadoDao
    abstract fun tarifaDao(): TarifaDao
    abstract fun horarioDao(): HorarioDao
    abstract fun fichajeDao(): FichajeDao
    abstract fun retiroDao(): RetiroDao
    abstract fun pagoDao(): PagoDao

    companion object {
        /**
         * Versión 1 (Fase 2) solo tenía el catálogo, que se vuelve a descargar: se puede
         * recrear sin perder nada. Desde la versión 2 hay movimientos del empleado que
         * pueden estar sin sincronizar, así que toda migración futura tiene que ser real
         * (nunca destructiva).
         */
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "agendapp.db")
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}
