import { EyeIcon, EyeOffIcon } from 'lucide-react'
import { RouteMap } from '@/components/RouteMap'
import { ErrorState } from '@/components/states'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useServiceRoutes } from '@/hooks/useServiceRoute'
import { useServices } from '@/hooks/useServices'
import { serviceColor } from '@/lib/serviceColors'
import { cn, formatKm } from '@/lib/utils'
import { useUiStore } from '@/store/uiStore'

export function HaritaGenel() {
  const services = useServices()
  const servisIdleri = services.data?.map((s) => s.id) ?? []
  const routes = useServiceRoutes(servisIdleri)

  const hiddenServiceIds = useUiStore((s) => s.hiddenServiceIds)
  const highlightedServiceId = useUiStore((s) => s.highlightedServiceId)
  const toggleServiceVisibility = useUiStore((s) => s.toggleServiceVisibility)
  const setAllServicesVisible = useUiStore((s) => s.setAllServicesVisible)
  const setHighlightedServiceId = useUiStore((s) => s.setHighlightedServiceId)

  const gorunurRotalar = routes.routes.filter(
    (r) => !hiddenServiceIds.includes(r.servisId),
  )

  if (services.isError) {
    return (
      <ErrorState
        title="Servisler yüklenemedi"
        error={services.error}
        onRetry={() => void services.refetch()}
      />
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Genel Harita</h1>
          <p className="text-sm text-muted-foreground">
            Tüm servis rotaları tek haritada. Legend'den bir servise tıklayarak
            vurgulayabilir, göz simgesiyle katmanı kapatabilirsin.
          </p>
        </div>
        {hiddenServiceIds.length > 0 || highlightedServiceId !== null ? (
          <Button variant="outline" size="sm" onClick={setAllServicesVisible}>
            Tümünü göster
          </Button>
        ) : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_280px]">
        {services.isPending || routes.isPending ? (
          <Skeleton className="h-[640px] w-full rounded-xl" />
        ) : routes.isError ? (
          <ErrorState
            title="Rotalar yüklenemedi"
            error={routes.error}
            onRetry={routes.refetch}
          />
        ) : (
          <RouteMap
            className="h-[640px]"
            routes={gorunurRotalar}
            highlightedServiceId={highlightedServiceId}
            showStopNumbers={highlightedServiceId !== null}
          />
        )}

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Servisler</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1">
            {services.isPending
              ? Array.from({ length: 10 }, (_, i) => (
                  <Skeleton key={i} className="h-9 w-full" />
                ))
              : services.data.map((service) => {
                  const gizli = hiddenServiceIds.includes(service.id)
                  const vurgulu = highlightedServiceId === service.id
                  const rota = routes.routes.find(
                    (r) => r.servisId === service.id,
                  )
                  const toplamKm = rota
                    ? rota.stops.reduce((sum, s) => sum + s.oncekiDuraktanKm, 0)
                    : service.toplamKm

                  return (
                    <div
                      key={service.id}
                      className={cn(
                        'flex items-center gap-2 rounded-md px-2 py-1.5 transition-colors',
                        vurgulu && 'bg-accent',
                        gizli && 'opacity-50',
                      )}
                    >
                      <button
                        type="button"
                        className="flex flex-1 items-center gap-2 text-left text-sm"
                        onClick={() =>
                          setHighlightedServiceId(vurgulu ? null : service.id)
                        }
                        aria-pressed={vurgulu}
                      >
                        <span
                          className="size-3 shrink-0 rounded-full"
                          style={{ backgroundColor: serviceColor(service.id) }}
                        />
                        <span className="font-medium">Servis-{service.id}</span>
                        <span className="ml-auto text-xs text-muted-foreground tabular-nums">
                          {service.kisiSayisi} kişi · {formatKm(toplamKm)}
                        </span>
                      </button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="size-7"
                        aria-label={
                          gizli
                            ? `Servis-${service.id} katmanını aç`
                            : `Servis-${service.id} katmanını kapat`
                        }
                        onClick={() => toggleServiceVisibility(service.id)}
                      >
                        {gizli ? (
                          <EyeOffIcon className="size-3.5" />
                        ) : (
                          <EyeIcon className="size-3.5" />
                        )}
                      </Button>
                    </div>
                  )
                })}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
