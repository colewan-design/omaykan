package com.omaykan.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omaykan.rider.core.designsystem.MoneyCard
import com.omaykan.rider.core.designsystem.OverviewTile
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SegmentedPills
import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryItem
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.model.Pickup
import com.omaykan.rider.feature.work.AssignmentCard
import com.omaykan.rider.feature.work.CompletedRow
import com.omaykan.rider.feature.forgot.ForgotPasswordScreen
import com.omaykan.rider.feature.onboarding.OnboardingScreen
import com.omaykan.rider.feature.register.RegisterScreen
import com.omaykan.rider.feature.signin.SignInScreen
import com.omaykan.rider.feature.work.OfferCard
import dagger.hilt.android.AndroidEntryPoint

/**
 * Every card in the app, with made-up jobs, on one scrolling screen.
 *
 * Debug source set only, so it cannot ship. It exists because the board is the
 * screen that matters and it is empty unless a real shop has a real order out —
 * which makes "does an offer card look right" a question you otherwise cannot
 * ask without a backend, a shop and a customer.
 *
 * `adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity`
 * `... GalleryActivity --es screen signin`
 *   (`register`, `forgot`, `onboarding`, `map`, `drive`, `nav`)
 *
 * `map` is the 3D marker standing still with a bearing slider; `drive` is the
 * same marker driven along a real Directions route, which is the only way to
 * see whether it turns like a scooter rather than like a compass needle. Both
 * draw their own map and neither can catch a bug in the screen a rider opens.
 *
 * `nav` is the one that can: it renders the shipping `JobRouteMap` unmodified
 * and drives a position and a heading into it, so the follow camera, the route
 * line and the puck can be watched working without a backend, a shop, an order
 * and somebody actually riding to it.
 */
@AndroidEntryPoint
class GalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val cashOffer = DeliveryOffer(
            id = "1",
            ticketNumber = "1042",
            placedAt = null,
            pickup = Pickup("s1", "Aling Nena's Kitchen", "12 Otek St, Baguio City", 16.4, 120.6),
            dropoffArea = "Sto. Tomas, Baguio City",
            distanceKm = 2.4,
            deliveryFeeCents = 6500,
            itemCount = 3,
            paid = false,
            collectCents = 54000,
        )

        val prepaidOffer = cashOffer.copy(
            id = "2",
            ticketNumber = "1043",
            pickup = Pickup("s2", "Ganza Bakery", "Session Rd, Baguio City", 16.4, 120.6),
            dropoffArea = "Camp 7",
            distanceKm = 5.1,
            deliveryFeeCents = 9000,
            itemCount = 1,
            paid = true,
            collectCents = 0,
        )

        fun job(stage: DeliveryStage) = DeliveryAssignment(
            offer = cashOffer,
            stage = stage,
            acceptedAt = null,
            deliveryAddress = "88 Marcoville, Sto. Tomas, Baguio City",
            deliveryLat = 16.4,
            deliveryLng = 120.6,
            customerName = "Ana Villanueva",
            customerPhone = "09171234567",
            items = listOf(DeliveryItem("Pancit Canton", 2.0), DeliveryItem("Coke 1.5L", 1.0)),
        )

        val screen = intent.getStringExtra("screen")
        val bearing = intent.getIntExtra("bearing", 0).toFloat()
        val pitch = intent.getIntExtra("pitch", 55).toDouble()

        setContent {
            RiderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (screen) {
                        "map" -> MapPreview(bearing, pitch)
                        "drive" -> DriveSimulation(pitch)
                        "nav" -> NavigationPreview()
                        "signin" -> SignInScreen(onRegister = {}, onForgotPassword = {})
                        "register" -> RegisterScreen(onBack = {})
                        "forgot" -> ForgotPasswordScreen(onBack = {})
                        // Writes the "seen" flag on Get started, like the real
                        // one. Debug build, debug preferences file — the point
                        // of looking at it is to press the buttons.
                        "onboarding" -> OnboardingScreen(onDone = {})
                        else -> Cards(cashOffer, prepaidOffer, ::job)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cards(
    cashOffer: DeliveryOffer,
    prepaidOffer: DeliveryOffer,
    job: (DeliveryStage) -> DeliveryAssignment,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The home screen's furniture, without the home screen: HomeScreen
        // needs a signed-in session and a live earnings call, and neither is
        // available to a gallery. What can be checked here is the part that
        // has a look rather than a source — the two money cards side by side,
        // which is where a wrong contrast shows up first.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoneyCard(
                label = "Earned today",
                value = "P 780.00",
                caption = "9 deliveries, fees yours in full",
                actionLabel = "Breakdown",
                onAction = {},
                container = RiderTheme.colors.payout,
                modifier = Modifier.weight(1f),
            )
            MoneyCard(
                label = "Cash to collect",
                value = "P 540.00",
                caption = "Collected at the door, owed to the shop",
                actionLabel = "See the jobs",
                onAction = {},
                container = RiderTheme.colors.owed,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewTile(
                value = "150",
                label = "Delivered",
                icon = Icons.Filled.CheckCircle,
                tint = RiderTheme.colors.success,
                container = RiderTheme.colors.successSoft,
                modifier = Modifier.weight(1f),
            )
            OverviewTile(
                value = "2",
                label = "In your hands",
                icon = Icons.Filled.TwoWheeler,
                tint = MaterialTheme.colorScheme.primary,
                container = RiderTheme.colors.accentSoft,
                modifier = Modifier.weight(1f),
            )
        }

        SegmentedPills(listOf("Available", "My jobs (1)"), 0, {})
        OfferCard(cashOffer, busy = false, enabled = true, onAccept = {})
        OfferCard(prepaidOffer, busy = false, enabled = true, onAccept = {})
        AssignmentCard(job(DeliveryStage.Assigned), false, true, {}, {}, {})
        AssignmentCard(job(DeliveryStage.PickedUp), false, true, {}, {}, {})
        CompletedRow(job(DeliveryStage.Delivered))
    }
}
