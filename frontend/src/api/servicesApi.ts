import { axiosClient } from '@/api/axiosClient'
import type { ReassignResult, Sefer, Service, ServiceRoute } from '@/types'

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

  /**
   * Bütün atamaları sıfırlayıp baştan dağıtır.
   *
   * <p>Yol matrisi kurulup arama koştuğu için diğer isteklerden uzun sürer;
   * çağıran taraf düğmeyi kilitlemeli.
   */
  async reassign(sefer: Sefer = 'sabah'): Promise<ReassignResult> {
    const { data } = await axiosClient.post<ReassignResult>(
      '/api/services/reassign',
      null,
      { params: { sefer }, timeout: REASSIGN_TIMEOUT_MS },
    )
    return data
  },
}

/** Dağıtım ~1-3 sn sürüyor; istemcinin varsayılan zaman aşımı buna dar gelebilir. */
const REASSIGN_TIMEOUT_MS = 60_000
