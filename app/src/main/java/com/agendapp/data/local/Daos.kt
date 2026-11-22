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

    @Query("SELECT * FROM productos WHERE activo = 1 ORDER BY nombre")
    suspend fun todosActivos(): List<ProductoEntity>

    @Query("SELECT * FROM productos WHERE activo = 1 AND codigoBarra = :codigo LIMIT 1")
    suspend fun porCodigo(codigo: String): ProductoEntity?

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

@Dao
interface FichajeDao {
    @Query("SELECT * FROM fichajes WHERE empleadoId = :empleadoId ORDER BY fecha, hora")
    fun observarDe(empleadoId: Int): Flow<List<FichajeEntity>>

    @Query("SELECT * FROM fichajes WHERE empleadoId = :empleadoId AND fecha = :fecha ORDER BY hora")
    suspend fun delDia(empleadoId: Int, fecha: String): List<FichajeEntity>

    @Query("SELECT * FROM fichajes WHERE id = :id")
    suspend fun porId(id: String): FichajeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(item: FichajeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodos(items: List<FichajeEntity>)

    @Query("SELECT COUNT(*) FROM fichajes WHERE empleadoId = :empleadoId AND pendiente = 1")
    suspend fun contarPendientes(empleadoId: Int): Int

    @Query("DELETE FROM fichajes WHERE id LIKE 'demo-%'")
    suspend fun borrarEjemplo()
}

@Dao
interface RetiroDao {
    @Query("SELECT * FROM retiros WHERE empleadoId = :empleadoId ORDER BY fecha, hora")
    fun observarDe(empleadoId: Int): Flow<List<RetiroEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(item: RetiroEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodos(items: List<RetiroEntity>)

    @Query("SELECT COUNT(*) FROM retiros WHERE empleadoId = :empleadoId AND pendiente = 1")
    suspend fun contarPendientes(empleadoId: Int): Int

    @Query("DELETE FROM retiros WHERE id LIKE 'demo-%'")
    suspend fun borrarEjemplo()
}

@Dao
interface PagoDao {
    @Query("SELECT * FROM pagos WHERE empleadoId = :empleadoId ORDER BY fecha, hora")
    fun observarDe(empleadoId: Int): Flow<List<PagoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodos(items: List<PagoEntity>)

    @Query("DELETE FROM pagos WHERE id LIKE 'demo-%'")
    suspend fun borrarEjemplo()
}
