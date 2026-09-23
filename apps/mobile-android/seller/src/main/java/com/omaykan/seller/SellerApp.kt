package com.omaykan.seller

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Nothing but the Hilt root.
 *
 * :app's Application also builds an image loader, because a storefront is
 * mostly photographs. This one shows names, counts and money, so there is
 * nothing to set up before the first screen — which is the whole reason it can
 * stay this short.
 */
@HiltAndroidApp
class SellerApp : Application()
