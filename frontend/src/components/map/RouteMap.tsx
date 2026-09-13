import 'leaflet/dist/leaflet.css'
import L from 'leaflet'
import { useEffect, useMemo } from 'react'
import {
  MapContainer,
  Marker,
  Polyline,
  TileLayer,
  Tooltip,
  useMap,
} from 'react-leaflet'
import { serviceColor } from '@/lib/serviceColors'
import { cn, formatKm } from '@/lib/utils'
import type { LatLon, RouteStop } from '@/types'

export interface MapRoute {
  servisId: number
  stops: RouteStop[]
  /**
   * Yolu takip eden çizgi. Backend'de OSRM kapalıysa null gelir; o durumda
   * duraklar düz çizgiyle birleştirilir (kuş uçuşu görünüm).
   */
  geometry: LatLon[] | null
}

interface RouteMapProps {
  routes: MapRoute[]
  /** Verilirse bu servis vurgulanır, diğerleri soluklaştırılır. */
  highlightedServiceId?: number | null
  /** Tek rota görünümünde durak numaralarını marker üzerinde göster. */
  showStopNumbers?: boolean
  className?: string
}

const ISTANBUL_MERKEZ: [number, number] = [41.03, 28.95]

/**
 * Harita soyutlaması. Dışarıya sadece alan tipleri (`RouteStop`, `LatLon`) ile
 * konuşur; Leaflet'e özgü hiçbir tip prop'lara sızmaz. Mapbox GL JS'e geçilmek
 * istendiğinde yalnızca bu dosyanın gövdesi değişir.
 */
export function RouteMap({
  routes,
  highlightedServiceId = null,
  showStopNumbers = true,
  className,
}: RouteMapProps) {
  const bounds = useMemo(() => {
    const noktalar = routes.flatMap((r) =>
      r.stops.map((s) => [s.lat, s.lon] as [number, number]),
    )
    return noktalar.length > 0 ? noktalar : null
  }, [routes])

  return (
    <div
      className={cn('overflow-hidden rounded-xl border border-border', className)}
    >
      <MapContainer
        center={ISTANBUL_MERKEZ}
        zoom={10}
        scrollWheelZoom
        className="h-full w-full"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <FitBounds points={bounds} />

        {routes.map((route) => (
          <ServiceRouteLayer
            key={route.servisId}
            route={route}
            dimmed={
              highlightedServiceId != null &&
              highlightedServiceId !== route.servisId
            }
            showStopNumbers={showStopNumbers}
          />
        ))}
      </MapContainer>
    </div>
  )
}

function ServiceRouteLayer({
  route,
  dimmed,
  showStopNumbers,
}: {
  route: MapRoute
  dimmed: boolean
  showStopNumbers: boolean
}) {
  const renk = serviceColor(route.servisId)

  // OSRM geometrisi varsa yolu takip eder; yoksa duraklar düz çizgiyle bağlanır.
  const cizgi: Array<[number, number]> =
    route.geometry && route.geometry.length > 1
      ? route.geometry
      : route.stops.map((s) => [s.lat, s.lon])

  return (
    <>
      <Polyline
        positions={cizgi}
        pathOptions={{
          color: renk,
          weight: dimmed ? 2 : 4,
          opacity: dimmed ? 0.25 : 0.9,
        }}
      />
      {route.stops.map((stop) => (
        <Marker
          key={`${stop.servisId}-${stop.durakNo}-${stop.employeeId ?? 'depo'}`}
          position={[stop.lat, stop.lon]}
          icon={stopIcon(stop, renk, dimmed, showStopNumbers)}
          opacity={dimmed ? 0.35 : 1}
          zIndexOffset={dimmed ? 0 : 400}
        >
          <Tooltip direction="top" offset={[0, -14]}>
            <span className="font-medium">{stop.adSoyad}</span>
            <br />
            <span className="text-xs">
              Servis-{stop.servisId} · Durak {stop.durakNo} ·{' '}
              {formatKm(stop.oncekiDuraktanKm)}
            </span>
          </Tooltip>
        </Marker>
      ))}
    </>
  )
}

/** Depo (kalkış/ofis) durakları kare, personel durakları numaralı daire. */
function stopIcon(
  stop: RouteStop,
  renk: string,
  dimmed: boolean,
  showStopNumbers: boolean,
) {
  const depo = stop.employeeId === null
  const etiket = depo
    ? stop.durakNo === 0
      ? 'K'
      : 'O'
    : showStopNumbers
      ? String(stop.durakNo)
      : ''

  const boyut = depo ? 24 : dimmed ? 14 : 26
  const stil = [
    `background:${depo ? '#111827' : renk}`,
    `width:${boyut}px`,
    `height:${boyut}px`,
    depo ? 'border-radius:6px' : '',
  ]
    .filter(Boolean)
    .join(';')

  return L.divIcon({
    className: '',
    html: `<span class="stop-marker" style="${stil}">${etiket}</span>`,
    iconSize: [boyut, boyut],
    iconAnchor: [boyut / 2, boyut / 2],
  })
}

function FitBounds({ points }: { points: Array<[number, number]> | null }) {
  const map = useMap()
  const anahtar = points ? `${points.length}:${points[0]?.join(',')}` : ''

  useEffect(() => {
    if (!points || points.length === 0) return
    map.fitBounds(L.latLngBounds(points), { padding: [40, 40] })
    // Sadece durak kümesi değiştiğinde yeniden çerçevele; kullanıcının
    // kaydırma/yakınlaştırma hareketini her poll'da bozmamak için.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anahtar, map])

  return null
}
