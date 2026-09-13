package com.eneshasanbese.util;

/**
 * Geohash kodlayıcı. traffic_speed tablosundaki hücreler geohash ile
 * anahtarlandığı için, bir koordinatın hangi trafik hücresine düştüğünü
 * bulmakta kullanılır.
 */
public final class GeoHash {

    private static final char[] BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();
    private static final int[] BIT_MASK = { 16, 8, 4, 2, 1 };

    private GeoHash() {
    }

    public static String encode(double latitude, double longitude, int precision) {
        if (precision < 1) {
            throw new IllegalArgumentException("precision en az 1 olmalı: " + precision);
        }

        double[] latRange = { -90.0, 90.0 };
        double[] lonRange = { -180.0, 180.0 };

        StringBuilder geohash = new StringBuilder(precision);
        boolean even = true; // true iken boylam, false iken enlem ikiye bölünür
        int bit = 0;
        int base32Index = 0;

        while (geohash.length() < precision) {
            double[] range = even ? lonRange : latRange;
            double value = even ? longitude : latitude;
            double middle = (range[0] + range[1]) / 2;

            if (value > middle) {
                base32Index |= BIT_MASK[bit];
                range[0] = middle;
            } else {
                range[1] = middle;
            }

            even = !even;

            if (bit < 4) {
                bit++;
            } else {
                geohash.append(BASE32[base32Index]);
                bit = 0;
                base32Index = 0;
            }
        }

        return geohash.toString();
    }
}
