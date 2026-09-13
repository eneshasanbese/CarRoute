import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { apiErrorMessage } from '@/api/axiosClient'
import { driversApi } from '@/api/driversApi'
import type { RequestStatus } from '@/store/employeesSlice'
import type { Driver, DriverInput } from '@/types'

interface DriversState {
  items: Driver[]
  status: RequestStatus
  error: string | null
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

      .addCase(createDriver.pending, (state) => {
        state.saving = true
      })
      .addCase(createDriver.fulfilled, (state, action) => {
        state.saving = false
        state.items.push(action.payload)
      })
      .addCase(createDriver.rejected, (state) => {
        state.saving = false
      })
  },
})

export default driversSlice.reducer
