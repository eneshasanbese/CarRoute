import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { apiErrorMessage } from '@/api/axiosClient'
import { trafficApi } from '@/api/trafficApi'
import type { RequestStatus } from '@/store/employeesSlice'
import type { TrafficBucket, TrafficSnapshot } from '@/types'

interface TrafficState {
  snapshots: Partial<Record<TrafficBucket, TrafficSnapshot>>
  status: RequestStatus
  error: string | null
}

const initialState: TrafficState = {
  snapshots: {},
  status: 'idle',
  error: null,
}

export const fetchTrafficSnapshot = createAsyncThunk<
  TrafficSnapshot,
  TrafficBucket,
  { rejectValue: string }
>('traffic/fetch', async (bucket, { rejectWithValue }) => {
  try {
    return await trafficApi.snapshot(bucket)
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

const trafficSlice = createSlice({
  name: 'traffic',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchTrafficSnapshot.pending, (state) => {
        state.status = state.status === 'succeeded' ? 'succeeded' : 'loading'
        state.error = null
      })
      .addCase(fetchTrafficSnapshot.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.snapshots[action.payload.bucket] = action.payload
      })
      .addCase(fetchTrafficSnapshot.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload ?? 'Trafik verisi alınamadı.'
      })
  },
})

export default trafficSlice.reducer
