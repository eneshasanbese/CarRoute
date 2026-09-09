import { axiosClient } from '@/api/axiosClient'
import type { Sefer, Service, ServiceRoute } from '@/types'

export const servicesApi = {
  async list(sefer: Sefer = 'sabah'): Promise<Service[]> {
    const { data } = await axiosClient.get<Service[]>('/api/services', {
      params: { sefer },
    })
    return data
  },

  async route(id: number, sefer: Sefer = 'sabah'): Promise<ServiceRoute> {
    const { data } = await axiosClient.get<ServiceRoute>(
      `/api/services/${id}/route`,
      { params: { sefer } },
    )
    return data
  },
}
