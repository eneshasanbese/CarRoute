/** Filtre select'lerinde "hepsi" seçeneğinin değeri; seçilince filtre kalkar. */
export const TUM_DEGERLER = '__hepsi__'

/** Select'ten gelen değer her zaman metin; kolon değeriyle metin olarak eşleşir. */
export function metinEsitlik(
  row: { getValue: (id: string) => unknown },
  id: string,
  value: unknown,
) {
  return String(row.getValue(id)) === String(value)
}
