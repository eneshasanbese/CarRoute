import { Loader2Icon, PlusIcon } from 'lucide-react'
import { useCallback, useState } from 'react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/common/ErrorState'
import { DriverDetailModal } from '@/components/drivers/DriverDetailModal'
import { DriverFormModal } from '@/components/drivers/DriverFormModal'
import { DriverTable } from '@/components/drivers/DriverTable'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { POLL_INTERVAL_MS, usePolling } from '@/hooks/usePolling'
import { servisAdi } from '@/lib/serviceName'
import { deleteDriver, fetchDrivers } from '@/store/driversSlice'
import { fetchEmployees } from '@/store/employeesSlice'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { fetchServices } from '@/store/servicesSlice'
import type { Driver } from '@/types'

export function SoforYonetimi() {
  const dispatch = useAppDispatch()
  const drivers = useAppSelector((state) => state.drivers.items)
  const status = useAppSelector((state) => state.drivers.status)
  const error = useAppSelector((state) => state.drivers.error)
  const saving = useAppSelector((state) => state.drivers.saving)
  const services = useAppSelector((state) => state.services.items)

  const [formAcik, setFormAcik] = useState(false)
  const [duzenlenen, setDuzenlenen] = useState<Driver | null>(null)
  const [silinecek, setSilinecek] = useState<Driver | null>(null)
  const [secilen, setSecilen] = useState<Driver | null>(null)

  const load = useCallback(() => {
    void dispatch(fetchDrivers())
    void dispatch(fetchServices())
  }, [dispatch])

  usePolling(load, POLL_INTERVAL_MS)

  function servisOf(driver: Driver | null) {
    return driver?.servisId != null
      ? services.find((service) => service.id === driver.servisId)
      : undefined
  }

  function ekle() {
    setDuzenlenen(null)
    setFormAcik(true)
  }

  function duzenle(driver: Driver) {
    setDuzenlenen(driver)
    setFormAcik(true)
  }

  async function silmeyiOnayla() {
    if (!silinecek) return
    try {
      const sonuc = await dispatch(deleteDriver(silinecek.id)).unwrap()

      // Silinen servisin yolcuları başka servislere geçti; iki liste de bayat.
      void dispatch(fetchServices())
      void dispatch(fetchEmployees())

      toast.success(`${silinecek.adSoyad} silindi`, {
        description:
          sonuc.tasinanPersonel > 0
            ? `${servisAdi(silinecek.plaka)} servisi kaldırıldı; ${sonuc.tasinanPersonel} kişi diğer servislere dağıtıldı.`
            : 'Şoförün servisinde yolcu yoktu.',
      })
    } catch (mesaj) {
      toast.error('Şoför silinemedi', { description: String(mesaj) })
    } finally {
      setSilinecek(null)
    }
  }

  const yukleniyor = status === 'loading' || status === 'idle'
  const silinecekServis = servisOf(silinecek)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Şoför Yönetimi</h1>
          <p className="text-sm text-muted-foreground">
            Her şoför bir servisin sahibidir. Şoför eklenince yeni servis açılır,
            silinince servisi kapanır ve yolcuları diğer servislere dağıtılır.
          </p>
        </div>
        <Button onClick={ekle}>
          <PlusIcon />
          Şoför Ekle
        </Button>
      </div>

      {status === 'failed' ? (
        <ErrorState
          title="Şoför listesi yüklenemedi"
          message={error}
          onRetry={load}
        />
      ) : yukleniyor && drivers.length === 0 ? (
        <TableSkeleton />
      ) : (
        <DriverTable
          drivers={drivers}
          services={services}
          onEdit={duzenle}
          onDelete={setSilinecek}
          onSelect={setSecilen}
        />
      )}

      <DriverFormModal
        open={formAcik}
        onOpenChange={setFormAcik}
        driver={duzenlenen}
      />

      <DriverDetailModal
        driver={secilen}
        service={servisOf(secilen)}
        onOpenChange={(open) => !open && setSecilen(null)}
      />

      <AlertDialog
        open={silinecek !== null}
        onOpenChange={(open) => !open && setSilinecek(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Şoför silinsin mi?</AlertDialogTitle>
            <AlertDialogDescription>
              <strong>{silinecek?.adSoyad}</strong>
              {silinecek?.servisId != null
                ? ` ve sürdüğü ${servisAdi(silinecek.plaka)} servisi`
                : ''}{' '}
              kalıcı olarak silinecek.
              {silinecekServis && silinecekServis.kisiSayisi > 0
                ? ` Bu servisteki ${silinecekServis.kisiSayisi} kişi kalan servislere dağıtılır.`
                : ''}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={saving}>Vazgeç</AlertDialogCancel>
            <AlertDialogAction
              disabled={saving}
              onClick={(event) => {
                event.preventDefault()
                void silmeyiOnayla()
              }}
            >
              {saving ? (
                <Loader2Icon className="mr-2 size-4 animate-spin" />
              ) : null}
              Sil
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function TableSkeleton() {
  return (
    <div className="space-y-3">
      <div className="flex gap-2">
        <Skeleton className="h-9 flex-1" />
        <Skeleton className="h-9 w-36" />
      </div>
      <div className="space-y-2 rounded-xl border border-border bg-card p-3">
        {Array.from({ length: 8 }, (_, i) => (
          <Skeleton key={i} className="h-9 w-full" />
        ))}
      </div>
    </div>
  )
}
