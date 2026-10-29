package pt.unl.fct.di.syspriv.kanonymity;

import java.util.*;
import pt.unl.fct.di.syspriv.anonymity.Anonymizable;
import pt.unl.fct.di.syspriv.entities.AnonymousHealthData;

/**
 * Mondrian multidimensional com k-anonymity e, opcionalmente, t-closeness.
 * Para atributos sensiveis numericos, t-closeness utiliza a EMD normalizada
 * pela amplitude observada na distribuicao global.
 */
public class KAnonymityTool<T extends Anonymizable> {

    private final int k;
    private final float t;

    public KAnonymityTool(int k, float t) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
        if (!Float.isFinite(t) || t < 0.0f || t > 1.0f) {
            throw new IllegalArgumentException("t must be finite and between 0 and 1");
        }
        this.k = k;
        this.t = t;
    }

    public boolean isKAnonymous(List<T> records) {
        if (records == null || records.isEmpty()) {
            return false;
        }
        Map<Map<String, Object>, Integer> groups = new HashMap<>();
        for (T record : records) {
            if (record == null || record.getQuasiIdentifiers() == null) {
                return false;
            }
            Map<String, Object> qi = new HashMap<>(record.getQuasiIdentifiers());
            groups.merge(qi, 1, Integer::sum);
        }
        for (int count : groups.values()) {
            if (count < k) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compara uma particao P com a populacao de referencia Q usando
     * Earth Mover's Distance para uma variavel sensivel numerica ordenada.
     * A distancia e normalizada por max(Q)-min(Q), ficando em [0,1]
     * quando P e um subconjunto de Q.
     */
    public boolean isTClose(List<T> P, List<T> Q) {
        if (P == null || Q == null || P.isEmpty() || Q.isEmpty()) {
            return false;
        }

        TreeSet<Double> support = new TreeSet<>();
        Map<Double, Integer> countP = new HashMap<>();
        Map<Double, Integer> countQ = new HashMap<>();

        for (T record : P) {
            Double value = numericSensitiveValue(record);
            if (value == null) return false;
            support.add(value);
            countP.merge(value, 1, Integer::sum);
        }
        for (T record : Q) {
            Double value = numericSensitiveValue(record);
            if (value == null) return false;
            support.add(value);
            countQ.merge(value, 1, Integer::sum);
        }

        double globalMin = Double.POSITIVE_INFINITY;
        double globalMax = Double.NEGATIVE_INFINITY;
        for (Double value : countQ.keySet()) {
            globalMin = Math.min(globalMin, value);
            globalMax = Math.max(globalMax, value);
        }
        double range = globalMax - globalMin;
        if (range == 0.0) {
            // Distancia zero apenas se P tambem for constante nesse valor.
            return support.size() == 1;
        }

        double cumulativeDifference = 0.0;
        double distance = 0.0;
        Double previous = null;
        for (Double value : support) {
            if (previous != null) {
                distance += Math.abs(cumulativeDifference) * (value - previous);
            }
            cumulativeDifference += countP.getOrDefault(value, 0) / (double) P.size()
                    - countQ.getOrDefault(value, 0) / (double) Q.size();
            previous = value;
        }
        double normalizedEmd = distance / range;
        return Double.isFinite(normalizedEmd) && normalizedEmd <= t + 1e-9;
    }

    private Double numericSensitiveValue(T record) {
        if (record == null || !(record.getSensitiveValue() instanceof Number)) {
            return null;
        }
        double value = ((Number) record.getSensitiveValue()).doubleValue();
        return Double.isFinite(value) ? value : null;
    }

    public Map<Map<String, Object>, Set<Object>> sensitiveValuesPerGroup(List<T> records) {
        Map<Map<String, Object>, Set<Object>> groups = new HashMap<>();
        if (records == null) return groups;
        for (T record : records) {
            if (record == null || record.getQuasiIdentifiers() == null) {
                throw new IllegalArgumentException("Record or quasi-identifiers cannot be null");
            }
            Map<String, Object> qi = new HashMap<>(record.getQuasiIdentifiers());
            groups.computeIfAbsent(qi, key -> new HashSet<>())
                    .add(record.getSensitiveValue());
        }
        return groups;
    }

    public List<T> runMondrain(List<T> records) {
        return runMondrianInternal(records, false);
    }

    public List<T> runTCloseMondrain(List<T> records) {
        return runMondrianInternal(records, true);
    }

    private List<T> runMondrianInternal(List<T> records, boolean enforceTCloseness) {
        if (records == null || records.size() < k) {
            throw new IllegalArgumentException("Not enough records for k-anonymity");
        }
        List<T> result = new ArrayList<>();
        for (T record : records) {
            if (!(record instanceof AnonymousHealthData)) {
                throw new IllegalArgumentException("Mondrian requires AnonymousHealthData");
            }
            AnonymousHealthData original = (AnonymousHealthData) record;
            if (!Float.isFinite(original.getPatientBirthYear())
                    || !Float.isFinite(original.getPatientPostalCode())
                    || !Float.isFinite(original.getDiagnosisDate())
                    || !Float.isFinite(original.getGlucose())) {
                throw new IllegalArgumentException("All numerical attributes must be finite");
            }
            AnonymousHealthData copy = new AnonymousHealthData();
            copy.setGlucose(original.getGlucose());
            copy.setPatientBirthYear(original.getPatientBirthYear());
            copy.setPatientPostalCode(original.getPatientPostalCode());
            copy.setDiagnosisYear(original.getDiagnosisDate());
            // Direct identifier intentionally not copied.
            @SuppressWarnings("unchecked")
            T anonymized = (T) copy;
            result.add(anonymized);
        }

        List<List<T>> partitions = new ArrayList<>();
        partitionRecords(result, result, partitions, enforceTCloseness);
        for (List<T> partition : partitions) {
            generalizePartition(partition);
        }
        if (!isKAnonymous(result)) {
            throw new IllegalStateException("Result is not k-anonymous");
        }
        if (enforceTCloseness) {
            // Validar os grupos de equivalencia finais, e nao apenas as
            // particoes internas usadas pelo algoritmo.
            Map<Map<String, Object>, List<T>> groups = new HashMap<>();
            for (T record : result) {
                Map<String, Object> qi = new HashMap<>(record.getQuasiIdentifiers());
                groups.computeIfAbsent(qi, key -> new ArrayList<>()).add(record);
            }
            for (List<T> group : groups.values()) {
                if (!isTClose(group, result)) {
                    throw new IllegalStateException("Equivalence group violates t-closeness");
                }
            }
        }
        return result;
    }

    private void partitionRecords(List<T> partition, List<T> original,
                                  List<List<T>> result, boolean enforceTCloseness) {
        if (partition.size() < 2 * k) {
            result.add(partition);
            return;
        }
        List<Integer> dimensions = new ArrayList<>(Arrays.asList(0, 1, 2));
        double[] ranges = new double[3];
        for (int dimension = 0; dimension < 3; dimension++) {
            double globalMin = Double.POSITIVE_INFINITY;
            double globalMax = Double.NEGATIVE_INFINITY;
            for (T record : original) {
                double value = getDimension(record, dimension);
                globalMin = Math.min(globalMin, value);
                globalMax = Math.max(globalMax, value);
            }
            double globalRange = globalMax - globalMin;
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (T record : partition) {
                double value = getDimension(record, dimension);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            ranges[dimension] = globalRange == 0.0 ? 0.0 : (max - min) / globalRange;
        }
        dimensions.sort((a, b) -> Double.compare(ranges[b], ranges[a]));
        for (int dimension : dimensions) {
            if (ranges[dimension] == 0.0) continue;
            List<T> sorted = new ArrayList<>(partition);
            sorted.sort(Comparator.comparingDouble(record -> getDimension(record, dimension)));
            int middle = sorted.size() / 2;
            while (middle < sorted.size() && middle > 0
                    && getDimension(sorted.get(middle - 1), dimension)
                    == getDimension(sorted.get(middle), dimension)) {
                middle++;
            }
            if (middle < k || sorted.size() - middle < k) continue;
            List<T> left = new ArrayList<>(sorted.subList(0, middle));
            List<T> right = new ArrayList<>(sorted.subList(middle, sorted.size()));
            if (enforceTCloseness
                    && (!isTClose(left, original) || !isTClose(right, original))) {
                continue;
            }
            partitionRecords(left, original, result, enforceTCloseness);
            partitionRecords(right, original, result, enforceTCloseness);
            return;
        }
        result.add(partition);
    }

    private double getDimension(T record, int dimension) {
        AnonymousHealthData data = (AnonymousHealthData) record;
        switch (dimension) {
            case 0: return data.getPatientBirthYear();
            case 1: return data.getPatientPostalCode();
            case 2: return data.getDiagnosisDate();
            default: throw new IllegalArgumentException("Unknown dimension");
        }
    }

    private void generalizePartition(List<T> partition) {
        for (int dimension = 0; dimension < 3; dimension++) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (T record : partition) {
                double value = getDimension(record, dimension);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            String generalizedValue = min == max
                    ? String.valueOf((int) min)
                    : (int) min + "-" + (int) max;
            for (T record : partition) {
                AnonymousHealthData data = (AnonymousHealthData) record;
                switch (dimension) {
                    case 0: data.setGeneralizedBirthYear(generalizedValue); break;
                    case 1: data.setGeneralizedPostalCode(generalizedValue); break;
                    case 2: data.setGeneralizedDiagnosisYear(generalizedValue); break;
                    default: throw new IllegalArgumentException("Unknown dimension");
                }
            }
        }
    }
}
