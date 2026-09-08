import { InboxIcon, RefreshCwIcon, TriangleAlertIcon } from 'lucide-react'
import type * as React from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface ErrorStateProps {
  title?: string
  error?: unknown
  onRetry?: () => void
  className?: string
}

export function ErrorState({
  title = 'Veri yüklenemedi',
  error,
  onRetry,
  className,
}: ErrorStateProps) {
  const detay =
    error instanceof Error ? error.message : 'Bilinmeyen bir hata oluştu.'

  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center',
        className,
      )}
    >
      <TriangleAlertIcon className="size-6 text-destructive" />
      <div className="space-y-1">
        <p className="font-medium">{title}</p>
        <p className="max-w-md text-sm text-muted-foreground">{detay}</p>
      </div>
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RefreshCwIcon />
          Tekrar dene
        </Button>
      ) : null}
    </div>
  )
}

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
