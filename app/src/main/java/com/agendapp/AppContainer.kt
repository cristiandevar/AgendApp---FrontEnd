package com.agendapp

import android.content.Context
import com.agendapp.data.local.AppDatabase
import com.agendapp.data.remote.Network
import com.agendapp.data.repo.AuthRepository
import com.agendapp.data.repo.CatalogoRepository
import com.agendapp.data.repo.MovimientosRepository
import com.agendapp.data.session.SessionStore

/** Crea y comparte las dependencias de la app (inyección manual, sin librerías). */
class AppContainer(context: Context) {
    val sesion = SessionStore(context)
    val db = AppDatabase.crear(context)
    private val api = Network.crearApi(sesion)

    val authRepository = AuthRepository(api, sesion, db)
    val catalogoRepository = CatalogoRepository(api, db, sesion)
    val movimientosRepository = MovimientosRepository(db)
}
