import { createSelector } from '@reduxjs/toolkit'
import type { RootState } from '@/store'

export const selectServices = (state: RootState) => state.services.items
export const selectEmployees = (state: RootState) => state.employees.items

export const selectServiceById = (servisId: number) => (state: RootState) =>
  state.services.items.find((service) => service.id === servisId)

export const selectRoute = (servisId: number) => (state: RootState) =>
  state.services.routes[servisId]

export const selectRouteStatus = (servisId: number) => (state: RootState) =>
  state.services.routeStatus[servisId] ?? 'idle'

export const selectRouteError = (servisId: number) => (state: RootState) =>
  state.services.routeError[servisId] ?? null

export interface ServicesSummary {
  toplamPersonel: number
  doluServis: number
  bosServis: number
  minAltiServis: number
  ortalamaDoluluk: number
}

/** Dashboard üst şeridi. Servis listesi değişmedikçe yeniden hesaplanmaz. */
export const selectServicesSummary = createSelector(
  [selectServices],
  (services): ServicesSummary => {
    if (services.length === 0) {
      return {
        toplamPersonel: 0,
        doluServis: 0,
        bosServis: 0,
        minAltiServis: 0,
        ortalamaDoluluk: 0,
      }
    }

    const toplamPersonel = services.reduce((sum, s) => sum + s.kisiSayisi, 0)
    const toplamKapasite = services.reduce((sum, s) => sum + s.maxKapasite, 0)

    return {
      toplamPersonel,
      doluServis: services.filter((s) => s.kisiSayisi >= s.maxKapasite).length,
      bosServis: services.filter((s) => s.kisiSayisi === 0).length,
      minAltiServis: services.filter((s) => s.kisiSayisi < s.minKapasite).length,
      ortalamaDoluluk: toplamKapasite
        ? Math.round((toplamPersonel / toplamKapasite) * 100)
        : 0,
    }
  },
)

/** Personel tablosundaki ilçe filtresi için. */
export const selectDistricts = createSelector([selectEmployees], (employees) =>
  [...new Set(employees.map((e) => e.ilce))].sort((a, b) =>
    a.localeCompare(b, 'tr'),
  ),
)
