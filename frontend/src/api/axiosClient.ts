import axios from 'axios'

/**
 * Bütün backend çağrıları bu örnek üzerinden gider. Adres .env'deki
 * VITE_API_BASE_URL ile belirlenir.
 */
export const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
})

/** Backend'in hata gövdesi: { status, error, message } */
interface ApiErrorBody {
  status: number
  error: string
  message: string
}

/**
 * Hangi katmandan bakılırsa bakılsın okunabilir tek bir hata metni üretir.
 * Slice'lardaki `rejectWithValue` bunu kullanır, bileşenler de doğrudan basar.
 */
export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    if (error.response?.data?.message) {
      return error.response.data.message
    }
    if (error.code === 'ECONNABORTED') {
      return 'Sunucu zamanında yanıt vermedi.'
    }
    if (!error.response) {
      return 'Sunucuya ulaşılamadı. Backend çalışıyor mu?'
    }
    return `İstek başarısız (HTTP ${error.response.status})`
  }

  return error instanceof Error ? error.message : 'Beklenmeyen bir hata oluştu.'
}
