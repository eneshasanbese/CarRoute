import type { GeocodeResult } from '@/types'

/**
 * Adres autocomplete sarmalayıcısı.
 *
 * Varsayılan sağlayıcı Nominatim (OpenStreetMap) — API key gerektirmez.
 * Google Places'e geçilmek istenirse sadece `searchAddress` gövdesi değişir;
 * çağıran taraf `GeocodeResult` sözleşmesini görür.
 *
 * Kullanıcı arayüzde koordinat görmez: seçilen sonucun lat/lon değeri forma
 * gizlice yazılır ve backend'e öyle gider.
 */

const PROVIDER = import.meta.env.VITE_GEOCODING_PROVIDER ?? 'nominatim'
const NOMINATIM_URL = 'https://nominatim.openstreetmap.org/search'

interface NominatimItem {
  display_name: string
  lat: string
  lon: string
  address?: Record<string, string | undefined>
}

function extractIlce(address: Record<string, string | undefined> = {}) {
  return (
    address.town ??
    address.city_district ??
    address.district ??
    address.county ??
    address.suburb ??
    address.province ??
    address.city ??
    ''
  )
}

export async function searchAddress(
  query: string,
  signal?: AbortSignal,
): Promise<GeocodeResult[]> {
  const trimmed = query.trim()
  if (PROVIDER === 'none' || trimmed.length < 3) return []

  const params = new URLSearchParams({
    q: trimmed,
    format: 'jsonv2',
    addressdetails: '1',
    limit: '6',
    countrycodes: 'tr',
    'accept-language': 'tr',
  })

  const response = await fetch(`${NOMINATIM_URL}?${params}`, { signal })
  if (!response.ok) throw new Error('Adres servisi yanıt vermedi.')

  const items = (await response.json()) as NominatimItem[]
  return items.map((item) => ({
    label: item.display_name,
    ilce: extractIlce(item.address),
    lat: Number(item.lat),
    lon: Number(item.lon),
  }))
}

export const geocodingEnabled = PROVIDER !== 'none'
