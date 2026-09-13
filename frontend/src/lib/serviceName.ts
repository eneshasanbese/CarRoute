/**
 * Servisin arayüzdeki adı: aracın plakası.
 *
 * Servis id'si veritabanı sırası; servis silinip yenisi eklendikçe boşluklu
 * ilerliyor (10 servis varken "Servis-12" görünüyordu). Id yalnızca URL'de ve
 * renk eşlemesinde kalıyor. Plaka zorunlu; yine de boş gelirse genel bir ad
 * düşülüyor.
 */
export function servisAdi(plaka: string | null | undefined) {
  return plaka?.trim() || 'Plakasız servis'
}

/** Plakaları sayı kısmına göre doğal sırayla dizer ("34 SRV 9" < "34 SRV 10"). */
export function servisAdiKarsilastir(a: string, b: string) {
  return a.localeCompare(b, 'tr', { numeric: true })
}
