package dev.agentation.storage

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.dataStore by preferencesDataStore(name = "agentation")
