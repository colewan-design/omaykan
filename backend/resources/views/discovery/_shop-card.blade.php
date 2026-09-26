@php
    // The town, not the doorstep: the last part of the address. Same rule as
    // the storefront's own directory card (ShopDirectory.vue, areaOf).
    $parts = array_values(array_filter(array_map('trim', explode(',', (string) $shop['address'])), fn ($part) => $part !== ''));
    $area = $parts === [] ? '' : end($parts);
    $initials = collect(preg_split('/\s+/', trim($shop['name'])))
        ->filter()
        ->take(2)
        ->map(fn ($word) => mb_strtoupper(mb_substr($word, 0, 1)))
        ->implode('');
@endphp
<a class="card" href="{{ $shop['url'] }}">
  <div class="card__media">
    @if ($shop['imageUrl'])
      <img src="{{ $shop['imageUrl'] }}" alt="" loading="lazy" decoding="async">
    @else
      <span class="card__initials" aria-hidden="true">{{ $initials }}</span>
    @endif
    @if ($shop['orderingPaused'])
      <span class="badge badge--closed">Closed now</span>
    @elseif ($shop['isNew'])
      <span class="badge badge--new">New</span>
    @endif
  </div>
  <div class="card__body">
    <span class="card__name">{{ $shop['name'] }}</span>
    <span class="card__meta">{{ collect([$shop['businessTypeLabel'], $area])->filter()->implode(' · ') }}</span>
    @if ($shop['categories'] !== [])
      <span class="card__aisles">
        @foreach ($shop['categories'] as $aisle)
          <span>{{ $aisle }}</span>
        @endforeach
      </span>
    @endif
  </div>
</a>
