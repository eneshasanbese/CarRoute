/**
 * Her servise sabit bir renk. Servis id'si 1..10 arasında olduğu için sıra
 * indeksine göre değil, id'ye göre eşleşir — böylece renk her sayfada aynı kalır.
 */
const SERVICE_COLORS = [
  '#2563eb', // 1  mavi
  '#dc2626', // 2  kırmızı
  '#16a34a', // 3  yeşil
  '#ea580c', // 4  turuncu
  '#9333ea', // 5  mor
  '#0891b2', // 6  camgöbeği
  '#a16207', // 7  koyu sarı
  '#db2777', // 8  pembe
  '#4d7c0f', // 9  zeytin
  '#475569', // 10 kurşuni
] as const

export function serviceColor(servisId: number) {
  const index = (Math.abs(servisId) - 1) % SERVICE_COLORS.length
  return SERVICE_COLORS[index] ?? SERVICE_COLORS[0]
}

export { SERVICE_COLORS }
