@extends('discovery.layout')

@php
    $count = count($shops);
    $pageUrl = "{$site}/{$town['slug']}/{$category['slug']}";
    $townUrl = "{$site}/{$town['slug']}";
    $headingLower = mb_strtolower($category['heading']);

    $title = "{$category['heading']} in {$town['name']} — order online | Omaykan";
    $description = $count > 0
        ? "{$count} ".($count === 1 ? 'shop' : 'shops')." for {$headingLower} in {$town['region']}. Browse what they have today and order straight from the shop, delivered by local riders."
        : "No shops for {$headingLower} in {$town['name']} on Omaykan yet. See what the rest of {$town['name']} has to offer.";
    $canonical = $pageUrl;
    $absolute = fn (?string $url) => $url === null ? null : (str_starts_with($url, '/') ? $site.$url : $url);
    $ogImage = $absolute(collect($shops)->pluck('imageUrl')->filter()->first());

    $crumbs = [
        ['name' => 'Omaykan', 'url' => '/'],
        ['name' => $town['name'], 'url' => "/{$town['slug']}"],
        ['name' => $category['name'], 'url' => null],
    ];

    $jsonLd = [
        [
            '@context' => 'https://schema.org',
            '@type' => 'BreadcrumbList',
            'itemListElement' => [
                ['@type' => 'ListItem', 'position' => 1, 'name' => 'Omaykan', 'item' => "{$site}/"],
                ['@type' => 'ListItem', 'position' => 2, 'name' => $town['name'], 'item' => $townUrl],
                ['@type' => 'ListItem', 'position' => 3, 'name' => $category['name'], 'item' => $pageUrl],
            ],
        ],
    ];
    if ($count > 0) {
        $jsonLd[] = [
            '@context' => 'https://schema.org',
            '@type' => 'ItemList',
            'name' => "{$category['heading']} in {$town['name']}",
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
    <h1>{{ $category['heading'] }} in {{ $town['name'] }}</h1>
    <p class="lede">{{ $category['blurb'] }}</p>
    @if ($count > 0)
      <p class="count">{{ $count }} {{ $count === 1 ? 'shop' : 'shops' }} on Omaykan in {{ $town['region'] }}</p>
    @endif
  </section>

  @if ($count > 0)
    <div class="grid">
      @foreach ($shops as $shop)
        @include('discovery._shop-card', ['shop' => $shop])
      @endforeach
    </div>
  @else
    <div class="empty">
      <p><strong>No {{ $headingLower }} in {{ $town['name'] }} on Omaykan yet.</strong></p>
      <p>New shops join every week. In the meantime, <a href="/{{ $town['slug'] }}">see every shop in {{ $town['name'] }}</a>.</p>
    </div>
  @endif

  @if ($otherTowns !== [])
    <h2>{{ $category['name'] }} nearby</h2>
    <ul class="links">
      @foreach ($otherTowns as $other)
        <li><a href="/{{ $other['slug'] }}/{{ $category['slug'] }}">{{ $category['name'] }} in {{ $other['name'] }} ({{ $other['count'] }})</a></li>
      @endforeach
    </ul>
  @endif

  @if ($siblings !== [])
    <h2>More in {{ $town['name'] }}</h2>
    <ul class="links">
      @foreach ($siblings as $sibling)
        <li><a href="/{{ $town['slug'] }}/{{ $sibling['slug'] }}">{{ $sibling['name'] }} ({{ $sibling['count'] }})</a></li>
      @endforeach
    </ul>
  @endif
@endsection
