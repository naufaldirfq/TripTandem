package com.triptandem.shared

/** Narrow, versioned callable transport. Neither native adapter reads private discovery documents. */
interface CommunityRepository {
    suspend fun execute(operation: String, input: Map<String, String>): DataResult<String>
}
object UnavailableCommunityRepository : CommunityRepository {
    override suspend fun execute(operation: String, input: Map<String, String>): DataResult<String> = DataResult.Failure(TripTandemError.Offline)
}

/** Push is a hint to open authenticated activity, never a cached grant of access. */
object CommunityNavigation {
    private val mutableRequests = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val requests: kotlinx.coroutines.flow.StateFlow<Long> = mutableRequests
    fun openActivity() { mutableRequests.value += 1 }
}
