package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.DeliveryStage
import com.omaykan.seller.core.model.OrderStatus
import com.omaykan.seller.core.model.PaymentMethod
import com.omaykan.seller.core.model.RiderDirectory
import com.omaykan.seller.core.model.SavedRider
import com.omaykan.seller.core.model.SellerOrder
import com.omaykan.seller.core.network.ApiCaller
import com.omaykan.seller.core.network.SellerApi
import com.omaykan.seller.core.network.dto.AssignRiderRequestDto
import com.omaykan.seller.core.network.dto.SaveRiderRequestDto
import com.omaykan.seller.core.network.dto.SettlePaymentRequestDto
import com.omaykan.seller.core.network.dto.UpdateDeliveryStageRequestDto
import com.omaykan.seller.core.network.dto.UpdateStatusRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shop's storefront orders, and the four decisions this app can make about
 * one.
 *
 * Every write answers with the whole order rather than an acknowledgement, and
 * the view model puts that answer back into the list in place of the row it
 * had. That is what keeps a card honest after a tap: assigning a rider also
 * moves the delivery stage, and settling a payment also fixes the payment
 * method, so a screen that patched only the field it changed would be showing a
 * different order from the one the server holds.
 */
@Singleton
class SellerOrderRepository @Inject constructor(
    private val api: SellerApi,
    private val caller: ApiCaller,
) {
    suspend fun orders(): List<SellerOrder> =
        caller.call { api.orders() }.orders.map { it.toModel() }

    suspend fun advanceStatus(orderId: String, to: OrderStatus): SellerOrder =
        caller.call { api.updateStatus(orderId, UpdateStatusRequestDto(to.wire)) }.order.toModel()

    suspend fun advanceDelivery(orderId: String, to: DeliveryStage): SellerOrder =
        caller.call {
            api.updateDeliveryStage(orderId, UpdateDeliveryStageRequestDto(to.wire))
        }.order.toModel()

    /**
     * Name the rider carrying this order, by typing one in.
     *
     * [save] puts them on the shop's list on the way past, so the same number
     * is not retyped four times a day. The server links the row to a platform
     * account if that number happens to belong to one, which is how a shop's
     * own rider quietly gains an app and a map without anybody configuring
     * anything.
     */
    suspend fun assignRider(
        orderId: String,
        name: String,
        phone: String?,
        save: Boolean = false,
    ): SellerOrder =
        caller.call {
            api.assignRider(
                orderId,
                AssignRiderRequestDto(
                    riderName = name.trim(),
                    riderPhone = phone?.trim()?.takeIf { it.isNotEmpty() },
                    saveRider = save,
                ),
            )
        }.order.toModel()

    /**
     * Hand the order to somebody already on the shop's list.
     *
     * The name and number come off that row server-side rather than being sent
     * again — and when the row is a platform account, so does `rider_id`, which
     * is what puts the job in that person's app and their position on the map.
     */
    suspend fun assignSavedRider(orderId: String, savedRiderId: String): SellerOrder =
        caller.call {
            api.assignRider(orderId, AssignRiderRequestDto(savedRiderId = savedRiderId))
        }.order.toModel()

    /** Take the rider off, and put the order back on the platform board. */
    suspend fun unassignRider(orderId: String): SellerOrder =
        caller.call { api.unassignRider(orderId) }.order.toModel()

    /**
     * The shop's own riders, read fresh every time the picker opens.
     *
     * Deliberately not cached with the orders: a saved rider's `online` flag is
     * only true for the ten seconds it describes, and a stale copy would offer
     * a rider whose phone is switched off as the one to send an order to.
     */
    suspend fun riders(): RiderDirectory =
        caller.call { api.savedRiders() }.toModel()

    suspend fun saveRider(
        name: String,
        phone: String?,
        riderId: String? = null,
        note: String? = null,
    ): SavedRider =
        caller.call {
            api.saveRider(
                SaveRiderRequestDto(
                    riderId = riderId,
                    name = name.trim(),
                    phone = phone?.trim()?.takeIf { it.isNotEmpty() },
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }.savedRider.toModel()

    suspend fun deleteSavedRider(savedRiderId: String) {
        caller.call { api.deleteSavedRider(savedRiderId) }
    }

    /**
     * Record that the money arrived.
     *
     * Never retried, at any layer, and it is the one call in this app that
     * would do real harm if it were: the payment row it writes is summed into
     * the cash a drawer is expected to hold at shift close, so a duplicate
     * makes an honest till reconcile short by that amount. OkHttp's connection
     * retries are off, nothing above catches a timeout and tries again, and the
     * server refuses a second settlement with a 422 as the last line of
     * defence. If it fails, the person at the counter decides.
     *
     * No tender or change is sent. Those are a drawer's arithmetic and belong
     * to the till that has the drawer; from a phone, the honest statement is
     * "this order has been paid, by this method", and the server reads an
     * absent tender as the exact total.
     *
     * No user id either — this app is paired as a device, not signed in as a
     * person, so `payment_confirmed_by_user_id` is left null rather than
     * attributed to somebody who may not have been holding the phone. The
     * column is nullable for exactly this reason.
     */
    suspend fun settle(orderId: String, method: PaymentMethod): SellerOrder =
        caller.call {
            api.settlePayment(orderId, SettlePaymentRequestDto(paymentMethod = method.wire))
        }.order.toModel()
}
