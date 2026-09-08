import { Loader2Icon, PlusIcon } from 'lucide-react'
import { useState } from 'react'
import { EmployeeFormModal } from '@/components/EmployeeFormModal'
import { EmployeeTable } from '@/components/EmployeeTable'
import { ErrorState } from '@/components/states'
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
import { useDeleteEmployee, useEmployees } from '@/hooks/useEmployees'
import { useServices } from '@/hooks/useServices'
import type { Employee } from '@/types'

export function PersonelYonetimi() {
  const { data, isPending, isError, error, refetch } = useEmployees()
  const services = useServices()
  const remove = useDeleteEmployee()

  const [formAcik, setFormAcik] = useState(false)
  const [duzenlenen, setDuzenlenen] = useState<Employee | null>(null)
  const [silinecek, setSilinecek] = useState<Employee | null>(null)

  const servisIdleri =
    services.data?.map((s) => s.id) ??
    Array.from({ length: 10 }, (_, i) => i + 1)

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
      await remove.mutateAsync({
        id: silinecek.id,
        adSoyad: silinecek.adSoyad,
      })
    } finally {
      setSilinecek(null)
    }
  }

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

      {isError ? (
        <ErrorState
          title="Personel listesi yüklenemedi"
          error={error}
          onRetry={() => void refetch()}
        />
      ) : isPending ? (
        <TableSkeleton />
      ) : (
        <EmployeeTable
          employees={data}
          servisIdleri={servisIdleri}
          onEdit={duzenle}
          onDelete={setSilinecek}
        />
      )}

      <EmployeeFormModal
        open={formAcik}
        onOpenChange={setFormAcik}
        employee={duzenlenen}
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
              Servis-{silinecek?.servisId} rotası silme sonrası yeniden
              hesaplanır.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={remove.isPending}>
              Vazgeç
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={remove.isPending}
              onClick={(event) => {
                event.preventDefault()
                void silmeyiOnayla()
              }}
            >
              {remove.isPending ? (
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
