package com.omaykan.rider.core.data

import com.omaykan.rider.core.model.Earnings
import com.omaykan.rider.core.network.ApiCaller
import com.omaykan.rider.core.network.RiderApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the work has paid.
 *
 * One call, no cache, no polling. Earnings are not a live number the way the
 * board is — they change when the rider finishes a job, which is a thing the
 * rider does, not a thing that happens to them — so this is read when the
 * screen opens and when the rider asks again, and never on a timer. A fortnight
 * of totals refreshing every fifteen seconds would be battery spent on a figure
 * that cannot have moved.
 */
@Singleton
class EarningsRepository @Inject constructor(
    private val api: RiderApi,
    private val caller: ApiCaller,
) {
    suspend fun fetch(): Earnings = caller.call { api.earnings() }.toModel()
}
