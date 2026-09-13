package com.eneshasanbese.util;

import java.util.regex.Pattern;

/**
 * İlçe için ayrı bir kolon olmadığından adres metninden çıkarılıyor. Adresler iki
 * biçimde geliyor:
 * <ul>
 * <li>Seed verisi: "... No:125 D:9 Pendik/İstanbul"</li>
 * <li>Arayüzdeki adres autocomplete'i (Nominatim): "Zara Sokak, Esenler
 * Mahallesi, Pendik, İstanbul, Marmara Bölgesi, 34899, Türkiye"</li>
 * </ul>
 */
public final class AddressUtils {

    private static final String COUNTRY = "Türkiye";
    private static final Pattern POSTCODE = Pattern.compile("\\d{5}");

    private AddressUtils() {
    }

    public static String extractDistrict(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }

        String[] parts = address.split(",");
        if (parts.length > 1 && parts[parts.length - 1].trim().equals(COUNTRY)) {
            return districtFromGeocoderLabel(parts);
        }

        int slash = address.lastIndexOf('/');
        String beforeCity = slash > 0 ? address.substring(0, slash) : address;

        String trimmed = beforeCity.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        String candidate = lastSpace >= 0 ? trimmed.substring(lastSpace + 1) : trimmed;

        // "No:125" gibi bir parça yakalandıysa ilçe okunamamış demektir.
        return candidate.contains(":") ? "" : candidate;
    }

    /**
     * Parçalar küçükten büyüğe sıralı: ..., ilçe, il, [bölge], [posta kodu], ülke.
     * Bölge ve posta kodu her sonuçta yok; sondakiler atılınca kalan son parça il,
     * ondan öncesi ilçe. Sonuç ilin kendisiyse ilçe yoktur.
     */
    private static String districtFromGeocoderLabel(String[] parts) {
        int end = parts.length - 1;
        while (end > 0 && isPostcodeOrRegion(parts[end - 1].trim())) {
            end--;
        }
        return end >= 2 ? parts[end - 2].trim() : "";
    }

    private static boolean isPostcodeOrRegion(String part) {
        return POSTCODE.matcher(part).matches() || part.endsWith(" Bölgesi");
    }

    /** "Ad Soyad" metnini ada ve soyada böler; tek kelimeyse soyad boş kalır. */
    public static String[] splitFullName(String fullName) {
        String normalized = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        int lastSpace = normalized.lastIndexOf(' ');

        if (lastSpace < 0) {
            return new String[] { normalized, "" };
        }
        return new String[] { normalized.substring(0, lastSpace), normalized.substring(lastSpace + 1) };
    }
}
