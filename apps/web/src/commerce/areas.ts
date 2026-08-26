// Places a shopper can name, and roughly where they are.
//
// There is no geocoder behind the storefront — no Google Places key, no
// Nominatim call — and the delivery filter needs a coordinate before it can
// decide whether a shop reaches you. So the address panel offers this list
// instead: the areas people in the beachhead city actually give as their
// address, each with an approximate centre.
//
// "Approximate" is the honest word. These are barangay/landmark centres good
// to a few hundred metres, which is fine against a 15 km delivery radius and
// useless as a rider's destination — the exact drop pin still comes from the
// device's own location at checkout. Say so wherever a distance derived from
// one of these is shown.
//
// Sharing my location is the accurate path and the panel offers it first; this
// list is what someone gets who declines it, or whose browser has no
// geolocation at all.

export interface ServiceArea {
  id: string
  name: string
  /** The heading it sits under in the picker. */
  group: string
  lat: number
  lng: number
}

export const serviceAreas: ServiceArea[] = [
  // -- Baguio City ---------------------------------------------------------
  { id: 'session-road', name: 'Session Road / CBD', group: 'Baguio City', lat: 16.4118, lng: 120.5966 },
  { id: 'burnham', name: 'Burnham Park', group: 'Baguio City', lat: 16.4103, lng: 120.5934 },
  { id: 'city-market', name: 'Baguio City Market', group: 'Baguio City', lat: 16.4149, lng: 120.5931 },
  { id: 'sm-baguio', name: 'SM City Baguio', group: 'Baguio City', lat: 16.409, lng: 120.5977 },
  { id: 'magsaysay', name: 'Magsaysay Avenue', group: 'Baguio City', lat: 16.4258, lng: 120.5983 },
  { id: 'aurora-hill', name: 'Aurora Hill', group: 'Baguio City', lat: 16.4231, lng: 120.6039 },
  { id: 'trancoville', name: 'Trancoville', group: 'Baguio City', lat: 16.4235, lng: 120.5962 },
  { id: 'quirino-hill', name: 'Quirino Hill', group: 'Baguio City', lat: 16.4245, lng: 120.5905 },
  { id: 'quezon-hill', name: 'Quezon Hill', group: 'Baguio City', lat: 16.418, lng: 120.585 },
  { id: 'guisad', name: 'Guisad', group: 'Baguio City', lat: 16.4212, lng: 120.5866 },
  { id: 'lucban', name: 'Lucban / Legarda', group: 'Baguio City', lat: 16.4183, lng: 120.5993 },
  { id: 'military-cutoff', name: 'Military Cut-off', group: 'Baguio City', lat: 16.4093, lng: 120.6047 },
  { id: 'teachers-camp', name: "Teachers' Camp", group: 'Baguio City', lat: 16.409, lng: 120.61 },
  { id: 'pacdal', name: 'Pacdal', group: 'Baguio City', lat: 16.4144, lng: 120.618 },
  { id: 'mines-view', name: 'Mines View / Gibraltar', group: 'Baguio City', lat: 16.4136, lng: 120.6284 },
  { id: 'camp-john-hay', name: 'Camp John Hay', group: 'Baguio City', lat: 16.3971, lng: 120.6171 },
  { id: 'country-club', name: 'Baguio Country Club', group: 'Baguio City', lat: 16.4045, lng: 120.6135 },
  { id: 'dominican-hill', name: 'Dominican Hill / Mirador', group: 'Baguio City', lat: 16.4132, lng: 120.582 },
  { id: 'naguilian', name: 'Naguilian Road', group: 'Baguio City', lat: 16.4118, lng: 120.5732 },
  { id: 'pinsao', name: 'Pinsao', group: 'Baguio City', lat: 16.4236, lng: 120.575 },
  { id: 'irisan', name: 'Irisan', group: 'Baguio City', lat: 16.4179, lng: 120.5588 },
  { id: 'marcos-highway', name: 'Marcos Highway', group: 'Baguio City', lat: 16.4046, lng: 120.5642 },
  { id: 'bakakeng', name: 'Bakakeng', group: 'Baguio City', lat: 16.3855, lng: 120.5843 },
  { id: 'camp-7', name: 'Camp 7', group: 'Baguio City', lat: 16.3803, lng: 120.5992 },
  { id: 'kias', name: 'Kias', group: 'Baguio City', lat: 16.3736, lng: 120.5901 },
  { id: 'loakan', name: 'Loakan / Green Water', group: 'Baguio City', lat: 16.3752, lng: 120.6122 },

  // -- Around it -----------------------------------------------------------
  //
  // Several of these sit well past the delivery radius, on purpose. Someone in
  // Kapangan picking their own town and being told plainly that nothing
  // reaches them yet is a better answer than a picker that lists only the
  // places we already serve and leaves them guessing which one to claim.
  { id: 'la-trinidad', name: 'La Trinidad', group: 'Benguet', lat: 16.455, lng: 120.588 },
  { id: 'tuba', name: 'Tuba', group: 'Benguet', lat: 16.3417, lng: 120.55 },
  { id: 'itogon', name: 'Itogon', group: 'Benguet', lat: 16.36, lng: 120.68 },
  { id: 'sablan', name: 'Sablan', group: 'Benguet', lat: 16.49, lng: 120.51 },
  { id: 'tublay', name: 'Tublay', group: 'Benguet', lat: 16.53, lng: 120.63 },
  { id: 'kapangan', name: 'Kapangan', group: 'Benguet', lat: 16.6, lng: 120.6 },
  { id: 'atok', name: 'Atok', group: 'Benguet', lat: 16.58, lng: 120.68 },
  { id: 'bokod', name: 'Bokod', group: 'Benguet', lat: 16.5, lng: 120.82 },
  { id: 'kibungan', name: 'Kibungan', group: 'Benguet', lat: 16.7, lng: 120.66 },

  { id: 'naguilian-lu', name: 'Naguilian', group: 'La Union', lat: 16.53, lng: 120.4 },
  { id: 'bauang', name: 'Bauang', group: 'La Union', lat: 16.53, lng: 120.33 },
  { id: 'san-fernando-lu', name: 'San Fernando', group: 'La Union', lat: 16.62, lng: 120.32 },
  { id: 'agoo', name: 'Agoo', group: 'La Union', lat: 16.33, lng: 120.37 },
  { id: 'rosario-lu', name: 'Rosario', group: 'La Union', lat: 16.23, lng: 120.49 },
]

