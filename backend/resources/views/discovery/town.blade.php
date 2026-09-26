@extends('discovery.layout')

@php
    $count = count($shops);
    $pageUrl = "{$site}/{$town['slug']}";
    $stocked = collect($categories)->filter(fn ($category) => $category['count'] > 0);

    $title = "Shop local in {$town['name']} — ".($stocked->isNotEmpty()
        ? mb_strtolower($stocked->take(3)->pluck('name')->implode(', ')).' and more'
        : 'order online')." | Omaykan";
    $description = $count > 0
        ? "Order from {$count} local ".($count === 1 ? 'shop' : 'shops')." in {$town['region']} on Omaykan: ".mb_strtolower($stocked->pluck('name')->implode(', ')).'. Delivered by local riders, paid on arrival.'
        : "Local shops in {$town['region']} are joining Omaykan. Order online from the shops you already know, delivered by local riders.";
    $canonical = $pageUrl;
    $absolute = fn (?string $url) => $url === null ? null : (str_starts_with($url, '/') ? $site.$url : $url);
    $ogImage = $absolute(collect($shops)->pluck('imageUrl')->filter()->first());

    $crumbs = [
        ['name' => 'Omaykan', 'url' => '/'],
        ['name' => $town['name'], 'url' => null],
    ];

    $jsonLd = [
        [
            '@context' => 'https://schema.org',
            '@type' => 'BreadcrumbList',
            'itemListElement' => [
                ['@type' => 'ListItem', 'position' => 1, 'name' => 'Omaykan', 'item' => "{$site}/"],
                ['@type' => 'ListItem', 'position' => 2, 'name' => $town['name'], 'item' => $pageUrl],
            ],
        ],
    ];
    if ($count > 0) {
        $jsonLd[] = [
            '@context' => 'https://schema.org',
            '@type' => 'ItemList',
            'name' => "Shops in {$town['name']}",
            'numberOfItems' => $count,
            'itemListElement' => collect($shops)->values()->map(fn ($shop, $i) => [
                '@type' => 'ListItem',
                'position' => $i + 1,
                'url' => $absolute($shop['url']),
                'name' => $shop['name'],
            ])->all(),
        ];
    }
@endphp

@section('content')
  <section class="hero">
    <h1>Shop local in {{ $town['name'] }}</h1>
    <p class="lede">Order from the shops you already know in {{ $town['region'] }} — straight from their own shelves, delivered by local riders.</p>
    @if ($count > 0)
      <p class="count">{{ $count }} {{ $count === 1 ? 'shop' : 'shops' }} taking orders</p>
    @endif
  </section>

  <h2>Browse {{ $town['name'] }}</h2>
  <ul class="tiles">
    @foreach ($categories as $category)
      <li>
        @if ($category['count'] > 0)
          <a class="tile" href="/{{ $town['slug'] }}/{{ $category['slug'] }}">
            <strong>{{ $category['name'] }}</strong>
            <span>{{ $category['count'] }} {{ $category['count'] === 1 ? 'shop' : 'shops' }}</span>
          </a>
        @else
          {{-- Not linked: an empty page is a dead end, and one a crawler
               should not be sent to. --}}
          <div class="tile tile--empty">
            <strong>{{ $category['name'] }}</strong>
            <span>Coming soon</span>
          </div>
        @endif
      </li>
    @endforeach
  </ul>

  @if ($count > 0)
    <h2>Every shop in {{ $town['name'] }}</h2>
    <div class="grid">
      @foreach ($shops as $shop)
        @include('discovery._shop-card', ['shop' => $shop])
      @endforeach
    </div>
  @else
    <div class="empty">
      <p><strong>No shops in {{ $town['name'] }} on Omaykan yet.</strong></p>
      <p><a href="/#shops">See every shop on Omaykan</a>.</p>
    </div>
  @endif

  @if ($otherTowns !== [])
    <h2>Nearby towns</h2>
    <ul class="links">
      @foreach ($otherTowns as $other)
        <li><a href="/{{ $other['slug'] }}">{{ $other['name'] }} ({{ $other['count'] }})</a></li>
      @endforeach
    </ul>
  @endif
@endsection
