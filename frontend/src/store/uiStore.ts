import { create } from 'zustand'

interface UiState {
  /** Genel harita görünümünde gizlenen servisler (katman aç/kapa). */
  hiddenServiceIds: number[]
  /** Legend'den seçilen servis; diğerleri soluklaştırılır. */
  highlightedServiceId: number | null
  toggleServiceVisibility: (id: number) => void
  setAllServicesVisible: () => void
  showOnlyService: (id: number) => void
  setHighlightedServiceId: (id: number | null) => void
}

export const useUiStore = create<UiState>((set) => ({
  hiddenServiceIds: [],
  highlightedServiceId: null,

  toggleServiceVisibility: (id) =>
    set((state) => ({
      hiddenServiceIds: state.hiddenServiceIds.includes(id)
        ? state.hiddenServiceIds.filter((x) => x !== id)
        : [...state.hiddenServiceIds, id],
    })),

  setAllServicesVisible: () =>
    set({ hiddenServiceIds: [], highlightedServiceId: null }),

  showOnlyService: (id) =>
    set({ hiddenServiceIds: [], highlightedServiceId: id }),

  setHighlightedServiceId: (id) => set({ highlightedServiceId: id }),
}))
