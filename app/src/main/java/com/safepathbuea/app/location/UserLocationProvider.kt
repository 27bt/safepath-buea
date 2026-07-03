package com.safepathbuea.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/** Thin coroutine wrapper around [FusedLocationProviderClient]. Callers must
 * already hold ACCESS_FINE_LOCATION; this class doesn't check permissions
 * itself so it can be unit-testable and permission-agnostic. */
class UserLocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        val cancellationSource = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()
        return client.getCurrentLocation(request, cancellationSource.token).await()
    }
}
