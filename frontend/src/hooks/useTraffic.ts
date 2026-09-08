import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import type { TrafficBucket } from '@/types'

/** Trafik özeti dakikalık değişir; rota verisi kadar sık çekmeye gerek yok. */
const TRAFFIC_REFETCH_MS = 60_000

export function useTraffic(bucket: TrafficBucket) {
  return useQuery({
    queryKey: queryKeys.traffic(bucket),
    queryFn: () => api.getTrafficSnapshot(bucket),
    refetchInterval: TRAFFIC_REFETCH_MS,
  })
}
