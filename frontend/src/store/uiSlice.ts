import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

interface UiState {
  /** Genel harita görünümünde gizlenen servisler (katman aç/kapa). */
  hiddenServiceIds: number[]
  /** Legend'den seçilen servis; diğerleri soluklaştırılır. */
  highlightedServiceId: number | null
}

const initialState: UiState = {
  hiddenServiceIds: [],
  highlightedServiceId: null,
}

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    toggleServiceVisibility(state, action: PayloadAction<number>) {
      const id = action.payload
      state.hiddenServiceIds = state.hiddenServiceIds.includes(id)
        ? state.hiddenServiceIds.filter((x) => x !== id)
        : [...state.hiddenServiceIds, id]
    },
    setHighlightedService(state, action: PayloadAction<number | null>) {
      state.highlightedServiceId = action.payload
    },
    showAllServices(state) {
      state.hiddenServiceIds = []
      state.highlightedServiceId = null
    },
  },
})

export const { toggleServiceVisibility, setHighlightedService, showAllServices } =
  uiSlice.actions

export default uiSlice.reducer
