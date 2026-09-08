import { useCallback } from 'react'
import { ErrorState } from '@/components/common/ErrorState'
import {
  ServiceCard,
  ServiceCardSkeleton,
} from '@/components/services/ServiceCard'
import { TrafficPanel } from '@/components/traffic/TrafficPanel'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { POLL_INTERVAL_MS, usePolling } from '@/hooks/usePolling'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { selectServicesSummary } from '@/store/selectors'
import { fetchServices } from '@/store/servicesSlice'

export function Dashboard() {
  const dispatch = useAppDispatch()
  const services = useAppSelector((state) => state.services.items)
  const status = useAppSelector((state) => state.services.status)
  const error = useAppSelector((state) => state.services.error)
  const ozet = useAppSelector(selectServicesSummary)

  const load = useCallback(() => {
    void dispatch(fetchServices())
  }, [dispatch])

  usePolling(load, POLL_INTERVAL_MS)

  const yukleniyor = status === 'loading' || status === 'idle'

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Servis doluluğu, rota özeti ve güncel trafik durumu.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryTile
            label="Toplam personel"
            value={ozet.toplamPersonel}
            loading={yukleniyor}
          />
          <SummaryTile
            label="Dolu servis"
            value={`${ozet.doluServis} / ${services.length || 10}`}
            loading={yukleniyor}
          />
          <SummaryTile
            label="Boş servis"
            value={ozet.bosServis}
            loading={yukleniyor}
          />
          <SummaryTile
            label="Ortalama doluluk"
            value={`%${ozet.ortalamaDoluluk}`}
            loading={yukleniyor}
            hint={
              ozet.minAltiServis > 0
                ? `${ozet.minAltiServis} servis min. kapasitenin altında`
                : undefined
            }
          />
        </div>

        <TrafficPanel className="lg:row-span-2" />
      </div>

      {status === 'failed' ? (
        <ErrorState
          title="Servisler yüklenemedi"
          message={error}
          onRetry={load}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          {yukleniyor && services.length === 0
            ? Array.from({ length: 10 }, (_, i) => (
                <ServiceCardSkeleton key={i} />
              ))
            : services.map((service) => (
                <ServiceCard key={service.id} service={service} />
              ))}
        </div>
      )}
    </div>
  )
}

function SummaryTile({
  label,
  value,
  hint,
  loading,
}: {
  label: string
  value: string | number
  hint?: string
  loading?: boolean
}) {
  return (
    <Card>
      <CardContent className="space-y-1 p-4">
        <p className="text-xs tracking-wide text-muted-foreground uppercase">
          {label}
        </p>
        {loading ? (
          <Skeleton className="h-7 w-16" />
        ) : (
          <p className="text-2xl font-semibold tabular-nums">{value}</p>
        )}
        {hint ? <p className="text-xs text-capacity-under">{hint}</p> : null}
      </CardContent>
    </Card>
  )
}
