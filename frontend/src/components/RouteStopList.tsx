import { BuildingIcon, WarehouseIcon } from 'lucide-react'
import { EmptyState } from '@/components/states'
import { serviceColor } from '@/lib/serviceColors'
import { cn, formatKm } from '@/lib/utils'
import type { RouteStop } from '@/types'

interface RouteStopListProps {
  stops: RouteStop[]
  className?: string
}

/**
 * Durak sırası salt-okunur: sıralamayı tamamen algoritma belirler, arayüzde
 * manuel taşıma/yeniden sıralama yoktur.
 */
export function RouteStopList({ stops, className }: RouteStopListProps) {
  const personelDuraklari = stops.filter((s) => s.employeeId !== null)

  if (personelDuraklari.length === 0) {
    return (
      <EmptyState
        title="Bu serviste henüz kimse yok"
        description="Personel eklendiğinde durak sırası burada otomatik oluşur."
        className={className}
      />
    )
  }

  return (
    <ol className={cn('space-y-0', className)}>
      {stops.map((stop, index) => {
        const depo = stop.employeeId === null
        const sonuncu = index === stops.length - 1
        const renk = serviceColor(stop.servisId)

        return (
          <li
            key={`${stop.durakNo}-${stop.employeeId ?? 'depo'}`}
            className="relative flex gap-3 pb-4 last:pb-0"
          >
            {!sonuncu ? (
              <span
                aria-hidden
                className="absolute top-7 left-[13px] h-[calc(100%-1rem)] w-px bg-border"
              />
            ) : null}

            <span
              className={cn(
                'relative z-10 flex size-[26px] shrink-0 items-center justify-center text-[11px] font-bold text-white',
                depo ? 'rounded-md bg-foreground' : 'rounded-full',
              )}
              style={depo ? undefined : { backgroundColor: renk }}
            >
              {depo ? (
                stop.durakNo === 0 ? (
                  <WarehouseIcon className="size-3.5" />
                ) : (
                  <BuildingIcon className="size-3.5" />
                )
              ) : (
                stop.durakNo
              )}
            </span>

            <div className="min-w-0 flex-1">
              <p
                className={cn(
                  'truncate text-sm',
                  depo ? 'font-semibold' : 'font-medium',
                )}
              >
                {stop.adSoyad}
              </p>
              <p className="text-xs text-muted-foreground tabular-nums">
                {stop.durakNo === 0
                  ? 'Başlangıç'
                  : `Önceki duraktan ${formatKm(stop.oncekiDuraktanKm)}`}
              </p>
            </div>
          </li>
        )
      })}
    </ol>
  )
}
