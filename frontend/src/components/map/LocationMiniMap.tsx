import 'leaflet/dist/leaflet.css'
import L from 'leaflet'
import { MapContainer, Marker, TileLayer } from 'react-leaflet'
import { cn } from '@/lib/utils'

interface LocationMiniMapProps {
  lat: number
  lon: number
  /** İşaretçinin rengi — kişinin servisinin rengi verilir. */
  color?: string
  /** Mahalle ölçeği için 15-16 uygun. */
  zoom?: number
  className?: string
}

/**
 * Tek bir adresi mahalle ölçeğinde gösteren küçük harita.
 *
 * <p>
 * {@code RouteMap} yerine ayrı bir bileşen: o, bütün durakları çerçeveye
 * sığdırmak (fitBounds) üzerine kurulu ve tek noktada mantıksız bir zoom
 * üretiyor. Buradaki amaç rota değil konum göstermek, o yüzden çerçeve sabit ve
 * etkileşim kapalı — kart içinde kaydırma hareketini çalmasın.
 */
export function LocationMiniMap({
  lat,
  lon,
  color = '#2563eb',
  zoom = 15,
  className,
}: LocationMiniMapProps) {
  return (
    <div
      className={cn(
        'overflow-hidden rounded-lg border border-border',
        className,
      )}
    >
      <MapContainer
        // Koordinat değişince Leaflet örneğini baştan kur; center prop'u
        // ilk render'dan sonra dikkate alınmıyor.
        key={`${lat},${lon}`}
        center={[lat, lon]}
        zoom={zoom}
        className="h-full w-full"
        zoomControl={false}
        scrollWheelZoom={false}
        dragging={false}
        doubleClickZoom={false}
        touchZoom={false}
        keyboard={false}
        attributionControl={false}
      >
        <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <Marker position={[lat, lon]} icon={pinIcon(color)} />
      </MapContainer>
    </div>
  )
}

function pinIcon(color: string) {
  return L.divIcon({
    className: '',
    html:
      `<span style="display:block;width:16px;height:16px;border-radius:9999px;` +
      `background:${color};border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)"></span>`,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  })
}
