import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { POLL_INTERVAL_MS, queryKeys } from '@/lib/queryKeys'
import type { EmployeeInput, MutationResult, Service } from '@/types'

export function useEmployees() {
  return useQuery({
    queryKey: queryKeys.employees,
    queryFn: api.getEmployees,
    refetchInterval: POLL_INTERVAL_MS,
  })
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Beklenmeyen bir hata oluştu.'
}

/**
 * Backend, ekleme/güncelleme/silme yanıtında etkilenen servisin güncel halini ve
 * yeniden hesaplanmış rotasını döndürür; cache'i önce bu yanıtla yazıp sonra
 * ilgili sorguları invalidate ediyoruz.
 */
function useMutationResultSync() {
  const queryClient = useQueryClient()

  return (result: MutationResult) => {
    queryClient.setQueryData<Service[]>(queryKeys.services, (old) =>
      old?.map((s) => (s.id === result.service.id ? result.service : s)),
    )
    queryClient.setQueryData(
      queryKeys.serviceRoute(result.service.id),
      result.route,
    )
    void queryClient.invalidateQueries({ queryKey: queryKeys.employees })
    void queryClient.invalidateQueries({ queryKey: queryKeys.services })
  }
}

export function useCreateEmployee() {
  const sync = useMutationResultSync()

  return useMutation({
    mutationFn: (input: EmployeeInput) => api.createEmployee(input),
    onSuccess: (result) => {
      sync(result)
      const ad = result.employee?.adSoyad ?? 'Personel'
      toast.success(`${ad} eklendi`, {
        description: `Servis-${result.service.id} rotası güncellendi.`,
      })
    },
    onError: (error) =>
      toast.error('Personel eklenemedi', { description: errorMessage(error) }),
  })
}

export function useUpdateEmployee() {
  const sync = useMutationResultSync()

  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: EmployeeInput }) =>
      api.updateEmployee(id, input),
    onSuccess: (result) => {
      sync(result)
      const ad = result.employee?.adSoyad ?? 'Personel'
      toast.success(`${ad} güncellendi`, {
        description: `Servis-${result.service.id} rotası güncellendi.`,
      })
    },
    onError: (error) =>
      toast.error('Personel güncellenemedi', {
        description: errorMessage(error),
      }),
  })
}

export function useDeleteEmployee() {
  const sync = useMutationResultSync()

  return useMutation({
    mutationFn: ({ id }: { id: number; adSoyad: string }) =>
      api.deleteEmployee(id),
    onSuccess: (result, variables) => {
      sync(result)
      toast.success(`${variables.adSoyad} silindi`, {
        description: `Servis-${result.service.id} rotası güncellendi.`,
      })
    },
    onError: (error) =>
      toast.error('Personel silinemedi', { description: errorMessage(error) }),
  })
}
