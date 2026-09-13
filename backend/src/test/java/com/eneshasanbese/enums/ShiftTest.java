package com.eneshasanbese.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShiftTest {

    @Test
    @DisplayName("Arayüzden gelen değerler sefere çevrilir")
    void arayuzDegerleriCevrilir() {
        assertEquals(Shift.SABAH, Shift.of("sabah"));
        assertEquals(Shift.AKSAM, Shift.of("aksam"));
        assertEquals(Shift.AKSAM, Shift.of("akşam"));
        assertEquals(Shift.SABAH, Shift.of("SABAH"));
        assertEquals(Shift.AKSAM, Shift.of("  Aksam  "));
    }

    @Test
    @DisplayName("Parametre verilmezse sabah varsayılır — mevcut çağrılar bozulmasın")
    void varsayilanSabah() {
        assertEquals(Shift.SABAH, Shift.of(null));
        assertEquals(Shift.SABAH, Shift.of(""));
        assertEquals(Shift.SABAH, Shift.of("   "));
    }

    @Test
    @DisplayName("Tanınmayan sefer sessizce sabaha düşmez, hata verir")
    void gecersizDegerHataVerir() {
        assertThrows(IllegalArgumentException.class, () -> Shift.of("ogle"));
    }

    @Test
    @DisplayName("Etiket API sözleşmesindeki değerlerle aynı")
    void etiketler() {
        assertEquals("sabah", Shift.SABAH.label());
        assertEquals("aksam", Shift.AKSAM.label());
    }
}
