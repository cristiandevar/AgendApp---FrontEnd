package com.agendapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos WHERE activo = 1 ORDER BY nombre")
    fun observarActivos(): Flow<List<ProductoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(items: List<ProductoEntity>)

    @Query("DELETE FROM productos")
    suspend fun borrarTodo()
}

@Dao
interface ParametroDao {
    @Query("SELECT * FROM parametros ORDER BY clave")
    fun observarTodos(): Flow<List<ParametroEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(items: List<ParametroEntity>)

    @Query("DELETE FROM parametros")
    suspend fun borrarTodo()
}

@Dao
interface FeriadoDao {
    @Query("SELECT * FROM feriados ORDER BY fecha")
    fun observarTodos(): Flow<List<FeriadoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(items: List<FeriadoEntity>)

    @Query("DELETE FROM feriados")
    suspend fun borrarTodo()
}

@Dao
interface TarifaDao {
    @Query("SELECT * FROM tarifas ORDER BY empleadoNombre, vigenteDesde DESC")
    fun observarTodas(): Flow<List<TarifaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(items: List<TarifaEntity>)

    @Query("DELETE FROM tarifas")
    suspend fun borrarTodo()
}

@Dao
interface HorarioDao {
    @Query("SELECT * FROM horarios ORDER BY fecha, horaInicio")
    fun observarTodos(): Flow<List<HorarioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(items: List<HorarioEntity>)

    @Query("DELETE FROM horarios")
    suspend fun borrarTodo()
}
