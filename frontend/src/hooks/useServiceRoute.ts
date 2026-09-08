import { useQueries, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { POLL_INTERVAL_MS, queryKeys } from '@/lib/queryKeys'
import type { RouteStop } from '@/types'

export function useServiceRoute(id: number, enabled = true) {
  return useQuery({
    queryKey: queryKeys.serviceRoute(id),
    queryFn: () => api.getServiceRoute(id),
    refetchInterval: POLL_INTERVAL_MS,
    enabled: enabled && Number.isFinite(id),
  })
}

/** Genel harita görünümü için tüm servislerin rotalarını paralel çeker. */
export function useServiceRoutes(ids: number[]) {
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: queryKeys.serviceRoute(id),
      queryFn: () => api.getServiceRoute(id),
      refetchInterval: POLL_INTERVAL_MS,
    })),
  })

  const routes: Array<{ servisId: number; stops: RouteStop[] }> = []
  ids.forEach((id, index) => {
    const data = results[index]?.data
    if (data) routes.push({ servisId: id, stops: data })
  })

  return {
    routes,
    isPending: results.some((r) => r.isPending),
    isError: results.some((r) => r.isError),
    error: results.find((r) => r.error)?.error ?? null,
    refetch: () => results.forEach((r) => void r.refetch()),
  }
}
