import { MoonIcon, SunriseIcon } from 'lucide-react'
import { ErrorState } from '@/components/states'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useTraffic } from '@/hooks/useTraffic'
import { cn } from '@/lib/utils'
import type { TrafficBucket, TrafficSnapshot } from '@/types'

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

function BucketRow({ bucket }: { bucket: TrafficBucket }) {
  const query = useTraffic(bucket)
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
        {query.data ? (
          <span className="font-semibold tabular-nums">
            %{query.data.yogunlukYuzde}
          </span>
        ) : (
          <Skeleton className="h-4 w-10" />
        )}
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
        {query.data ? (
          <div
            className={cn(
              'h-full rounded-full transition-all',
              yogunlukRengi(query.data.yogunlukYuzde),
            )}
            style={{ width: `${query.data.yogunlukYuzde}%` }}
          />
        ) : null}
      </div>
    </div>
  )
}

function guncellemeMetni(snapshot?: TrafficSnapshot) {
  if (!snapshot) return null
  const tarih = new Date(snapshot.guncellemeZamani)
  if (Number.isNaN(tarih.getTime())) return null
  return tarih.toLocaleTimeString('tr-TR', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function TrafficPanel({ className }: { className?: string }) {
  const sabah = useTraffic('sabah')
  const guncelleme = guncellemeMetni(sabah.data)

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
        {sabah.isError ? (
          <ErrorState
            title="Trafik verisi alınamadı"
            error={sabah.error}
            onRetry={() => void sabah.refetch()}
            className="p-4"
          />
        ) : (
          <>
            <BucketRow bucket="sabah" />
            <BucketRow bucket="aksam" />
          </>
        )}
      </CardContent>
    </Card>
  )
}
