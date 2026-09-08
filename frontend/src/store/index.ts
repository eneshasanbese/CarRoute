import { configureStore } from '@reduxjs/toolkit'
import employeesReducer from '@/store/employeesSlice'
import servicesReducer from '@/store/servicesSlice'
import trafficReducer from '@/store/trafficSlice'
import uiReducer from '@/store/uiSlice'

export const store = configureStore({
  reducer: {
    employees: employeesReducer,
    services: servicesReducer,
    traffic: trafficReducer,
    ui: uiReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
