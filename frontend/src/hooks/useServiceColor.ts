import { useCallback } from 'react'
import { fallbackServiceColor } from '@/lib/serviceColors'
import { useAppSelector } from '@/store/hooks'
import { selectServiceColors } from '@/store/selectors'

/**
 * Servis id'sinden rengini veren fonksiyon. Renkler kayıtlı servislerin sırasına
 * göre dağıtıldığı için store'daki listeden okunuyor; liste henüz yoksa geçici
 * renk döner. Ayrıntı: lib/serviceColors.ts.
 */
export function useServiceColor() {
  const colors = useAppSelector(selectServiceColors)
  return useCallback(
    (servisId: number) => colors[servisId] ?? fallbackServiceColor(servisId),
    [colors],
  )
}
