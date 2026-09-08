import { MoonIcon, SunriseIcon } from 'lucide-react'
import { useCallback } from 'react'
import { ErrorState } from '@/components/common/ErrorState'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { usePolling } from '@/hooks/usePolling'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { fetchTrafficSnapshot } from '@/store/trafficSlice'
import { cn } from '@/lib/utils'
import type { TrafficBucket } from '@/types'

/** Trafik özeti dakikalık değişir; rota verisi kadar sık çekmeye gerek yok. */
const TRAFFIC_POLL_MS = 60_000

const BUCKET_META: Record<
  TrafficBucket,
  { label: string; aralik: string; icon: typeof SunriseIcon }
> = {
  sabah: { label: 'Sabah', aralik: '07:00 – 09:00', icon: SunriseIcon },
  aksam: { label: 'Akşam', aralik: '17:00 sonrası', icon: MoonIcon },
}

function yogunlukRengi(yuzde: number) {
  if (yuzde >= 75) return 'bg-capacity-full'
  if (yuzde >= 55) return 'bg-capacity-warn'
  return 'bg-capacity-ok'
}

export function TrafficPanel({ className }: { className?: string }) {
  const dispatch = useAppDispatch()
  const snapshots = useAppSelector((state) => state.traffic.snapshots)
  const status = useAppSelector((state) => state.traffic.status)
  const error = useAppSelector((state) => state.traffic.error)

  const load = useCallback(() => {
    void dispatch(fetchTrafficSnapshot('sabah'))
    void dispatch(fetchTrafficSnapshot('aksam'))
  }, [dispatch])

  usePolling(load, TRAFFIC_POLL_MS)

  const guncelleme = guncellemeMetni(snapshots.sabah?.guncellemeZamani)

  return (
    <Card className={className}>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Trafik Durumu</CardTitle>
        {guncelleme ? (
          <p className="text-xs text-muted-foreground">
            Son güncelleme {guncelleme}
          </p>
        ) : null}
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
            />
          ))
        )}
      </CardContent>
    </Card>
  )
}

function BucketRow({
  bucket,
  yuzde,
}: {
  bucket: TrafficBucket
  yuzde?: number
}) {
  const meta = BUCKET_META[bucket]
  const Icon = meta.icon

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-sm">
        <span className="flex items-center gap-1.5">
          <Icon className="size-4 text-muted-foreground" />
          <span className="font-medium">{meta.label}</span>
          <span className="text-xs text-muted-foreground">{meta.aralik}</span>
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

function guncellemeMetni(isoDate?: string) {
  if (!isoDate) return null
  const tarih = new Date(isoDate)
  if (Number.isNaN(tarih.getTime())) return null
  return tarih.toLocaleTimeString('tr-TR', {
    hour: '2-digit',
    minute: '2-digit',
  })
}
