import { axiosClient } from '@/api/axiosClient'
import type { RouteStop, Service } from '@/types'

export const servicesApi = {
  async list(): Promise<Service[]> {
    const { data } = await axiosClient.get<Service[]>('/api/services')
    return data
  },

  async route(id: number): Promise<RouteStop[]> {
    const { data } = await axiosClient.get<RouteStop[]>(
      `/api/services/${id}/route`,
    )
    return data
  },
}
