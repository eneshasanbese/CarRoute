package com.eneshasanbese.enums;

/**
 * Servis seferi. Sabah ve akşam yalnızca "aynı rotanın tersi" değil; üç ayrı
 * yerde farklılaşıyorlar ve bu enum o farkları tek noktada topluyor.
 *
 * <ul>
 * <li><b>Yön</b> — sabah şoförün evinden ofise, akşam ofisten şoförün evine.</li>
 * <li><b>Trafik dilimi</b> — akşam zirvesi sabahtan belirgin biçimde yavaş
 * (ölçülen medyan hız 44'e karşı 53 km/sa), ve yavaşlama her koridorda aynı
 * oranda değil.</li>
 * <li><b>Zaman çapası</b> — sabah <i>varış</i> sabit (08:00'de ofiste ol,
 * kalkışı geriye say), akşam <i>kalkış</i> sabit (17:30'da ofisten çık,
 * varışları ileriye say).</li>
 * </ul>
 *
 * <p>
 * Yol matrisi de asimetrik olduğu için akşam sıralaması sabahın tersi olarak
 * türetilmez, bağımsız hesaplanır.
 */
public enum Shift {

    /** Ev → ofis. Kuralı zorlayan kişi ilk binen. */
    SABAH,

    /** Ofis → ev. Kuralı zorlayan kişi en son inen. */
    AKSAM;

    /** Arayüzden gelen {@code sabah} / {@code aksam} değerini karşılığa çevirir. */
    public static Shift of(String value) {
        if (value == null || value.isBlank()) {
            return SABAH;
        }
        return switch (value.trim().toLowerCase()) {
            case "sabah" -> SABAH;
            case "aksam", "akşam" -> AKSAM;
            default -> throw new IllegalArgumentException(
                    "Geçersiz sefer: " + value + " (beklenen: sabah | aksam)");
        };
    }

    public String label() {
        return this == SABAH ? "sabah" : "aksam";
    }
}
