import { axiosClient } from '@/api/axiosClient'
import type { Driver, DriverInput } from '@/types'

export const driversApi = {
  async list(): Promise<Driver[]> {
    const { data } = await axiosClient.get<Driver[]>('/api/drivers')
    return data
  },

  /**
   * Şoförü ve servisini birlikte oluşturur. Backend eklemeden sonra dağıtımı
   * dengelediği için çağıran tarafın servis listesini de tazelemesi gerekir.
   */
  async create(input: DriverInput): Promise<Driver> {
    const { data } = await axiosClient.post<Driver>('/api/drivers', input)
    return data
  },
}
