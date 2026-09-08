import { CAPACITY_META, capacityLevel } from '@/lib/capacity'
import { cn } from '@/lib/utils'
import type { Service } from '@/types'

interface CapacityGaugeProps {
  service: Service
  className?: string
  /** Sayısal etiket (ör. "12 / 15") gösterilsin mi. */
  showLabel?: boolean
}

/**
 * Kapasite göstergesi. Bar 0..maxKapasite üzerinden dolar; minKapasite (5)
 * çizgisi bar üzerinde ayrı bir işaret olarak durur.
 */
export function CapacityGauge({
  service,
  className,
  showLabel = true,
}: CapacityGaugeProps) {
  const level = capacityLevel(service)
  const meta = CAPACITY_META[level]
  const oran = Math.min(100, (service.kisiSayisi / service.maxKapasite) * 100)
  const minOran = (service.minKapasite / service.maxKapasite) * 100

  return (
    <div className={cn('space-y-1.5', className)}>
      {showLabel ? (
        <div className="flex items-baseline justify-between text-sm">
          <span className={cn('font-semibold tabular-nums', meta.text)}>
            {service.kisiSayisi} / {service.maxKapasite}
          </span>
          <span className="text-xs text-muted-foreground">{meta.label}</span>
        </div>
      ) : null}

      <div
        className="relative h-2 w-full overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-valuenow={service.kisiSayisi}
        aria-valuemin={0}
        aria-valuemax={service.maxKapasite}
        aria-label={`Servis-${service.id} doluluk`}
      >
        <div
          className={cn('h-full rounded-full transition-all', meta.bar)}
          style={{ width: `${oran}%` }}
        />
        <span
          className="absolute inset-y-0 w-px bg-foreground/35"
          style={{ left: `${minOran}%` }}
          title={`Min. kapasite: ${service.minKapasite}`}
        />
      </div>
    </div>
  )
}
