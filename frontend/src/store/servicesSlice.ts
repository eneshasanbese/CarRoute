import {
  createAsyncThunk,
  createSlice,
  isAnyOf,
  type PayloadAction,
} from '@reduxjs/toolkit'
import { apiErrorMessage } from '@/api/axiosClient'
import { servicesApi } from '@/api/servicesApi'
import {
  createEmployee,
  deleteEmployee,
  updateEmployee,
  type RequestStatus,
} from '@/store/employeesSlice'
import type { Sefer, Service, ServiceRoute } from '@/types'

interface ServicesState {
  /** Görüntülenen sefer. Değişince rotalar tazelenir. */
  sefer: Sefer
  items: Service[]
  status: RequestStatus
  error: string | null
  /** Servis id -> duraklar + yol geometrisi. */
  routes: Record<number, ServiceRoute>
  routeStatus: Record<number, RequestStatus>
  routeError: Record<number, string | null>
}

const initialState: ServicesState = {
  sefer: 'sabah',
  items: [],
  status: 'idle',
  error: null,
  routes: {},
  routeStatus: {},
  routeError: {},
}

export const fetchServices = createAsyncThunk<
  Service[],
  Sefer | undefined,
  { rejectValue: string; state: { services: ServicesState } }
>('services/fetch', async (sefer, { getState, rejectWithValue }) => {
  try {
    return await servicesApi.list(sefer ?? getState().services.sefer)
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

export const fetchServiceRoute = createAsyncThunk<
  { servisId: number; route: ServiceRoute },
  number,
  { rejectValue: string; state: { services: ServicesState } }
>('services/fetchRoute', async (servisId, { getState, rejectWithValue }) => {
  try {
    const sefer = getState().services.sefer
    return { servisId, route: await servicesApi.route(servisId, sefer) }
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

const servicesSlice = createSlice({
  name: 'services',
  initialState,
  reducers: {
    /**
     * Sefer değişince rotalar bayatlar: akşam güzergâhı sabahın tersi değil,
     * bağımsız hesaplanıyor. Bu yüzden önbellek temizlenip yeniden çekiliyor.
     */
    setSefer(state, action: PayloadAction<Sefer>) {
      if (state.sefer === action.payload) return
      state.sefer = action.payload
      state.routes = {}
      state.routeStatus = {}
      state.routeError = {}
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchServices.pending, (state) => {
        state.status = state.status === 'succeeded' ? 'succeeded' : 'loading'
        state.error = null
      })
      .addCase(fetchServices.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.items = action.payload
      })
      .addCase(fetchServices.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload ?? 'Servisler yüklenemedi.'
      })

      .addCase(fetchServiceRoute.pending, (state, action) => {
        const servisId = action.meta.arg
        if (state.routeStatus[servisId] !== 'succeeded') {
          state.routeStatus[servisId] = 'loading'
        }
        state.routeError[servisId] = null
      })
      .addCase(fetchServiceRoute.fulfilled, (state, action) => {
        const { servisId, route } = action.payload
        state.routeStatus[servisId] = 'succeeded'
        state.routes[servisId] = route
      })
      .addCase(fetchServiceRoute.rejected, (state, action) => {
        const servisId = action.meta.arg
        state.routeStatus[servisId] = 'failed'
        state.routeError[servisId] = action.payload ?? 'Rota yüklenemedi.'
      })

      /**
       * Backend ekleme/güncelleme/silme yanıtında etkilenen servisin güncel
       * halini ve yeniden hesaplanmış rotasını döndürüyor; ayrı bir istek
       * atmadan doğrudan store'a yazıyoruz.
       */
      .addMatcher(
        isAnyOf(
          createEmployee.fulfilled,
          updateEmployee.fulfilled,
          deleteEmployee.fulfilled,
        ),
        (state, action) => {
          const { service, route } = action.payload
          const index = state.items.findIndex((s) => s.id === service.id)
          if (index === -1) {
            state.items.push(service)
          } else {
            state.items[index] = service
          }
          state.routes[service.id] = route
          state.routeStatus[service.id] = 'succeeded'
          state.routeError[service.id] = null
        },
      )
  },
})

export const { setSefer } = servicesSlice.actions

export default servicesSlice.reducer
