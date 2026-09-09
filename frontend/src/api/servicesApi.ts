import { axiosClient } from '@/api/axiosClient'
import type { Service, ServiceRoute } from '@/types'

export const servicesApi = {
  async list(): Promise<Service[]> {
    const { data } = await axiosClient.get<Service[]>('/api/services')
    return data
  },

  async route(id: number): Promise<ServiceRoute> {
    const { data } = await axiosClient.get<ServiceRoute>(
      `/api/services/${id}/route`,
    )
    return data
  },
}
