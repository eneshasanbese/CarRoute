import { axiosClient } from '@/api/axiosClient'
import type { Employee, EmployeeInput, MutationResult } from '@/types'

export const employeesApi = {
  async list(): Promise<Employee[]> {
    const { data } = await axiosClient.get<Employee[]>('/api/employees')
    return data
  },

  async create(input: EmployeeInput): Promise<MutationResult> {
    const { data } = await axiosClient.post<MutationResult>(
      '/api/employees',
      input,
    )
    return data
  },

  async update(id: number, input: EmployeeInput): Promise<MutationResult> {
    const { data } = await axiosClient.put<MutationResult>(
      `/api/employees/${id}`,
      input,
    )
    return data
  },

  async remove(id: number): Promise<MutationResult> {
    const { data } = await axiosClient.delete<MutationResult>(
      `/api/employees/${id}`,
    )
    return data
  },
}
