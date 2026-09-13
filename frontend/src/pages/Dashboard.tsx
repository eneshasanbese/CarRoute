import { ArrowDownUpIcon } from 'lucide-react'
import { useCallback, useMemo, useState } from 'react'
import { ErrorState } from '@/components/common/ErrorState'
import {
  ServiceCard,
  ServiceCardSkeleton,
} from '@/components/services/ServiceCard'
import { ReassignButton } from '@/components/services/ReassignButton'
import { SeferToggle } from '@/components/services/SeferToggle'
import { TrafficPanel } from '@/components/traffic/TrafficPanel'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { POLL_INTERVAL_MS, usePolling } from '@/hooks/usePolling'
import {
  SERVICE_SORT_OPTIONS,
  type ServiceSortKey,
  sortServices,
} from '@/lib/serviceSort'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { selectServicesSummary } from '@/store/selectors'
import { fetchServices, setSefer } from '@/store/servicesSlice'

export function Dashboard() {
  const dispatch = useAppDispatch()
  const services = useAppSelector((state) => state.services.items)
  const status = useAppSelector((state) => state.services.status)
  const error = useAppSelector((state) => state.services.error)
  const sefer = useAppSelector((state) => state.services.sefer)
  const yenidenDagitiliyor = useAppSelector(
    (state) => state.services.reassigning,
  )
  const ozet = useAppSelector(selectServicesSummary)

  const [siralama, setSiralama] = useState<ServiceSortKey>('servis')

  const load = useCallback(() => {
    // Dağıtım sürerken poll etmiyoruz: yolda olan istek eski tabloyu getirip
    // yeni sonucun üstüne yazabilir.
    if (yenidenDagitiliyor) return
    void dispatch(fetchServices())
  }, [dispatch, yenidenDagitiliyor])

  usePolling(load, POLL_INTERVAL_MS)

  const siraliServisler = useMemo(
    () => sortServices(services, siralama),
    [services, siralama],
  )

  const ihlalSayisi = services.filter((s) => s.kuralIhlali).length
  const ilkAzamiSure = services[0]?.azamiYolculukDk ?? 90

  const yukleniyor = status === 'loading' || status === 'idle'

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Dashboard</h1>
          <p className="text-sm text-muted-foreground">
            Servis doluluğu, rota özeti ve güncel trafik durumu.
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/*
            Kartlardaki km, süre ve saatlerin hepsi sefere bağlı; akşam turu
            sabahın tersi değil, bağımsız hesaplanıyor. Seçim değişince liste
            backend'den yeniden çekiliyor.
          */}
          <SeferToggle
            value={sefer}
            onChange={(secim) => {
              dispatch(setSefer(secim))
              void dispatch(fetchServices(secim))
            }}
          />
          <ReassignButton />
        </div>
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
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-sm font-medium text-muted-foreground">
              Servisler
              {ihlalSayisi > 0 ? (
                <span className="ml-2 text-destructive">
                  · {ihlalSayisi} servis {ilkAzamiSure} dakikayı aşıyor
                </span>
              ) : null}
            </h2>

            <div className="flex items-center gap-2">
              <ArrowDownUpIcon className="size-4 text-muted-foreground" />
              <Select
                value={siralama}
                onValueChange={(value) =>
                  setSiralama(value as ServiceSortKey)
                }
              >
                <SelectTrigger className="w-auto min-w-52">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SERVICE_SORT_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
            {yukleniyor && services.length === 0
              ? Array.from({ length: 10 }, (_, i) => (
                  <ServiceCardSkeleton key={i} />
                ))
              : siraliServisler.map((service) => (
                  <ServiceCard key={service.id} service={service} />
                ))}
          </div>
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
