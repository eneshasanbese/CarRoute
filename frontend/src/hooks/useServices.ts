import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { api } from '@/lib/api'
import { POLL_INTERVAL_MS, queryKeys } from '@/lib/queryKeys'
import type { Service } from '@/types'

export function useServices() {
  return useQuery({
    queryKey: queryKeys.services,
    queryFn: api.getServices,
    refetchInterval: POLL_INTERVAL_MS,
  })
}

export function useService(id: number) {
  const query = useServices()
  const service = useMemo(
    () => query.data?.find((s) => s.id === id),
    [query.data, id],
  )
  return { ...query, service }
}

export interface ServicesSummary {
  toplamPersonel: number
  doluServis: number
  bosServis: number
  minAltiServis: number
  ortalamaDoluluk: number
}

export function summarizeServices(services: Service[]): ServicesSummary {
  if (services.length === 0) {
    return {
      toplamPersonel: 0,
      doluServis: 0,
      bosServis: 0,
      minAltiServis: 0,
      ortalamaDoluluk: 0,
    }
  }

  const toplamPersonel = services.reduce((sum, s) => sum + s.kisiSayisi, 0)
  const toplamKapasite = services.reduce((sum, s) => sum + s.maxKapasite, 0)

  return {
    toplamPersonel,
    doluServis: services.filter((s) => s.kisiSayisi >= s.maxKapasite).length,
    bosServis: services.filter((s) => s.kisiSayisi === 0).length,
    minAltiServis: services.filter((s) => s.kisiSayisi < s.minKapasite).length,
    ortalamaDoluluk: toplamKapasite
      ? Math.round((toplamPersonel / toplamKapasite) * 100)
      : 0,
  }
}
