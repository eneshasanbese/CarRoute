import { EyeIcon, EyeOffIcon } from 'lucide-react'
import { useCallback } from 'react'
import { ErrorState } from '@/components/common/ErrorState'
import { RouteMap } from '@/components/map/RouteMap'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { POLL_INTERVAL_MS, usePolling } from '@/hooks/usePolling'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { fetchServiceRoute, fetchServices } from '@/store/servicesSlice'
import {
  setHighlightedService,
  showAllServices,
  toggleServiceVisibility,
} from '@/store/uiSlice'
import { serviceColor } from '@/lib/serviceColors'
import { cn, formatKm } from '@/lib/utils'

export function HaritaGenel() {
  const dispatch = useAppDispatch()
  const services = useAppSelector((state) => state.services.items)
  const status = useAppSelector((state) => state.services.status)
  const error = useAppSelector((state) => state.services.error)
  const routes = useAppSelector((state) => state.services.routes)
  const { hiddenServiceIds, highlightedServiceId } = useAppSelector(
    (state) => state.ui,
  )

  const load = useCallback(() => {
    void dispatch(fetchServices())
      .unwrap()
      .then((liste) => {
        liste.forEach((service) => {
          void dispatch(fetchServiceRoute(service.id))
        })
      })
      .catch(() => {
        // Hata durumu servicesSlice'ta tutuluyor, ayrıca ele almaya gerek yok.
      })
  }, [dispatch])

  usePolling(load, POLL_INTERVAL_MS)

  const gorunurRotalar = services
    .filter((service) => !hiddenServiceIds.includes(service.id))
    .flatMap((service) => {
      const rota = routes[service.id]
      return rota
        ? [{ servisId: service.id, stops: rota.stops, geometry: rota.geometry }]
        : []
    })

  if (status === 'failed') {
    return (
      <ErrorState
        title="Servisler yüklenemedi"
        message={error}
        onRetry={load}
      />
    )
  }

  const haritaHazir = services.length > 0 && gorunurRotalar.length > 0
  const filtreVar = hiddenServiceIds.length > 0 || highlightedServiceId !== null

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
        {filtreVar ? (
          <Button
            variant="outline"
            size="sm"
            onClick={() => dispatch(showAllServices())}
          >
            Tümünü göster
          </Button>
        ) : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_280px]">
        {haritaHazir ? (
          <RouteMap
            className="h-[640px]"
            routes={gorunurRotalar}
            highlightedServiceId={highlightedServiceId}
            showStopNumbers={highlightedServiceId !== null}
          />
        ) : (
          <Skeleton className="h-[640px] w-full rounded-xl" />
        )}

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Servisler</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1">
            {services.length === 0
              ? Array.from({ length: 10 }, (_, i) => (
                  <Skeleton key={i} className="h-9 w-full" />
                ))
              : services.map((service) => {
                  const gizli = hiddenServiceIds.includes(service.id)
                  const vurgulu = highlightedServiceId === service.id

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
                        aria-pressed={vurgulu}
                        onClick={() =>
                          dispatch(
                            setHighlightedService(vurgulu ? null : service.id),
                          )
                        }
                      >
                        <span
                          className="size-3 shrink-0 rounded-full"
                          style={{ backgroundColor: serviceColor(service.id) }}
                        />
                        <span className="font-medium">Servis-{service.id}</span>
                        <span className="ml-auto text-xs text-muted-foreground tabular-nums">
                          {service.kisiSayisi} kişi ·{' '}
                          {formatKm(service.toplamKm)}
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
                        onClick={() =>
                          dispatch(toggleServiceVisibility(service.id))
                        }
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
