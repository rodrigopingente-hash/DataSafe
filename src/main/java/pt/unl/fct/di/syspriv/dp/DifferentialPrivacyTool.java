package pt.unl.fct.di.syspriv.dp;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.storage.Hibernate;

public class DifferentialPrivacyTool {

    private static final double MIN_GLUCOSE = 0.0;
    private static final double MAX_GLUCOSE = 600.0;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private float privacyBudget;

    private final PrivacyBudgetManager budgetManager;

    private final Hibernate hibernate;

    private final List<Long> recordIds;

    // Auxiliary attribute to ensure queries only use the records in this dataset chunk.
    private final String idsStr;

    public DifferentialPrivacyTool(float privacyBudget) {
        this.privacyBudget = privacyBudget;
        this.budgetManager = new PrivacyBudgetManager(privacyBudget);
        this.hibernate = Hibernate.getInstance();
        this.recordIds = List.copyOf(hibernate.sql("SELECT id FROM health_data", Long.class));
        this.idsStr = concatenateRecordIds(this.recordIds);
    }

    public DifferentialPrivacyTool(float privacyBudget, List<Long> recordIds) {
        this.privacyBudget = privacyBudget;
        this.budgetManager = new PrivacyBudgetManager(privacyBudget);
        this.hibernate = Hibernate.getInstance();
        this.recordIds = List.copyOf(recordIds);
        this.idsStr = concatenateRecordIds(this.recordIds);
    }

    private static String concatenateRecordIds(List<Long> recordIds) {
        var idsStr = recordIds.stream().map("'%d'"::formatted).toList();
        return String.join(",", idsStr);
    }
    private List<Integer> getGlucoseValuesOnePerPatient() {

        List<Integer> values = new ArrayList<>();

        if (recordIds.isEmpty()) {
            return values;
        }

        List<Long> selectedIds = hibernate.sql("""
            SELECT MIN(id)
            FROM health_data
            WHERE id IN (%s)
            AND patientId IS NOT NULL
            AND glucose IS NOT NULL
            GROUP BY patientId
            """.formatted(idsStr), Long.class);

        for (Long id : selectedIds) {

            List<Integer> glucoseValues = hibernate.sql(
                    "SELECT glucose FROM health_data "
                            + "WHERE id = " + id
                            + " AND glucose IS NOT NULL",
                    Integer.class
            );

            if (!glucoseValues.isEmpty()) {
                values.add(glucoseValues.get(0));
            }
        }

        return values;
    }

    public DifferentialPrivacyTool[] split(int cutoffIdx) {

        if (privacyBudget <= 0 ||
                budgetManager.getRemainingBudget() <= 0) {
            throw new IllegalStateException(
                    "O objeto já foi dividido ou não tem orçamento disponível"
            );
        }

        // Agrupar os registos pelo identificador do paciente.
        Map<Long, List<Long>> patientRecords = new LinkedHashMap<>();

        for (Long recordId : recordIds) {

            List<Long> patientIds = hibernate.sql(
                    "SELECT patientId FROM health_data WHERE id = "
                            + recordId,
                    Long.class
            );

            if (patientIds.isEmpty() || patientIds.get(0) == null) {
                throw new IllegalStateException(
                        "Registo sem paciente válido: " + recordId
                );
            }

            Long patientId = patientIds.get(0);

            patientRecords
                    .computeIfAbsent(
                            patientId,
                            key -> new ArrayList<>()
                    )
                    .add(recordId);
        }

        List<Long> patients =
                new ArrayList<>(patientRecords.keySet());

        // cutoffIdx representa agora o número de pacientes
        // que ficam na primeira partição.
        if (cutoffIdx <= 0 || cutoffIdx >= patients.size()) {
            throw new IllegalArgumentException(
                    "O índice deve dividir os pacientes "
                            + "em duas partições não vazias"
            );
        }

        List<Long> leftRecords = new ArrayList<>();
        List<Long> rightRecords = new ArrayList<>();

        for (int i = 0; i < patients.size(); i++) {

            Long patientId = patients.get(i);

            if (i < cutoffIdx) {
                leftRecords.addAll(patientRecords.get(patientId));
            } else {
                rightRecords.addAll(patientRecords.get(patientId));
            }
        }

        float remainingBudget =
                budgetManager.getRemainingBudget();

        DifferentialPrivacyTool left =
                new DifferentialPrivacyTool(
                        remainingBudget,
                        leftRecords
                );

        DifferentialPrivacyTool right =
                new DifferentialPrivacyTool(
                        remainingBudget,
                        rightRecords
                );

        // O objeto original deixa de aceitar consultas.
        this.privacyBudget = 0;

        return new DifferentialPrivacyTool[]{left, right};
    }

    // TODO Right now this method does not provide Differential Privacy. Change it
    public DPResult sumGlucoseValues(float privacyCost) {

        DPResult res = new DPResult();

        res.setResultType(DPResult.ResultType.SUM);
        res.setQuerySensitivity((float) (MAX_GLUCOSE - MIN_GLUCOSE));
        res.setClippingLower((float) MIN_GLUCOSE);
        res.setClippingUpper((float) MAX_GLUCOSE);
        res.setPrivacyBudget(budgetManager.getRemainingBudget());

        if (!hasBudget(privacyCost)) {
            res.setResultValue(Float.NaN);
            return res;
        }

        double sum = 0;

        List<Integer> values = getGlucoseValuesOnePerPatient();

        for (Integer value : values) {
            sum += clip(value.doubleValue());
        }

        double sensitivity = MAX_GLUCOSE - MIN_GLUCOSE;
        double noisySum = sum + laplace(sensitivity, privacyCost);



        res.setResultValue((float) noisySum);
        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(budgetManager.getRemainingBudget());

        return res;
    }

