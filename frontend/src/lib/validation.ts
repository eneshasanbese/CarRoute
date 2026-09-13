import { z } from 'zod'

/**
 * Telefon alanı — personel ve şoför formlarında ortak.
 *
 * <p>
 * Opsiyonel: mevcut kayıtların çoğunda telefon yok ve düzenlerken zorunlu kılmak
 * kullanıcıyı olmayan bir veriyi uydurmaya iterdi. Girildiyse biçim denetleniyor.
 */
export function telefonAlani() {
  return z
    .string()
    .trim()
    .refine(
      (v) => v === '' || /^[\d+()\s-]{10,20}$/.test(v),
      'Telefon yalnızca rakam, boşluk ve + ( ) - içerebilir.',
    )
    .refine(
      (v) => v === '' || v.replace(/\D/g, '').length >= 10,
      'Telefon en az 10 rakam olmalı (örn. 0532 123 45 67).',
    )
}

/**
 * Sayısal alanlar formda metin olarak tutulup gönderim öncesi sayıya çevriliyor;
 * böylece boş/geçersiz girişte Türkçe hata mesajı verebiliyoruz.
 */
export function sayiAlani(min: number, max: number, label: string) {
  return z
    .string()
    .trim()
    .min(1, `${label} zorunlu.`)
    .refine((v) => /^\d+$/.test(v), `${label} sayı olmalı.`)
    .refine((v) => {
      const n = Number(v)
      return n >= min && n <= max
    }, `${label} ${min}-${max} arasında olmalı.`)
}
