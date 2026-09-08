import { ClockIcon, RouteIcon, TimerIcon, TriangleAlertIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { CapacityGauge } from '@/components/CapacityGauge'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { CAPACITY_META, capacityLevel } from '@/lib/capacity'
import { serviceColor } from '@/lib/serviceColors'
import { cn, formatKm, formatSure } from '@/lib/utils'
import type { Service } from '@/types'

interface ServiceCardProps {
  service: Service
}

export function ServiceCard({ service }: ServiceCardProps) {
  const level = capacityLevel(service)
  const meta = CAPACITY_META[level]
  const renk = serviceColor(service.id)

  return (
    <Card className="relative overflow-hidden transition-shadow hover:shadow-md focus-within:ring-2 focus-within:ring-ring/50">
      <span
        aria-hidden
        className="absolute inset-y-0 left-0 w-1"
        style={{ backgroundColor: renk }}
      />

      <div className="space-y-3 p-4 pl-5">
        <div className="flex items-start justify-between gap-2">
          <Link
            to={`/servis/${service.id}`}
            className="font-semibold outline-none after:absolute after:inset-0 after:content-['']"
          >
            Servis-{service.id}
          </Link>
          {level === 'under' ? (
            <Badge
              variant="outline"
              className={cn('relative z-10', meta.badge)}
            >
              <TriangleAlertIcon className="size-3" />
              Min. kapasite altında
            </Badge>
          ) : (
            <Badge variant="outline" className={cn('relative z-10', meta.badge)}>
              {meta.label}
            </Badge>
          )}
        </div>

        <CapacityGauge service={service} />

        <dl className="grid grid-cols-3 gap-2 border-t border-border pt-3 text-sm">
          <div>
            <dt className="flex items-center gap-1 text-xs text-muted-foreground">
              <RouteIcon className="size-3" />
              Mesafe
            </dt>
            <dd className="font-medium tabular-nums">
              {formatKm(service.toplamKm)}
            </dd>
          </div>
          <div>
            <dt className="flex items-center gap-1 text-xs text-muted-foreground">
              <TimerIcon className="size-3" />
              Süre
            </dt>
            <dd className="font-medium tabular-nums">
              {formatSure(service.tahminiSureDk)}
            </dd>
          </div>
          <div>
            <dt className="flex items-center gap-1 text-xs text-muted-foreground">
              <ClockIcon className="size-3" />
              Kalkış
            </dt>
            <dd className="font-medium tabular-nums">{service.kalkisSaati}</dd>
          </div>
        </dl>
      </div>
    </Card>
  )
}

export function ServiceCardSkeleton() {
  return (
    <Card className="space-y-3 p-4">
      <div className="h-4 w-24 animate-pulse rounded bg-muted" />
      <div className="h-2 w-full animate-pulse rounded-full bg-muted" />
      <div className="grid grid-cols-3 gap-2 border-t border-border pt-3">
        <div className="h-8 animate-pulse rounded bg-muted" />
        <div className="h-8 animate-pulse rounded bg-muted" />
        <div className="h-8 animate-pulse rounded bg-muted" />
      </div>
    </Card>
  )
}
