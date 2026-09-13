import { MoonIcon, SunriseIcon } from 'lucide-react'
import { useCallback, useEffect } from 'react'
import { ErrorState } from '@/components/common/ErrorState'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { fetchTrafficSnapshot } from '@/store/trafficSlice'
import { cn } from '@/lib/utils'
import type { TrafficBucket } from '@/types'

const BUCKET_META: Record<
  TrafficBucket,
  { label: string; icon: typeof SunriseIcon }
> = {
  sabah: { label: 'Sabah', icon: SunriseIcon },
  aksam: { label: 'Akşam', icon: MoonIcon },
}

/**
 * Eşikler gerçek veriden: Ocak 2025'te şehir geneli yavaşlama sabah ~%9, akşam
 * ~%19. Önceki eşikler (%55 / %75) elle konmuş 80 km/sa referansına göreydi; bu
 * ölçekte çubuk hep yeşil kalırdı.
 */
function yogunlukRengi(yuzde: number) {
  if (yuzde >= 20) return 'bg-capacity-full'
  if (yuzde >= 10) return 'bg-capacity-warn'
  return 'bg-capacity-ok'
}

/**
 * Zirve saatlerin trafik özeti.
 *
 * Saat aralıkları ve kaynak backend'den geliyor: önceden burada elle yazılıydı
 * ("07:00 – 09:00") ve veriyle uyuşmuyordu. Veri statik (Ocak 2025) olduğu için
 * "son güncelleme" gösterilmiyor ve periyodik tazeleme yapılmıyor.
 */
export function TrafficPanel({ className }: { className?: string }) {
  const dispatch = useAppDispatch()
  const snapshots = useAppSelector((state) => state.traffic.snapshots)
  const status = useAppSelector((state) => state.traffic.status)
  const error = useAppSelector((state) => state.traffic.error)

  const load = useCallback(() => {
    void dispatch(fetchTrafficSnapshot('sabah'))
    void dispatch(fetchTrafficSnapshot('aksam'))
  }, [dispatch])

  useEffect(() => {
    load()
  }, [load])

  const kaynak = snapshots.sabah?.kaynak ?? snapshots.aksam?.kaynak

  return (
    <Card className={className}>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Trafik Durumu</CardTitle>
        <p className="text-xs text-muted-foreground">
          Zirve saatlerde gece serbest akışına göre yavaşlama
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        {status === 'failed' ? (
          <ErrorState
            title="Trafik verisi alınamadı"
            message={error}
            onRetry={load}
            className="p-4"
          />
        ) : (
          (['sabah', 'aksam'] as const).map((bucket) => (
            <BucketRow
              key={bucket}
              bucket={bucket}
              yuzde={snapshots[bucket]?.yogunlukYuzde}
              saatAraligi={snapshots[bucket]?.saatAraligi}
            />
          ))
        )}
        {kaynak ? (
          <p className="text-xs text-muted-foreground">Kaynak: {kaynak}</p>
        ) : null}
      </CardContent>
    </Card>
  )
}

function BucketRow({
  bucket,
  yuzde,
  saatAraligi,
}: {
  bucket: TrafficBucket
  yuzde?: number
  saatAraligi?: string
}) {
  const meta = BUCKET_META[bucket]
  const Icon = meta.icon

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-sm">
        <span className="flex items-center gap-1.5">
          <Icon className="size-4 text-muted-foreground" />
          <span className="font-medium">{meta.label}</span>
          {saatAraligi ? (
            <span className="text-xs text-muted-foreground">{saatAraligi}</span>
          ) : null}
        </span>
        {yuzde == null ? (
          <Skeleton className="h-4 w-10" />
        ) : (
          <span className="font-semibold tabular-nums">%{yuzde}</span>
        )}
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
        {yuzde == null ? null : (
          <div
            className={cn(
              'h-full rounded-full transition-all',
              yogunlukRengi(yuzde),
            )}
            style={{ width: `${yuzde}%` }}
          />
        )}
      </div>
    </div>
  )
}
