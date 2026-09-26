{{--
  Shared frame for the town and category landing pages. Self-contained: no
  Vite bundle, so the page is complete as served and costs one request. The
  palette is the storefront's (apps/web/src/landing/landing.css), so moving
  from here to a shop does not feel like changing sites.
--}}
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{{ $title }}</title>
  <meta name="description" content="{{ $description }}">
  @if ($indexable)
    <link rel="canonical" href="{{ $canonical }}">
  @else
    {{-- Nothing listed yet: reachable, but not a page to rank. --}}
    <meta name="robots" content="noindex, follow">
  @endif
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="Omaykan">
  <meta property="og:title" content="{{ $title }}">
  <meta property="og:description" content="{{ $description }}">
  <meta property="og:url" content="{{ $canonical }}">
  @if (! empty($ogImage))
    <meta property="og:image" content="{{ $ogImage }}">
  @endif
  <link rel="icon" href="/favicon.ico" sizes="any">
  <link rel="icon" type="image/png" href="/icon.png">
  <link rel="apple-touch-icon" href="/icon.png">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Libre+Caslon+Text:wght@400;700&display=swap" rel="stylesheet">
  @foreach ($jsonLd as $graph)
    <script type="application/ld+json">{!! json_encode($graph, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_HEX_TAG | JSON_HEX_AMP) !!}</script>
  @endforeach
  <style>
    :root {
      --forest: #1f2e25; --forest-soft: #2b3f33; --cream: #ffffff; --sand: #f3f1ed;
      --clay: #b4532a; --clay-deep: #93401d; --leaf: #1a7a45; --leaf-wash: #eaf3ed;
      --ink: #231d18; --muted: #6f665c; --faint: #9a9086; --rule: #e5e5e5;
      --serif: 'Libre Caslon Text', Georgia, 'Times New Roman', serif;
      --sans: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
      --gutter: 28px;
    }
    * { box-sizing: border-box; }
    body { margin: 0; background: var(--cream); color: var(--ink); font: 16px/1.5 var(--sans); }
    a { color: inherit; }
    .wrap { max-width: 1180px; margin: 0 auto; padding: 0 var(--gutter); }
    .bar { background: var(--forest); color: var(--cream); }
    .bar .wrap { display: flex; align-items: center; gap: 20px; min-height: 68px; }
    .bar img { height: 22px; display: block; }
    .bar nav { margin-left: auto; display: flex; gap: 22px; font-size: 15px; }
    .bar nav a { text-decoration: none; opacity: .88; }
    .bar nav a:hover { opacity: 1; text-decoration: underline; }
    .crumbs { font-size: 14px; color: var(--muted); padding: 22px 0 0; }
    .crumbs ol { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px; }
    .crumbs li + li::before { content: '›'; margin-right: 6px; color: var(--faint); }
    .crumbs a { text-decoration: none; }
    .crumbs a:hover { text-decoration: underline; }
    .hero { padding: 18px 0 30px; border-bottom: 1px solid var(--rule); }
    h1 { font: 700 clamp(30px, 5vw, 46px)/1.1 var(--serif); letter-spacing: -0.01em; margin: 0 0 10px; }
    .lede { font-size: 18px; color: var(--muted); margin: 0; max-width: 60ch; }
    .count { margin: 14px 0 0; font-size: 14px; font-weight: 600; color: var(--leaf); }
    h2 { font: 700 24px/1.2 var(--serif); margin: 40px 0 16px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 22px; margin: 26px 0 10px; }
    .card { display: flex; flex-direction: column; text-decoration: none; border: 1px solid var(--rule); border-radius: 14px; overflow: hidden; background: var(--cream); transition: box-shadow .2s, transform .2s; }
    .card:hover { box-shadow: 0 10px 28px rgba(35, 29, 24, .12); transform: translateY(-2px); }
    .card__media { position: relative; aspect-ratio: 16 / 10; overflow: hidden; background: var(--sand); display: grid; place-items: center; }
    .card__media img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; display: block; }
    .card__initials { font: 700 38px var(--serif); color: var(--forest-soft); }
    .badge { position: absolute; top: 10px; left: 10px; font-size: 12px; font-weight: 700; padding: 3px 9px; border-radius: 999px; }
    .badge--new { background: var(--leaf); color: var(--cream); }
    .badge--closed { background: var(--ink); color: var(--cream); }
    .card__body { padding: 14px 16px 16px; display: flex; flex-direction: column; gap: 4px; }
    .card__name { font-weight: 700; font-size: 17px; }
    .card__meta { font-size: 14px; color: var(--muted); }
    .card__aisles { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
    .card__aisles span { font-size: 12px; background: var(--leaf-wash); color: var(--leaf); padding: 2px 8px; border-radius: 999px; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; margin: 0; padding: 0; list-style: none; }
    .tile { display: block; height: 100%; padding: 16px 18px; border: 1px solid var(--rule); border-radius: 12px; text-decoration: none; }
    a.tile:hover { border-color: var(--forest-soft); background: var(--sand); }
    .tile strong { display: block; font-size: 17px; }
    .tile span { font-size: 14px; color: var(--muted); }
    .tile--empty, .tile--empty span { color: var(--faint); }
    .empty { padding: 26px 28px; background: var(--sand); border-radius: 14px; margin: 26px 0; }
    .empty p { margin: 0 0 6px; }
    .empty p:last-child { margin: 0; }
    .links { display: flex; flex-wrap: wrap; gap: 10px; padding: 0; margin: 0; list-style: none; }
    .links a { display: inline-block; padding: 8px 14px; border: 1px solid var(--rule); border-radius: 999px; text-decoration: none; font-size: 15px; }
    .links a:hover { border-color: var(--forest-soft); }
    .cta { margin: 56px 0 0; padding: 28px 0; background: var(--forest); color: var(--cream); }
    .cta .wrap { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; }
    .cta p { margin: 0; font: 700 20px/1.3 var(--serif); }
    .cta a { background: var(--clay); color: var(--cream); padding: 11px 20px; border-radius: 999px; text-decoration: none; font-weight: 700; }
    .cta a:hover { background: var(--clay-deep); }
    footer.wrap { padding-top: 26px; padding-bottom: 40px; font-size: 14px; color: var(--muted); }
    @media (max-width: 640px) {
      :root { --gutter: 16px; }
      .bar nav { gap: 14px; font-size: 14px; }
      .lede { font-size: 16px; }
      .grid { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
  <header class="bar">
    <div class="wrap">
      <a href="/" aria-label="Omaykan — home"><img src="/logo-wordmark-light.png" alt="Omaykan"></a>
      <nav aria-label="Site">
        <a href="/#shops">All shops</a>
        <a href="/account">Your orders</a>
      </nav>
    </div>
  </header>

  <main class="wrap">
    <nav class="crumbs" aria-label="Breadcrumb">
      <ol>
        @foreach ($crumbs as $crumb)
          <li>@if ($crumb['url'] !== null)<a href="{{ $crumb['url'] }}">{{ $crumb['name'] }}</a>@else<span aria-current="page">{{ $crumb['name'] }}</span>@endif</li>
        @endforeach
      </ol>
    </nav>

    @yield('content')
  </main>

  <section class="cta">
    <div class="wrap">
      <p>Run a shop in {{ $town['name'] }}? Take orders online for free.</p>
      <a href="/seller/signup">Sell on Omaykan</a>
    </div>
  </section>

  <footer class="wrap">
    Omaykan — local shops in Baguio and Benguet, ordered online and delivered by local riders.
  </footer>
</body>
</html>
