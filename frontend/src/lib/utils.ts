import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatKm(km: number) {
  return `${km.toFixed(1)} km`
}

export function formatSure(dakika: number) {
  const saat = Math.floor(dakika / 60)
  const dk = Math.round(dakika % 60)
  return saat > 0 ? `${saat}sa ${dk}dk` : `${dk} dk`
}
