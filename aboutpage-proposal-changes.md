The About page is much better conceptually than the homepage because it explains what makes Omaykan different. The main weakness is presentation: it currently feels like a long informational article placed inside a narrow column rather than a polished marketplace brand page.

The biggest issue I see is scale. On desktop, almost everything is too small: text, cards, images, icons, and section content. You have a lot of unused horizontal space, which makes the page look more like a mobile/tablet layout centered inside desktop.

I would increase the main content width substantially. Your current content appears to be around 850–950px wide. For this page, I would target roughly:

max-width: 1200px;
margin: 0 auto;
padding: 0 24px;

Then let some sections go up to 1280px where appropriate.

The second problem is that the page has too many consecutive sections with the same visual rhythm:

heading
image
paragraph
divider
heading
image
paragraph

After a while, everything starts carrying equal importance.

I would redesign the hierarchy.

The top of the About page

Currently you have tabs:

About Us | Sell on Omaykan | Ride with Omaykan | Contact Us

I like this. Keep it.

But immediately below, instead of just putting the video in the center, make a proper About hero.

Something like:

Your neighborhood market, online.

Omaykan connects customers with nearby groceries, sari-sari stores, wet-market vendors, and independent shops — starting in Baguio and La Trinidad.

Stores keep control of their prices. Riders keep their delivery earnings. Customers get a simpler way to shop locally.

[ Start shopping ] [ Sell on Omaykan ]

Then place your video beside it.

Desktop layout:

┌───────────────────────────────────────────────────────────┐
│                                                           │
│  Your neighborhood market,       [                     ] │
│  online.                          [       VIDEO         ] │
│                                   [                     ] │
│  Shop from businesses around                              │
│  your community...                                        │
│                                                           │
│  [Start shopping] [Sell on Omaykan]                       │
│                                                           │
└───────────────────────────────────────────────────────────┘

That would immediately make the page feel more intentional.

Your "Grocery Delivery in 3 Simple Steps" section is good

This is probably one of the strongest sections on the page.

But the cards are currently too small.

Make the three cards larger, with stronger titles:

1. Shop nearby

Browse groceries and everyday essentials from shops serving your location.

2. The shop prepares your order

Your order goes directly to the merchant, who confirms and prepares it.

3. A local rider delivers it

A rider collects the order and brings it to your address.

Your current images are useful, but I would give them about a 16:10 or 4:3 crop and make the title more prominent.

Something like:

01                     02                     03

[ LARGE IMAGE ]        [ LARGE IMAGE ]        [ LARGE IMAGE ]

Shop nearby            Shop prepares it       Delivered locally

Short description      Short description      Short description
"Our Promise" should become one of the main visual sections

This is actually the most important content on your About page.

You currently have:

0% commission from the shop
100% of delivery fee to the rider
In-store prices for you

Those are excellent differentiators.

But right now they're shown as small cards.

Make this section much stronger:

A marketplace designed differently

Then three large cards:

┌─────────────────────┐
│        0%           │
│                     │
│ Seller commission   │
│                     │
│ Shops don't lose a  │
│ percentage of every │
│ order to Omaykan.   │
└─────────────────────┘

┌─────────────────────┐
│       100%          │
│                     │
│ Delivery earnings   │
│                     │
│ The delivery fee    │
│ belongs to the      │
│ rider.              │
└─────────────────────┘

┌─────────────────────┐
│  Store-controlled   │
│       prices        │
│                     │
│ Merchants decide    │
│ what their products │
│ cost.               │
└─────────────────────┘

I would also reconsider the phrase:

In-store prices for you

because technically you cannot guarantee that every merchant will always make the online price identical unless your system enforces it.

Safer wording:

Prices set by the shop

Then underneath:

Shops control their own product pricing. Omaykan does not take a percentage from each order that has to be recovered through marketplace markups.

That is a stronger and more defensible claim.

"The Price You See Is The Price In The Shop"

The concept is good, but the section currently looks visually disconnected.

