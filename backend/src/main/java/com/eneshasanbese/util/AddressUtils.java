package com.eneshasanbese.util;

/**
 * Seed verisindeki adresler "... No:125 D:9 Pendik/İstanbul" biçiminde. İlçe için
 * ayrı bir kolon olmadığından adres metninden çıkarılıyor.
 */
public final class AddressUtils {

    private AddressUtils() {
    }

    public static String extractDistrict(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }

        int slash = address.lastIndexOf('/');
        String beforeCity = slash > 0 ? address.substring(0, slash) : address;

        String trimmed = beforeCity.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        String candidate = lastSpace >= 0 ? trimmed.substring(lastSpace + 1) : trimmed;

        // "No:125" gibi bir parça yakalandıysa ilçe okunamamış demektir.
        return candidate.contains(":") ? "" : candidate;
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