/** In picker order: the city first, then outwards. */
export const serviceAreaGroups: { name: string; areas: ServiceArea[] }[] = [
  { name: 'Baguio City', areas: serviceAreas.filter((area) => area.group === 'Baguio City') },
  { name: 'Benguet', areas: serviceAreas.filter((area) => area.group === 'Benguet') },
  { name: 'La Union', areas: serviceAreas.filter((area) => area.group === 'La Union') },
]

export function findArea(id: string): ServiceArea | null {
  return serviceAreas.find((area) => area.id === id) ?? null
}

function normalize(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()
}

/**
 * Best guess at which area a written address is in.
 *
 * Used to give a saved account address a coordinate it was never asked for:
 * the account form collects a barangay and a city as free text, so "12 Session
 * Road" is all there is to go on. A miss returns null and the address is then
 * treated as an unknown location — which is the right answer, rather than
 * dropping a pin in the middle of town and quoting a distance from it.
 */
export function matchArea(...parts: string[]): ServiceArea | null {
  const haystack = normalize(parts.filter(Boolean).join(' '))
  if (!haystack) return null

  // Longest name first, so "Baguio City Market" is not beaten to it by a
  // shorter area whose name happens to be a substring.
  const candidates = [...serviceAreas].sort((a, b) => b.name.length - a.name.length)

  for (const area of candidates) {
    for (const alias of area.name.split('/')) {
      const needle = normalize(alias)
      if (needle && haystack.includes(needle)) return area
    }
  }

  return null
}
