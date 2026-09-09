import { MoonIcon, SunIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { Sefer } from '@/types'

interface SeferToggleProps {
  value: Sefer
  onChange: (sefer: Sefer) => void
  className?: string
}

const SECENEKLER: Array<{ value: Sefer; label: string; icon: typeof SunIcon }> =
  [
    { value: 'sabah', label: 'Sabah', icon: SunIcon },
    { value: 'aksam', label: 'Akşam', icon: MoonIcon },
  ]

/**
 * Sabah ve akşam seferleri arasında geçiş.
 *
 * İkisi aynı rotanın tersi değil: yol matrisi asimetrik, akşam tıkanıklığı her
 * koridorda aynı oranda artmıyor ve zaman çapası ters yönde çalışıyor (sabah
 * varış sabit, akşam kalkış sabit). Bu yüzden seçim değişince rotalar
 * backend'den yeniden çekilir.
 */
export function SeferToggle({ value, onChange, className }: SeferToggleProps) {
  return (
    <div
      role="radiogroup"
      aria-label="Sefer seçimi"
      className={cn(
        'inline-flex items-center gap-1 rounded-lg border border-border bg-muted/40 p-1',
        className,
      )}
    >
      {SECENEKLER.map(({ value: secenek, label, icon: Icon }) => {
        const secili = value === secenek
        return (
          <button
            key={secenek}
            type="button"
            role="radio"
            aria-checked={secili}
            onClick={() => onChange(secenek)}
            className={cn(
              'flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
              secili
                ? 'bg-background text-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            <Icon className="size-4" />
            {label}
          </button>
        )
      })}
    </div>
  )
}
