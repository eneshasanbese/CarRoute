import { useEffect } from 'react'

/**
 * Verilen işi hemen bir kez, sonra sabit aralıklarla tekrar çalıştırır.
 * Ekleme/silme sonrası rota değişikliğinin arayüze yansıması için kullanılıyor;
 * WebSocket'e geçilirse bu hook yerine soket olayları dispatch etmeli.
 */
export function usePolling(run: () => void, intervalMs: number) {
  useEffect(() => {
    run()
    const timer = setInterval(run, intervalMs)
    return () => clearInterval(timer)
    // `run` her render'da yeniden üretilirse interval sıfırlanır; çağıran taraf
    // useCallback ile sabitlemeli.
  }, [run, intervalMs])
}

/** Servis/personel verisinin tazelenme sıklığı. */
export const POLL_INTERVAL_MS = 5000
