import { Loader2Icon, PlusIcon } from "lucide-react"
import { useCallback, useState } from 'react'
import { toast } from 'sonner'
import { ErrorState } from "@/components/common/ErrorState"
import { EmployeeDetailModal } from "@/components/employees/EmployeeDetailModal"
import { EmployeeFormModal } from '@/components/employees/EmployeeFormModal'
import { EmployeeTable } from '@/components/employees/EmployeeTable'
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
import { deleteEmployee, fetchEmployees } from '@/store/employeesSlice'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { selectServiceNames } from '@/store/selectors'
import { fetchServices } from '@/store/servicesSlice'
import type { Employee } from '@/types'

export function PersonelYonetimi() {
  const dispatch = useAppDispatch()
  const employees = useAppSelector((state) => state.employees.items)
  const status = useAppSelector((state) => state.employees.status)
  const error = useAppSelector((state) => state.employees.error)
  const saving = useAppSelector((state) => state.employees.saving)
  const servisAdlari = useAppSelector(selectServiceNames)

  const [formAcik, setFormAcik] = useState(false)
  const [duzenlenen, setDuzenlenen] = useState<Employee | null>(null)
  const [silinecek, setSilinecek] = useState<Employee | null>(null)
  const [secilen, setSecilen] = useState<Employee | null>(null)

  const load = useCallback(() => {
    void dispatch(fetchEmployees())
    void dispatch(fetchServices())
  }, [dispatch])

  usePolling(load, POLL_INTERVAL_MS)

  function ekle() {
    setDuzenlenen(null)
    setFormAcik(true)
  }

  function duzenle(employee: Employee) {
    setDuzenlenen(employee)
    setFormAcik(true)
  }

  async function silmeyiOnayla() {
    if (!silinecek) return
    try {
      const sonuc = await dispatch(
        deleteEmployee({ id: silinecek.id, adSoyad: silinecek.adSoyad }),
      ).unwrap()
      toast.success(`${silinecek.adSoyad} silindi`, {
        description: sonuc.service
          ? `${servisAdi(sonuc.service.plaka)} rotası güncellendi.`
          : undefined,
      })
    } catch (mesaj) {
      toast.error('Personel silinemedi', { description: String(mesaj) })
    } finally {
      setSilinecek(null)
    }
  }

  const yukleniyor = status === 'loading' || status === 'idle'

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Personel Yönetimi</h1>
          <p className="text-sm text-muted-foreground">
            Kişi eklendiğinde veya silindiğinde ilgili servisin rotası anında
            yeniden hesaplanır.
          </p>
        </div>
        <Button onClick={ekle}>
          <PlusIcon />
          Personel Ekle
        </Button>
      </div>

      {status === 'failed' ? (
        <ErrorState
          title="Personel listesi yüklenemedi"
          message={error}
          onRetry={load}
        />
      ) : yukleniyor && employees.length === 0 ? (
        <TableSkeleton />
      ) : (
        <EmployeeTable
          employees={employees}
          servisAdlari={servisAdlari}
          onEdit={duzenle}
          onDelete={setSilinecek}
          onSelect={setSecilen}
        />
      )}

      <EmployeeFormModal
        open={formAcik}
        onOpenChange={setFormAcik}
        employee={duzenlenen}
      />

      <EmployeeDetailModal
        employee={secilen}
        onOpenChange={(open) => !open && setSecilen(null)}
      />

      <AlertDialog
        open={silinecek !== null}
        onOpenChange={(open) => !open && setSilinecek(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Personel silinsin mi?</AlertDialogTitle>
            <AlertDialogDescription>
              <strong>{silinecek?.adSoyad}</strong> kalıcı olarak silinecek.
              {silinecek?.servisId != null
                ? ` ${servisAdlari[silinecek.servisId] ?? 'Servis'} rotası silme sonrası yeniden hesaplanır.`
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
        <Skeleton className="h-9 w-36" />
        <Skeleton className="h-9 w-36" />
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
