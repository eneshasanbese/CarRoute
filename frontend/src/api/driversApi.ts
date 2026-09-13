import { axiosClient } from '@/api/axiosClient'
import type { Driver, DriverDeletionResult, DriverInput } from '@/types'

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

  /**
   * Şoförü ve aracını günceller. Ev adresi değiştiyse backend dağıtımı yeniden
   * dengeler; çağıran taraf servis ve personel listesini de tazelemeli.
   */
  async update(id: number, input: DriverInput): Promise<Driver> {
    const { data } = await axiosClient.put<Driver>(`/api/drivers/${id}`, input)
    return data
  },

  /** Şoförü ve servisini siler; o servisin yolcuları kalan servislere dağıtılır. */
  async remove(id: number): Promise<DriverDeletionResult> {
    const { data } = await axiosClient.delete<DriverDeletionResult>(
      `/api/drivers/${id}`,
    )
    return data
  },
}
