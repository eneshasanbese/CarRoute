package com.eneshasanbese.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Or-opt yerel araması: 1-3 duraklık bir parçayı <b>yönünü bozmadan</b> turun
 * başka bir yerine taşır.
 *
 * <p>
 * <b>Neden bu hamle.</b> Klasik 2-opt bir parçayı ters çevirir. Kuş uçuşu
 * maliyet simetrik olduğu için orada bedavaydı, ama gerçek yol matrisi simetrik
 * değil — ters çevirme parçanın içindeki her bacağı da ters yöne çeviriyor ve o
 * yön çok daha pahalı olabiliyor (ölçülen bir örnekte Şahin→Cansu 10.76 km,
 * Cansu→Şahin 22.47 km). 2-opt tek başına bu yüzden asimetrik matriste yerel
 * optimumda kilitleniyordu. Or-opt hiçbir bacağı ters çevirmediği için
 * asimetrik maliyetle doğru çalışan hamle budur.
 *
 * <p>
 * <b>Kazanç tur yeniden hesaplanmadan bulunur.</b> Parçayı taşımak yalnızca üç
 * bacağı değiştirir: parçanın iki ucunun eski komşuluğu kopar, arkasında kalan
 * boşluk kapanır ve parçanın girdiği yer açılır. Parçanın <i>içindeki</i>
 * bacaklar ve durak sayısı (dolayısıyla biniş süreleri) aynı kaldığı için bu
 * fark birebir doğrudur. Turu her aday için baştan toplamak
 * {@code AssignmentService}'in on binlerce yük tahminini dakikalara çıkarıyordu.
 *
 * <p>
 * Uçlar sabittir: {@code startNode} ve {@code endNode} yerinden oynamaz, yalnızca
 * aradaki duraklar sıralanır.
 */
public final class OrOpt {

    /** Taşınabilecek en uzun durak dizisi. */
    public static final int MAX_SEGMENT = 3;
    /** Bir hamlenin kabul edilmesi için gereken en az kazanç. */
    private static final double EPSILON = 1e-9;

    /** İki düğüm arası maliyet. Kaynağı yol matrisi ya da kuş uçuşu tahmindir. */
    @FunctionalInterface
    public interface LegCost {
        double minutes(int from, int to);
    }

    private OrOpt() {
    }

    /**
     * Kazanç kalmayana kadar en iyi hamleyi uygular. {@code order} yerinde
     * değiştirilir.
     *
     * @param maxPasses üst sınır; arama normalde çok daha önce durur, bu yalnızca
     *                  emniyet
     * @return tur değiştiyse {@code true}
     */
    public static boolean improve(List<Integer> order, int startNode, int endNode,
            LegCost cost, int maxPasses) {

        boolean anyImprovement = false;

        for (int pass = 0; pass < maxPasses; pass++) {
            if (!applyBestMove(order, startNode, endNode, cost)) {
                return anyImprovement;
            }
            anyImprovement = true;
        }

        return anyImprovement;
    }

    /**
     * En çok kazandıran tek hamleyi uygular; kazandıran hamle yoksa
     * {@code false}.
     */
    static boolean applyBestMove(List<Integer> order, int startNode, int endNode, LegCost cost) {
        int size = order.size();
        if (size < 2) {
            return false;
        }

        double bestGain = EPSILON;
        int bestFrom = -1;
        int bestLength = 0;
        int bestTo = -1;

        int maxSegment = Math.min(MAX_SEGMENT, size - 1);

        for (int length = 1; length <= maxSegment; length++) {
            for (int from = 0; from + length <= size; from++) {
                int head = order.get(from);
                int tail = order.get(from + length - 1);

                int previous = from == 0 ? startNode : order.get(from - 1);
                int next = from + length == size ? endNode : order.get(from + length);

                // Parçayı çıkarmanın kazancı: iki uç bacak gider, boşluk kapanır.
                double removalGain = cost.minutes(previous, head)
                        + cost.minutes(tail, next)
                        - cost.minutes(previous, next);

                int restSize = size - length;
                for (int to = 0; to <= restSize; to++) {
                    if (to == from) {
                        // Aynı yere geri koymak turu değiştirmez.
                        continue;
                    }

                    int left = to == 0 ? startNode : restNode(order, from, length, to - 1);
                    int right = to == restSize ? endNode : restNode(order, from, length, to);

                    double insertion = cost.minutes(left, head)
                            + cost.minutes(tail, right)
                            - cost.minutes(left, right);

                    double gain = removalGain - insertion;
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestFrom = from;
                        bestLength = length;
                        bestTo = to;
                    }
                }
            }
        }

        if (bestFrom < 0) {
            return false;
        }

        apply(order, bestFrom, bestLength, bestTo);
        return true;
    }

    /** Parçayı {@code from} konumundan alıp, kalan turun {@code to} konumuna koyar. */
    static void apply(List<Integer> order, int from, int length, int to) {
        List<Integer> segment = new ArrayList<>(order.subList(from, from + length));
        order.subList(from, from + length).clear();
        order.addAll(to, segment);
    }

    /**
     * Parça çıkarılmış turdaki {@code index}. düğüm — listeyi kopyalamadan,
     * indeksi kaydırarak.
     */
    private static int restNode(List<Integer> order, int from, int length, int index) {
        return order.get(index < from ? index : index + length);
    }
}
