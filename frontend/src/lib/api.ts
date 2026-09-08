import type {
  Employee,
  EmployeeInput,
  MutationResult,
  RouteStop,
  Service,
  TrafficBucket,
  TrafficSnapshot,
} from '@/types'
import * as mock from './mockApi'

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
).replace(/\/+$/, '')

/** Backend ayağa kalkana kadar tarayıcı içi sahte veri katmanı. */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export class ApiError extends Error {
  status: number
  url: string

  constructor(message: string, status: number, url: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.url = url
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${API_BASE_URL}${path}`
  let response: Response
  try {
    response = await fetch(url, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers,
      },
    })
  } catch {
    throw new ApiError('Sunucuya ulaşılamadı.', 0, url)
  }

  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new ApiError(
      body?.slice(0, 300) || `İstek başarısız (HTTP ${response.status})`,
      response.status,
      url,
    )
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  getEmployees(): Promise<Employee[]> {
    return USE_MOCK ? mock.getEmployees() : request('/api/employees')
  },

  createEmployee(input: EmployeeInput): Promise<MutationResult> {
    return USE_MOCK
      ? mock.createEmployee(input)
      : request('/api/employees', {
          method: 'POST',
          body: JSON.stringify(input),
        })
  },

  updateEmployee(id: number, input: EmployeeInput): Promise<MutationResult> {
    return USE_MOCK
      ? mock.updateEmployee(id, input)
      : request(`/api/employees/${id}`, {
          method: 'PUT',
          body: JSON.stringify(input),
        })
  },

  deleteEmployee(id: number): Promise<MutationResult> {
    return USE_MOCK
      ? mock.deleteEmployee(id)
      : request(`/api/employees/${id}`, { method: 'DELETE' })
  },

  getServices(): Promise<Service[]> {
    return USE_MOCK ? mock.getServices() : request('/api/services')
  },

  getServiceRoute(id: number): Promise<RouteStop[]> {
    return USE_MOCK
      ? mock.getServiceRoute(id)
      : request(`/api/services/${id}/route`)
  },

  getTrafficSnapshot(bucket: TrafficBucket): Promise<TrafficSnapshot> {
    return USE_MOCK
      ? mock.getTrafficSnapshot(bucket)
      : request(`/api/traffic/snapshot?bucket=${bucket}`)
  },
}
