import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { apiErrorMessage } from '@/api/axiosClient'
import { employeesApi } from '@/api/employeesApi'
import type { Employee, EmployeeInput, MutationResult } from '@/types'

export type RequestStatus = 'idle' | 'loading' | 'succeeded' | 'failed'

interface EmployeesState {
  items: Employee[]
  status: RequestStatus
  error: string | null
  /** Ekleme/güncelleme/silme sürerken butonları kilitlemek için. */
  saving: boolean
}

const initialState: EmployeesState = {
  items: [],
  status: 'idle',
  error: null,
  saving: false,
}

export const fetchEmployees = createAsyncThunk<
  Employee[],
  void,
  { rejectValue: string }
>('employees/fetch', async (_arg, { rejectWithValue }) => {
  try {
    return await employeesApi.list()
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

export const createEmployee = createAsyncThunk<
  MutationResult,
  EmployeeInput,
  { rejectValue: string }
>('employees/create', async (input, { rejectWithValue }) => {
  try {
    return await employeesApi.create(input)
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

export const updateEmployee = createAsyncThunk<
  MutationResult,
  { id: number; input: EmployeeInput },
  { rejectValue: string }
>('employees/update', async ({ id, input }, { rejectWithValue }) => {
  try {
    return await employeesApi.update(id, input)
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

export const deleteEmployee = createAsyncThunk<
  MutationResult,
  { id: number; adSoyad: string },
  { rejectValue: string }
>('employees/delete', async ({ id }, { rejectWithValue }) => {
  try {
    return await employeesApi.remove(id)
  } catch (error) {
    return rejectWithValue(apiErrorMessage(error))
  }
})

const employeesSlice = createSlice({
  name: 'employees',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchEmployees.pending, (state) => {
        // Arka plandaki periyodik tazelemede iskeleti tekrar göstermiyoruz.
        state.status = state.status === 'succeeded' ? 'succeeded' : 'loading'
        state.error = null
      })
      .addCase(fetchEmployees.fulfilled, (state, action) => {
        state.status = 'succeeded'
        state.items = action.payload
      })
      .addCase(fetchEmployees.rejected, (state, action) => {
        state.status = 'failed'
        state.error = action.payload ?? 'Personel listesi yüklenemedi.'
      })

      // Ekleme/güncelleme/silme yanıtındaki personeli listeye hemen işliyoruz;
      // servis ve rota tarafını servicesSlice aynı action'ları dinleyerek günceller.
      .addCase(createEmployee.fulfilled, (state, action) => {
        state.saving = false
        if (action.payload.employee) {
          state.items.push(action.payload.employee)
        }
      })
      .addCase(updateEmployee.fulfilled, (state, action) => {
        state.saving = false
        const updated = action.payload.employee
        if (updated) {
          const index = state.items.findIndex((e) => e.id === updated.id)
          if (index !== -1) state.items[index] = updated
        }
      })
      .addCase(deleteEmployee.fulfilled, (state, action) => {
        state.saving = false
        state.items = state.items.filter((e) => e.id !== action.meta.arg.id)
      })

      // Üç mutasyonun pending/rejected davranışı aynı.
      .addMatcher(
        (action): action is { type: string } =>
          /^employees\/(create|update|delete)\/pending$/.test(
            (action as { type: string }).type,
          ),
        (state) => {
          state.saving = true
        },
      )
      .addMatcher(
        (action): action is { type: string } =>
          /^employees\/(create|update|delete)\/rejected$/.test(
            (action as { type: string }).type,
          ),
        (state) => {
          state.saving = false
        },
      )
  },
})

export default employeesSlice.reducer
