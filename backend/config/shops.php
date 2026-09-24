<?php

/*
|--------------------------------------------------------------------------
| Where a shop lives, and where the built frontend is
|--------------------------------------------------------------------------
|
| A shop's own address is `<slug>.omaykan.com`, and until now that was
| entirely the browser's business: the page reads the slug off its own
| hostname (packages/shared slugFromShopHost) and asks the API which shop it
| belongs to. Nothing server-side needed to know.
|
| Link previews changed that. Facebook, Messenger, Viber, Slack and Discord
| fetch the HTML and never run it, so the shop's name and photo have to be in
| the file before it is sent — which means PHP has to resolve the shop from
| the Host header too. ShopShellController does that, and these two values
| are what it needs.
|
| `root_domain` is the server-side twin of VITE_SHOP_ROOT_DOMAIN in
| apps/web/.env.production. Blank **on purpose** where there is no shop
| domain — a laptop, a preview build — and a blank value means no host is
| read as a shop, which is the behaviour this application had before.
|
| `web_root` is the directory nginx serves the built frontend from, because
| the shell ShopShellController stamps its tags into is the same `shop.html`
| the build produced. The default matches production, where the two halves
| are siblings (`/var/www/omaykan/backend` and `/var/www/omaykan/web`). In
| this repository they are not, so local work sets
| WEB_ROOT=../apps/web/dist.
|
*/

return [
    'root_domain' => env('SHOP_ROOT_DOMAIN', ''),

    'web_root' => env('WEB_ROOT', base_path('../web')),
];
