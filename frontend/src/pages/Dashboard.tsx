import { ServiceCard, ServiceCardSkeleton } from '@/components/ServiceCard'
import { ErrorState } from '@/components/states'
import { TrafficPanel } from '@/components/TrafficPanel'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { summarizeServices, useServices } from '@/hooks/useServices'

export function Dashboard() {
  const { data, isPending, isError, error, refetch } = useServices()
  const ozet = summarizeServices(data ?? [])

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
            loading={isPending}
          />
          <SummaryTile
            label="Dolu servis"
            value={`${ozet.doluServis} / ${data?.length ?? 10}`}
            loading={isPending}
          />
          <SummaryTile
            label="Boş servis"
            value={ozet.bosServis}
            loading={isPending}
          />
          <SummaryTile
            label="Ortalama doluluk"
            value={`%${ozet.ortalamaDoluluk}`}
            loading={isPending}
            hint={
              ozet.minAltiServis > 0
                ? `${ozet.minAltiServis} servis min. kapasitenin altında`
                : undefined
            }
          />
        </div>

        <TrafficPanel className="lg:row-span-2" />
      </div>

      {isError ? (
        <ErrorState
          title="Servisler yüklenemedi"
          error={error}
          onRetry={() => void refetch()}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          {isPending
            ? Array.from({ length: 10 }, (_, i) => <ServiceCardSkeleton key={i} />)
            : data.map((service) => (
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
        {hint ? (
          <p className="text-xs text-capacity-under">{hint}</p>
        ) : null}
      </CardContent>
    </Card>
  )
}
