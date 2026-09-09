import { createAsyncThunk, createSlice, isAnyOf } from '@reduxjs/toolkit'
import { apiErrorMessage } from '@/api/axiosClient'
import { servicesApi } from '@/api/servicesApi'
import {
  createEmployee,
  deleteEmployee,
  updateEmployee,
  type RequestStatus,
} from '@/store/employeesSlice'
import type { Service, ServiceRoute } from '@/types'

interface ServicesState {
  items: Service[]
  status: RequestStatus
  error: string | null
  /** Servis id -> duraklar + yol geometrisi. */
  routes: Record<number, ServiceRoute>
  routeStatus: Record<number, RequestStatus>
  routeError: Record<number, string | null>
}

const initialState: ServicesState = {
  items: [],
  status: 'idle',
  error: null,
  routes: {},
  routeStatus: {},
  routeError: {},
}

export const fetchServices = createAsyncThunk<
  Service[],
  void,
  { rejectValue: string }
>('services/fetch', async (_arg, { rejectWithValue }) => {
  try {
    return await servicesApi.list()
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

export const fetchServiceRoute = createAsyncThunk<
  { servisId: number; route: ServiceRoute },
  number,
  { rejectValue: string }
>('services/fetchRoute', async (servisId, { rejectWithValue }) => {
  try {
    return { servisId, route: await servicesApi.route(servisId) }
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

const servicesSlice = createSlice({
  name: 'services',
  initialState,
  reducers: {},
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

export default servicesSlice.reducer
