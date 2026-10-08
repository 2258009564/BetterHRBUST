package com.glassous.betterhrbust.data.repository

sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<out T>(val data: T, val isOfflineCache: Boolean = false) : Resource<T>
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>
}
