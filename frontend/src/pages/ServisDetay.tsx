import {
  ArrowLeftIcon,
  ClockIcon,
  RouteIcon,
  TimerIcon,
  TriangleAlertIcon,
  UsersIcon,
} from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { CapacityGauge } from '@/components/CapacityGauge'
import { RouteMap } from '@/components/RouteMap'
import { RouteStopList } from '@/components/RouteStopList'
import { ErrorState } from '@/components/states'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useServiceRoute } from '@/hooks/useServiceRoute'
import { useService } from '@/hooks/useServices'
import { CAPACITY_META, capacityLevel } from '@/lib/capacity'
import { serviceColor } from '@/lib/serviceColors'
import { cn, formatKm, formatSure } from '@/lib/utils'

export function ServisDetay() {
  const params = useParams<{ id: string }>()
  const servisId = Number(params.id)
  const gecerliId = Number.isInteger(servisId) && servisId > 0

  const { service, isPending: servisYukleniyor, isError: servisHatasi, error: servisHata, refetch: servisTekrar } =
    useService(servisId)
  const route = useServiceRoute(servisId, gecerliId)

  if (!gecerliId) {
    return (
      <ErrorState
        title="Geçersiz servis"
        error={new Error(`"${params.id}" bir servis numarası değil.`)}
      />
    )
  }

  const level = service ? capacityLevel(service) : null
  const meta = level ? CAPACITY_META[level] : null

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
        {meta && level === 'under' ? (
          <Badge variant="outline" className={meta.badge}>
            <TriangleAlertIcon className="size-3" />
            Min. kapasite altında
          </Badge>
        ) : meta ? (
          <Badge variant="outline" className={meta.badge}>
            {meta.label}
          </Badge>
        ) : null}
      </div>

      {servisHatasi ? (
        <ErrorState
          title="Servis bilgisi yüklenemedi"
          error={servisHata}
          onRetry={() => void servisTekrar()}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            icon={<UsersIcon className="size-4" />}
            label="Kapasite"
            loading={servisYukleniyor}
          >
            {service ? (
              <CapacityGauge service={service} />
            ) : null}
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
            {route.isError ? (
              <ErrorState
                title="Rota yüklenemedi"
                error={route.error}
                onRetry={() => void route.refetch()}
                className="p-4"
              />
            ) : route.isPending ? (
              <div className="space-y-3">
                {Array.from({ length: 6 }, (_, i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : (
              <RouteStopList stops={route.data} />
            )}
          </CardContent>
        </Card>

        {route.isPending ? (
          <Skeleton className="h-[560px] w-full rounded-xl" />
        ) : route.isError ? (
          <ErrorState
            title="Harita yüklenemedi"
            error={route.error}
            onRetry={() => void route.refetch()}
          />
        ) : (
          <RouteMap
            className="h-[560px]"
            routes={[{ servisId, stops: route.data }]}
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
      <CardContent className={cn('space-y-2 p-4')}>
        <p className="flex items-center gap-1.5 text-xs tracking-wide text-muted-foreground uppercase">
          {icon}
          {label}
        </p>
        {loading ? <Skeleton className="h-8 w-24" /> : children}
      </CardContent>
    </Card>
  )
}
