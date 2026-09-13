/**
 * Servis renkleri.
 *
 * Renk servisin id'sinden değil, o an kayıtlı servisler arasındaki <b>id
 * sırasından</b> seçiliyor. Önceden `id % 10` kullanılıyordu: şoför eklenip
 * silindikçe id'ler 10'u geçiyor, 11 numaralı servis 1 numarayla aynı renge
 * düşüyor ve haritada iki rota ayırt edilemiyordu. Sıraya göre seçimde aynı anda
 * var olan servisler hep farklı renkte. Bedeli: araya bir servis eklenip
 * silinince sonrakilerin rengi kayabilir.
 *
 * Bileşenler rengi `useServiceColor()` ile alır; servis listesi store'da.
 */
const SERVICE_COLORS = [
  '#2563eb', // mavi
  '#dc2626', // kırmızı
  '#16a34a', // yeşil
  '#ea580c', // turuncu
  '#9333ea', // mor
  '#0891b2', // camgöbeği
  '#a16207', // koyu sarı
  '#db2777', // pembe
  '#4d7c0f', // zeytin
  '#475569', // kurşuni
] as const

/**
 * Sıradaki servisin rengi. Palet bitince altın açıyla (137.5°) ilerleyen koyu
 * tonlara geçiliyor: her yeni ton öncekilerin arasına düşüyor, koyu olduğu için
 * paletteki orta tonlardan da ayrılıyor.
 */
function colorAt(index: number) {
  if (index < SERVICE_COLORS.length) return SERVICE_COLORS[index]
  const hue = Math.round((index * 137.508) % 360)
  return `hsl(${hue} 70% 32%)`
}

/** Servis id'leri -> renk. Aynı anda var olan iki servis aynı renge düşmez. */
export function assignServiceColors(serviceIds: number[]): Record<number, string> {
  const sirali = [...serviceIds].sort((a, b) => a - b)
  return Object.fromEntries(sirali.map((id, index) => [id, colorAt(index)]))
}

/** Servis listesi henüz yüklenmemişken kullanılan geçici renk. */
export function fallbackServiceColor(servisId: number) {
  const index = (Math.abs(servisId) - 1) % SERVICE_COLORS.length
  return SERVICE_COLORS[index] ?? SERVICE_COLORS[0]
}

export { SERVICE_COLORS }
