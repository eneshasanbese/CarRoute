import { axiosClient } from '@/api/axiosClient'
import type { Employee, EmployeeInput, MutationResult, Sefer } from '@/types'

/**
 * Değişiklik yanıtları etkilenen servisin rotasını da taşıyor; `sefer` o rotanın
 * ekranda açık olan sefere ait olmasını sağlar.
 */
export const employeesApi = {
  async list(): Promise<Employee[]> {
    const { data } = await axiosClient.get<Employee[]>('/api/employees')
    return data
  },

  async create(input: EmployeeInput, sefer: Sefer): Promise<MutationResult> {
    const { data } = await axiosClient.post<MutationResult>(
      '/api/employees',
      input,
      { params: { sefer } },
    )
    return data
  },

  async update(
    id: number,
    input: EmployeeInput,
    sefer: Sefer,
  ): Promise<MutationResult> {
    const { data } = await axiosClient.put<MutationResult>(
      `/api/employees/${id}`,
      input,
      { params: { sefer } },
    )
    return data
  },

  async remove(id: number, sefer: Sefer): Promise<MutationResult> {
    const { data } = await axiosClient.delete<MutationResult>(
      `/api/employees/${id}`,
      { params: { sefer } },
    )
    return data
  },
}