    /*
     * TODO Right now this method does not provide Differential Privacy. Change it
     *  Hint: It may help to consider that the average of a collection is: avg(col) = sum(col) / len(col)
     */
    public DPResult countBelowGlucose(float privacyCost, int upperGlucose) {

        DPResult res = new DPResult();

        res.setResultType(DPResult.ResultType.COUNT);
        res.setQuerySensitivity(1.0f);
        res.setPrivacyBudget(budgetManager.getRemainingBudget());

        if (!hasBudget(privacyCost)) {
            res.setResultValue(Float.NaN);
            return res;
        }

        double count = 0;
        List<Integer> values = getGlucoseValuesOnePerPatient();
        for (Integer value : values) {
            if (clip(value.doubleValue()) < upperGlucose) {
                count++;
            }
        }

        double noisyCount = count + laplace(1.0, privacyCost);



        res.setResultValue((float) noisyCount);
        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(budgetManager.getRemainingBudget());

        return res;
    }

    public DPResult averageGlucoseValues(float privacyCost) {

        DPResult res = new DPResult();

        res.setResultType(DPResult.ResultType.AVERAGE);
        res.setClippingLower((float) MIN_GLUCOSE);
        res.setClippingUpper((float) MAX_GLUCOSE);
        res.setPrivacyBudget(budgetManager.getRemainingBudget());

        if (!hasBudget(privacyCost)) {
            return res;
        }

        double sum = 0;
        int count = 0;
        List<Integer> values = getGlucoseValuesOnePerPatient();
        for (Integer value : values) {
            sum += clip(value.doubleValue());
            count++;
        }

        double halfEpsilon = privacyCost / 2.0;

        double noisySum = sum
                + laplace(MAX_GLUCOSE - MIN_GLUCOSE, halfEpsilon);

        double noisyCount = count
                + laplace(1.0, halfEpsilon);

        double safeCount = Math.max(noisyCount, 1.0);
        double noisyAverage = noisySum / safeCount;

        res.setResultValue((float) noisyAverage);
        res.setResultCode(
                Double.isFinite(noisyAverage)
                        ? DPResult.ResultCode.SUCCESS
                        : DPResult.ResultCode.FAIL
        );
        res.setPrivacyBudget(budgetManager.getRemainingBudget());

        return res;
    }


    public DPResult histogramGlucoseValues(float privacyCost) {

        DPResult res = new DPResult();

        res.setResultType(DPResult.ResultType.HISTOGRAM);
        res.setQuerySensitivity(1.0f);
        res.setClippingLower((float) MIN_GLUCOSE);
        res.setClippingUpper((float) MAX_GLUCOSE);
        res.setPrivacyBudget(budgetManager.getRemainingBudget());

        if (!hasBudget(privacyCost)) {
            return res;
        }

        Map<String, Float> histogram = new LinkedHashMap<>();

        int[] counts = new int[6];

        List<Integer> values = getGlucoseValuesOnePerPatient();
        for (Integer value : values) {
            double glucose = clip(value.doubleValue());
            int index = Math.min((int) (glucose / 100), 5);
            counts[index]++;
        }

        for (int i = 0; i < counts.length; i++) {

            String interval = (i * 100) + "-" + ((i + 1) * 100);

            double noisyCount = counts[i] + laplace(1.0, privacyCost);

            histogram.put(interval, (float) noisyCount);
        }



        res.setHistogramResultValue(histogram);
        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(budgetManager.getRemainingBudget());

        return res;
    }




    private boolean hasBudget(float cost) {

        if (privacyBudget <= 0) {
            return false;
        }

        return budgetManager.consume(cost);
    }

    private double laplace(double sensitivity, double epsilon) {
        double u = SECURE_RANDOM.nextDouble() - 0.5;

        return -sensitivity / epsilon
                * Math.signum(u)
                * Math.log1p(-2 * Math.abs(u));
    }

    private double clip(double value) {
        return Math.max(MIN_GLUCOSE,
                Math.min(MAX_GLUCOSE, value));
    }

    // Getters and Setters
    /**
     * Get privacyBudget.
     *
     * @return privacyBudget as float.
     */
    public float getPrivacyBudget() {
        if (privacyBudget <= 0) {
            return 0.0f;
        }

        return budgetManager.getRemainingBudget();
    }
    public int getDatasetSize() {
        return recordIds.size();
    }
    public List<Long> getPatientIds() {

        List<Long> patientIds = new ArrayList<>();

        for (Long recordId : recordIds) {

            List<Long> result = hibernate.sql(
                    "SELECT patientId FROM health_data WHERE id = "
                            + recordId,
                    Long.class
            );

            if (!result.isEmpty() && result.get(0) != null) {
                patientIds.add(result.get(0));
            }
        }

        return patientIds.stream().distinct().toList();
    }
}
