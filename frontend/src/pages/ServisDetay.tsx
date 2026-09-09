import {
  ArrowLeftIcon,
  ClockIcon,
  RouteIcon,
  TimerIcon,
  TriangleAlertIcon,
  UsersIcon,
} from 'lucide-react'
import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ErrorState } from '@/components/common/ErrorState'
import { RouteMap } from '@/components/map/RouteMap'
import { RouteStopList } from '@/components/map/RouteStopList'
import { CapacityGauge } from '@/components/services/CapacityGauge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { POLL_INTERVAL_MS, usePolling } from '@/hooks/usePolling'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import {
  selectRoute,
  selectRouteError,
  selectRouteStatus,
  selectServiceById,
} from '@/store/selectors'
import { fetchServiceRoute, fetchServices } from '@/store/servicesSlice'
import { CAPACITY_META, capacityLevel } from '@/lib/capacity'
import { serviceColor } from '@/lib/serviceColors'
import { formatKm, formatSure } from '@/lib/utils'

export function ServisDetay() {
  const params = useParams<{ id: string }>()
  const servisId = Number(params.id)
  const gecerliId = Number.isInteger(servisId) && servisId > 0

  const dispatch = useAppDispatch()
  const service = useAppSelector(selectServiceById(servisId))
  const servicesStatus = useAppSelector((state) => state.services.status)
  const servicesError = useAppSelector((state) => state.services.error)
  const route = useAppSelector(selectRoute(servisId))
  const routeStatus = useAppSelector(selectRouteStatus(servisId))
  const routeError = useAppSelector(selectRouteError(servisId))

  const load = useCallback(() => {
    if (!gecerliId) return
    void dispatch(fetchServices())
    void dispatch(fetchServiceRoute(servisId))
  }, [dispatch, servisId, gecerliId])

  usePolling(load, POLL_INTERVAL_MS)

  if (!gecerliId) {
    return (
      <ErrorState
        title="Geçersiz servis"
        message={`"${params.id}" bir servis numarası değil.`}
      />
    )
  }

  const level = service ? capacityLevel(service) : null
  const meta = level ? CAPACITY_META[level] : null
  const servisYukleniyor = !service && servicesStatus !== 'failed'
  const rotaYukleniyor = !route && routeStatus !== 'failed'

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link to="/">
            <ArrowLeftIcon />
            Dashboard
          </Link>
        </Button>
        <div className="flex items-center gap-2">
          <span
            className="size-3 rounded-full"
            style={{ backgroundColor: serviceColor(servisId) }}
          />
          <h1 className="text-2xl font-semibold">Servis-{servisId}</h1>
        </div>
        {meta ? (
          <Badge variant="outline" className={meta.badge}>
            {level === 'under' ? <TriangleAlertIcon className="size-3" /> : null}
            {meta.label}
          </Badge>
        ) : null}
      </div>

      {servicesStatus === 'failed' ? (
        <ErrorState
          title="Servis bilgisi yüklenemedi"
          message={servicesError}
          onRetry={load}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            icon={<UsersIcon className="size-4" />}
            label="Kapasite"
            loading={servisYukleniyor}
          >
            {service ? <CapacityGauge service={service} /> : null}
          </StatCard>
          <StatCard
            icon={<RouteIcon className="size-4" />}
            label="Toplam mesafe"
            loading={servisYukleniyor}
          >
            <p className="text-2xl font-semibold tabular-nums">
              {service ? formatKm(service.toplamKm) : '—'}
            </p>
          </StatCard>
          <StatCard
            icon={<TimerIcon className="size-4" />}
            label="Tahmini süre"
            loading={servisYukleniyor}
          >
            <p className="text-2xl font-semibold tabular-nums">
              {service ? formatSure(service.tahminiSureDk) : '—'}
            </p>
          </StatCard>
          <StatCard
            icon={<ClockIcon className="size-4" />}
            label="Önerilen kalkış"
            loading={servisYukleniyor}
          >
            <p className="text-2xl font-semibold tabular-nums">
              {service?.kalkisSaati ?? '—'}
            </p>
          </StatCard>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[320px_1fr]">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Durak sırası</CardTitle>
            <p className="text-xs text-muted-foreground">
              Sıralama algoritma tarafından belirlenir; elle değiştirilemez.
            </p>
          </CardHeader>
          <CardContent>
            {routeStatus === 'failed' ? (
              <ErrorState
                title="Rota yüklenemedi"
                message={routeError}
                onRetry={load}
                className="p-4"
              />
            ) : rotaYukleniyor ? (
              <div className="space-y-3">
                {Array.from({ length: 6 }, (_, i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : (
              <RouteStopList stops={route?.stops ?? []} />
            )}
          </CardContent>
        </Card>

        {routeStatus === 'failed' ? (
          <ErrorState
            title="Harita yüklenemedi"
            message={routeError}
            onRetry={load}
          />
        ) : rotaYukleniyor ? (
          <Skeleton className="h-[560px] w-full rounded-xl" />
        ) : (
          <RouteMap
            className="h-[560px]"
            routes={[
              {
                servisId,
                stops: route?.stops ?? [],
                geometry: route?.geometry ?? null,
              },
            ]}
          />
        )}
      </div>
    </div>
  )
}

function StatCard({
  icon,
  label,
  loading,
  children,
}: {
  icon: React.ReactNode
  label: string
  loading?: boolean
  children: React.ReactNode
}) {
  return (
    <Card>
      <CardContent className="space-y-2 p-4">
        <p className="flex items-center gap-1.5 text-xs tracking-wide text-muted-foreground uppercase">
          {icon}
          {label}
        </p>
        {loading ? <Skeleton className="h-8 w-24" /> : children}
      </CardContent>
    </Card>
  )
}
