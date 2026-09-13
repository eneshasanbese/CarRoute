package com.eneshasanbese.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AddressUtilsTest {

    @Test
    @DisplayName("Seed biçiminde ilçe eğik çizgiden önceki kelime")
    void seedBicimi() {
        assertEquals("Kadıköy",
                AddressUtils.extractDistrict("Fikirtepe Mah. Rıdvan Paşa Cad. No:172 D:1 Kadıköy/İstanbul"));
    }

    @Test
    @DisplayName("Seed biçiminde virgül ve kapı numarasındaki eğik çizgi yanıltmaz")
    void seedBicimiVirgulVeEgikCizgi() {
        assertEquals("Sancaktepe",
                AddressUtils.extractDistrict("Meclis Mah. Atatürk Cad. C Blok Apt. No:41/K, 34785 Sancaktepe/İstanbul"));
    }

    @Test
    @DisplayName("Autocomplete adresinde ilçe ilden önceki parça")
    void autocompletePostaKoduyla() {
        // Eskiden son kelime alındığı için "Türkiye" dönüyordu.
        assertEquals("Pendik", AddressUtils.extractDistrict(
                "Zara Sokak, Esenler Mahallesi, Pendik, İstanbul, Marmara Bölgesi, 34899, Türkiye"));
    }

    @Test
    @DisplayName("Autocomplete adresinde posta kodu olmayabilir")
    void autocompletePostaKodsuz() {
        assertEquals("Ataşehir", AddressUtils.extractDistrict(
                "İnönü Mahallesi, Ataşehir, İstanbul, Marmara Bölgesi, Türkiye"));
    }

    @Test
    @DisplayName("Autocomplete adresinde sokak adındaki eğik çizgi yanıltmaz")
    void autocompleteEgikCizgiliSokak() {
        assertEquals("Kartal", AddressUtils.extractDistrict(
                "1453/1 Sokak, Yakacık Mahallesi, Kartal, İstanbul, Marmara Bölgesi, 34876, Türkiye"));
    }

    @Test
    @DisplayName("Seçilen sonuç ilçenin ya da ilin kendisi olabilir")
    void autocompleteIlceVeIlSeviyesi() {
        assertEquals("Pendik", AddressUtils.extractDistrict("Pendik, İstanbul, Marmara Bölgesi, Türkiye"));
        assertEquals("", AddressUtils.extractDistrict("İstanbul, Marmara Bölgesi, Türkiye"));
    }

    @Test
    @DisplayName("Boş adres çökmez")
    void bosAdres() {
        assertEquals("", AddressUtils.extractDistrict(null));
        assertEquals("", AddressUtils.extractDistrict("  "));
    }
}