I'd turn this into a split section rather than image → centered paragraph.

For example:

┌──────────────────────────────────────────────────────────┐
│                                                          │
│ [                        ]     No marketplace markup      │
│ [                        ]                                │
│ [      MARKET IMAGE      ]     Sellers control what      │
│ [                        ]     their products cost.       │
│ [                        ]                                │
│                               Omaykan doesn't take a     │
│                               percentage of every sale.  │
│                                                          │
└──────────────────────────────────────────────────────────┘

And change the headline to something more natural:

Local shops stay in control of their prices.

That is more credible and less absolute.

The "Riders" section needs clearer wording

You currently have:

Riders in Always Free

and

A Delivery Fee That Goes To The Rider

The first heading sounds awkward.

I'd change this section to:

Fairer for local riders

Then:

No platform membership fee

Riders can join the delivery network without paying Omaykan a recurring platform fee.

The delivery fee goes to the rider

When an order is assigned and completed, the delivery charge belongs to the rider rather than being split with the marketplace.

Assuming that accurately reflects your business model.

The green circular icons are good. Keep that visual treatment.

"Omaykan for Shops" should be larger

This section is strategically important because Omaykan depends on seller acquisition.

At the moment, it gets only one image and one paragraph.

I'd make it a large conversion section.

Something like:

Built for neighborhood businesses

From sari-sari stores and market stalls to groceries and specialty shops, Omaykan gives local businesses an online storefront without taking a percentage from every order.

Manage your products, receive customer orders, track sales, and reach people nearby.

[ Become a seller ]

Beside it:

✓ Online storefront
✓ Product management
✓ Order management
✓ Local customer reach
✓ No percentage-based sales commission

This would also communicate much better to potential merchants who land directly on /about.

The "Ready to shop?" banner is good

I would keep this.

But increase its height slightly and make the button visible.

Currently it mostly appears as:

Ready to shop?

I'd use:

Ready to shop local?

See businesses currently serving your area.

[ Browse nearby shops ]

The button gives it an actual purpose.

"Riding With Omaykan"

This section currently looks almost like another informational article.

Treat it as recruitment instead:

Deliver locally. Earn directly.

Omaykan connects independent riders with deliveries from businesses around their area.

[ Become an Omaykan rider ]

Then show three small benefits:

Keep the delivery fee
Choose available jobs
Work around your area

That is clearer.

"We Are Starting In Baguio" is important but visually too weak

I would elevate this.

This is one of the strongest things Omaykan can say because it establishes the brand as deliberately local.

Instead of plain text at the bottom:

Starting in Baguio and La Trinidad

Omaykan is being built one community at a time. We're beginning with local shops, riders, and customers across Baguio and La Trinidad before expanding into more areas of the Cordillera.

Then perhaps show a simple local map/illustration or image of Baguio public market / La Trinidad rather than plain white space.

You could eventually make this:

Currently serving

● Baguio City
● La Trinidad

Coming next
○ Selected nearby Benguet municipalities

That also makes the startup/growth story tangible.

The page needs fewer dividers

There are horizontal lines between almost every section.

I'd reduce them significantly.

Instead, alternate backgrounds:

WHITE
Hero

OFF-WHITE
How it works

WHITE
Our promise

VERY LIGHT GREEN
Pricing philosophy

WHITE
For sellers

DARK GREEN
Ready to shop

WHITE
For riders

LIGHT GRAY/GREEN
Starting in Baguio

FOOTER

That would make the page feel much more designed without adding much complexity.

Typography is currently too small

This is very noticeable in the screenshot.

I would roughly use:

.hero-title {
    font-size: clamp(42px, 5vw, 64px);
}

.section-title {
    font-size: 32px;
}

.card-title {
    font-size: 20px;
}

.body {
    font-size: 16px;
    line-height: 1.7;
}

.small {
    font-size: 14px;
}

Your current body text looks closer to 11–13px on desktop.

That makes users work harder than necessary.