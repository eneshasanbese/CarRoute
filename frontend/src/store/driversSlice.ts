import { createAsyncThunk, createSlice, isAnyOf } from '@reduxjs/toolkit'
import { apiErrorMessage } from '@/api/axiosClient'
import { driversApi } from '@/api/driversApi'
import type { RequestStatus } from '@/store/employeesSlice'
import type { Driver, DriverDeletionResult, DriverInput } from '@/types'

interface DriversState {
  items: Driver[]
  status: RequestStatus
  error: string | null
  /** Ekleme/güncelleme/silme sürerken butonları kilitlemek için. */
  saving: boolean
}

const initialState: DriversState = {
  items: [],
  status: 'idle',
  error: null,
  saving: false,
}

export const fetchDrivers = createAsyncThunk<
  Driver[],
  void,
  { rejectValue: string }
>('drivers/fetch', async (_arg, { rejectWithValue }) => {
  try {
    return await driversApi.list()
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

export const createDriver = createAsyncThunk<
  Driver,
  DriverInput,
  { rejectValue: string }
>('drivers/create', async (input, { rejectWithValue }) => {
  try {
    return await driversApi.create(input)
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

export const updateDriver = createAsyncThunk<
  Driver,
  { id: number; input: DriverInput },
  { rejectValue: string }
>('drivers/update', async ({ id, input }, { rejectWithValue }) => {
  try {
    return await driversApi.update(id, input)
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

export const deleteDriver = createAsyncThunk<
  DriverDeletionResult,
  number,
  { rejectValue: string }
>('drivers/delete', async (id, { rejectWithValue }) => {
  try {
    return await driversApi.remove(id)
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

const driversSlice = createSlice({
  name: 'drivers',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchDrivers.pending, (state) => {
        state.status = state.status === 'succeeded' ? 'succeeded' : 'loading'
        state.error = null
      })
      .addCase(fetchDrivers.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.items = action.payload
      })
      .addCase(fetchDrivers.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload ?? 'Şoför listesi yüklenemedi.'
      })

      // Şoför değişiklikleri birden fazla servisi etkileyebildiği için servis ve
      // personel listesini dispatch eden bileşen ayrıca tazeler.
      .addCase(createDriver.fulfilled, (state, action) => {
        state.saving = false
        state.items.push(action.payload)
      })
      .addCase(updateDriver.fulfilled, (state, action) => {
        state.saving = false
        const index = state.items.findIndex((d) => d.id === action.payload.id)
        if (index !== -1) state.items[index] = action.payload
      })
      .addCase(deleteDriver.fulfilled, (state, action) => {
        state.saving = false
        state.items = state.items.filter((d) => d.id !== action.meta.arg)
      })

      // Üç mutasyonun pending/rejected davranışı aynı.
      .addMatcher(
        isAnyOf(createDriver.pending, updateDriver.pending, deleteDriver.pending),
        (state) => {
          state.saving = true
        },
      )
      .addMatcher(
        isAnyOf(createDriver.rejected, updateDriver.rejected, deleteDriver.rejected),
        (state) => {
          state.saving = false
        },
      )
  },
})

export default driversSlice.reducer
