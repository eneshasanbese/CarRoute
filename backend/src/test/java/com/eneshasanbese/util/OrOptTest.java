package com.eneshasanbese.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Or-opt'un kazancı turu baştan toplamadan buluyor. Kazanç, üç bacağın
 * değişiminden çıkarılıyor — bu aritmetik yanlış olsaydı arama turu
 * <em>uzatan</em> hamleleri kabul eder ve kimse fark etmezdi, çünkü sonuç yine
 * geçerli bir sıralama olurdu.
 *
 * <p>
 * Bu yüzden testler artımlı hesabı, aynı hamleyi elle kurup turu baştan toplayan
 * bağımsız bir uygulamayla karşılaştırıyor. Maliyet matrisi bilerek
 * <b>asimetrik</b>: gerçek yol matrisi de öyle ve A→B ile B→A'yı karıştıran bir
 * hata ancak burada yakalanır.
 */
class OrOptTest {

    private static final int NODES = 12;
    private static final int START = 0;
    private static final int END = NODES - 1;

    // ------------------------------------------------------------- testler

    @Test
    @DisplayName("Uygulanan hamle turu her zaman kısaltır")
    void hamleTuruKisaltir() {
        for (int seed = 0; seed < 50; seed++) {
            double[][] matrix = asymmetricMatrix(seed);
            List<Integer> order = randomOrder(seed);

            double before = tourCost(order, matrix);
            boolean moved = OrOpt.applyBestMove(order, START, END, legCost(matrix));

            if (moved) {
                assertTrue(tourCost(order, matrix) < before,
                        "seed " + seed + ": hamle uygulandıysa tur kısalmalı");
            }
        }
    }

    @Test
    @DisplayName("Seçilen hamle, elle denenen bütün hamlelerin en iyisiyle aynı")
    void enIyiHamleyiSecer() {
        for (int seed = 0; seed < 50; seed++) {
            double[][] matrix = asymmetricMatrix(seed);
            List<Integer> order = randomOrder(seed);

            double beklenen = bestCostByBruteForce(order, matrix);

            List<Integer> uygulanan = new ArrayList<>(order);
            OrOpt.applyBestMove(uygulanan, START, END, legCost(matrix));

            assertEquals(beklenen, tourCost(uygulanan, matrix), 1e-9,
                    "seed " + seed + ": artımlı kazanç kaba kuvvetle aynı hamleyi seçmeli");
        }
    }

    @Test
    @DisplayName("Arama bittiğinde hiçbir tek hamle turu kısaltamaz")
    void yerelOptimumdaDurur() {
        for (int seed = 0; seed < 50; seed++) {
            double[][] matrix = asymmetricMatrix(seed);
            List<Integer> order = randomOrder(seed);

            OrOpt.improve(order, START, END, legCost(matrix), 100);

            double son = tourCost(order, matrix);
            assertEquals(son, bestCostByBruteForce(order, matrix), 1e-9,
                    "seed " + seed + ": arama durduysa kazandıran hamle kalmamalı");
        }
    }

    @Test
    @DisplayName("Arama turu asla uzatmaz")
    void aramaTuruUzatmaz() {
        for (int seed = 0; seed < 50; seed++) {
            double[][] matrix = asymmetricMatrix(seed);
            List<Integer> order = randomOrder(seed);

            double before = tourCost(order, matrix);
            OrOpt.improve(order, START, END, legCost(matrix), 100);

            assertTrue(tourCost(order, matrix) <= before + 1e-9, "seed " + seed);
        }
    }

    @Test
    @DisplayName("Duraklar korunur: hiçbiri kaybolmaz, çoğalmaz, sırası bozulmaz")
    void duraklarKorunur() {
        for (int seed = 0; seed < 50; seed++) {
            List<Integer> order = randomOrder(seed);
            List<Integer> beklenen = order.stream().sorted().toList();

            OrOpt.improve(order, START, END, legCost(asymmetricMatrix(seed)), 100);

            assertEquals(beklenen, order.stream().sorted().toList(), "seed " + seed);
        }
    }

    @Test
    @DisplayName("Tek duraklı turda hamle yok")
    void tekDurak() {
        List<Integer> order = new ArrayList<>(List.of(1));
        assertFalse(OrOpt.applyBestMove(order, START, END, legCost(asymmetricMatrix(0))));
        assertEquals(List.of(1), order);
    }

    // ----------------------------------------------------------- yardımcı

    /**
     * Aynı hamle kümesini elle deneyen bağımsız uygulama: her aday tur baştan
     * toplanır. Artımlı hesabın ölçüldüğü referans budur.
     */
    private double bestCostByBruteForce(List<Integer> order, double[][] matrix) {
        double best = tourCost(order, matrix);
        int size = order.size();
        int maxSegment = Math.min(OrOpt.MAX_SEGMENT, size - 1);

        for (int length = 1; length <= maxSegment; length++) {
            for (int from = 0; from + length <= size; from++) {
                List<Integer> rest = new ArrayList<>(order);
                List<Integer> segment = new ArrayList<>(rest.subList(from, from + length));
                rest.subList(from, from + length).clear();

                for (int to = 0; to <= rest.size(); to++) {
                    List<Integer> candidate = new ArrayList<>(rest);
                    candidate.addAll(to, segment);

                    best = Math.min(best, tourCost(candidate, matrix));
                }
            }
        }

        return best;
    }

    /** Turun tamamı: kalkış → duraklar → varış. Biniş süresi sabit olduğu için yok. */
    private double tourCost(List<Integer> order, double[][] matrix) {
        double total = 0;
        int current = START;

        for (int node : order) {
            total += matrix[current][node];
            current = node;
        }

        return total + matrix[current][END];
    }

    private OrOpt.LegCost legCost(double[][] matrix) {
        return (from, to) -> matrix[from][to];
    }

    /** A→B ile B→A bilerek farklı — tek yönler ve köprü çıkışları gibi. */
    private double[][] asymmetricMatrix(int seed) {
        Random random = new Random(seed);
        double[][] matrix = new double[NODES][NODES];

        for (int from = 0; from < NODES; from++) {
            for (int to = 0; to < NODES; to++) {
                matrix[from][to] = from == to ? 0 : 1 + random.nextDouble() * 40;
            }
        }

        return matrix;
    }

    /** 1..NODES-2 arası duraklar, karıştırılmış. Uçlar sabit. */
    private List<Integer> randomOrder(int seed) {
        List<Integer> order = new ArrayList<>();
        for (int node = 1; node < END; node++) {
            order.add(node);
        }
        java.util.Collections.shuffle(order, new Random(seed + 1000));
        return order;
    }
}
