import { RefreshCwIcon, TriangleAlertIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface ErrorStateProps {
  title?: string
  message?: string | null
  onRetry?: () => void
  className?: string
}

export function ErrorState({
  title = 'Veri yüklenemedi',
  message,
  onRetry,
  className,
}: ErrorStateProps) {
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
        <p className="max-w-md text-sm text-muted-foreground">
          {message ?? 'Bilinmeyen bir hata oluştu.'}
        </p>
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
