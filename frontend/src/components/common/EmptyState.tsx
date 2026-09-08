import { InboxIcon } from 'lucide-react'
import type * as React from 'react'
import { cn } from '@/lib/utils'

interface EmptyStateProps {
  title: string
  description?: string
  icon?: React.ReactNode
  action?: React.ReactNode
  className?: string
}

export function EmptyState({
  title,
  description,
  icon,
  action,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-card/50 p-10 text-center',
        className,
      )}
    >
      <div className="text-muted-foreground">
        {icon ?? <InboxIcon className="size-6" />}
      </div>
      <div className="space-y-1">
        <p className="font-medium">{title}</p>
        {description ? (
          <p className="max-w-md text-sm text-muted-foreground">
            {description}
          </p>
        ) : null}
      </div>
      {action}
    </div>
  )
}
