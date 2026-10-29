
package pt.unl.fct.di.syspriv;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import pt.unl.fct.di.syspriv.dp.DifferentialPrivacyTool;
import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.entities.HealthData;
import pt.unl.fct.di.syspriv.storage.Hibernate;

public class DPTest {

    @BeforeEach
    public void setUp() {
        Hibernate hibernate = Hibernate.getInstance();

        if (hibernate.sql(
                "SELECT id FROM health_data",
                Long.class
        ).isEmpty()) {

            hibernate.persist(
                    new HealthData(1L, 85, new Date()),
                    new HealthData(2L, 120, new Date()),
                    new HealthData(3L, 160, new Date()),
                    new HealthData(4L, 210, new Date()),
                    new HealthData(5L, 300, new Date())
            );
        }
    }

    @Test
    public void testDifferentialPrivacyQueries() {

        DifferentialPrivacyTool dp =
                new DifferentialPrivacyTool(5.0f);

        assertTrue(
                dp.getDatasetSize() > 0,
                "O tamanho do dataset deve ser maior que zero"
        );

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                dp.countBelowGlucose(1.0f, 150).getResultCode()
        );

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                dp.sumGlucoseValues(1.0f).getResultCode()
        );

        // A média pode falhar devido à contagem com ruído.
        DPResult averageResult =
                dp.averageGlucoseValues(1.0f);

        assertNotNull(averageResult);

        assertNotNull(
                averageResult.getResultCode(),
                "A consulta deve devolver um estado"
        );

        if (averageResult.getResultCode()
                == DPResult.ResultCode.SUCCESS) {

            assertTrue(
                    Double.isFinite(averageResult.getResultValue()),
                    "Uma média bem-sucedida deve ser finita"
            );

        } else {

            assertEquals(
                    DPResult.ResultCode.FAIL,
                    averageResult.getResultCode()
            );
        }

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                dp.histogramGlucoseValues(1.0f).getResultCode()
        );
    }

    @Test
    public void testBudgetExceeded() {

        DifferentialPrivacyTool dp =
                new DifferentialPrivacyTool(1.0f);

        DPResult firstResult =
                dp.countBelowGlucose(1.0f, 150);

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                firstResult.getResultCode()
        );

        DPResult secondResult =
                dp.countBelowGlucose(2.0f, 150);

        assertEquals(
                DPResult.ResultCode.FAIL,
                secondResult.getResultCode(),
                "A consulta deve falhar por orçamento insuficiente"
        );
    }

    @Test
    public void testExtractExperimentalDataForReport() {

        Hibernate hibernate = Hibernate.getInstance();

        List<Double> glucoseValues = hibernate.sql(
                "SELECT CAST(glucose AS REAL) "
                        + "FROM health_data WHERE glucose IS NOT NULL",
                Double.class
        );

        assertFalse(
                glucoseValues.isEmpty(),
                "O dataset não pode estar vazio"
        );

        double realAverage = glucoseValues.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();

        float[] epsilons = {
                0.1f,
                0.5f,
                1.0f,
                5.0f
        };

        int repetitions = 100;

        System.out.println(
                "\n--- VALIDAÇÃO ESTATÍSTICA DA DP ---"
        );

        System.out.printf(
                "Registos: %d | Média real: %.2f%n",
                glucoseValues.size(),
                realAverage
        );

        for (float eps : epsilons) {

            int successes = 0;
            int failures = 0;

            double totalAbsoluteError = 0.0;
            double totalSquaredError = 0.0;

            for (int i = 0; i < repetitions; i++) {

                // Cada execução utiliza orçamento independente.
                DifferentialPrivacyTool dp =
                        new DifferentialPrivacyTool(eps);

                DPResult result =
                        dp.averageGlucoseValues(eps);

                if (result == null
                        || result.getResultCode()
                        != DPResult.ResultCode.SUCCESS) {

                    failures++;
                    continue;
                }

                double noisyAverage =
                        result.getResultValue();

                if (!Double.isFinite(noisyAverage)) {
                    failures++;
                    continue;
                }

                double error =
                        noisyAverage - realAverage;

                totalAbsoluteError += Math.abs(error);
                totalSquaredError += error * error;

                successes++;
            }

            double mae = successes > 0
                    ? totalAbsoluteError / successes
                    : Double.NaN;

            double mse = successes > 0
                    ? totalSquaredError / successes
                    : Double.NaN;

            System.out.printf(
                    "Epsilon = %.1f | "
                            + "Sucessos = %d | "
                            + "Falhas = %d | "
                            + "MAE = %.2f | "
                            + "MSE = %.2f%n",
                    eps,
                    successes,
                    failures,
                    mae,
                    mse
            );

            assertEquals(
                    repetitions,
                    successes + failures,
                    "Todas as execuções devem ser contabilizadas"
            );
        }

        System.out.println(
                "--- FIM DA VALIDAÇÃO ---"
        );
    }

    @Test
    public void testSplitAndOriginalBudget() {

        DifferentialPrivacyTool original =
                new DifferentialPrivacyTool(2.0f);

        int originalSize = original.getDatasetSize();

        assertTrue(
                originalSize >= 2,
                "São necessários pelo menos dois registos"
        );

        DifferentialPrivacyTool[] partitions =
                original.split(original.getPatientIds().size() / 2);

        assertEquals(2, partitions.length);

        assertEquals(
                originalSize,
                partitions[0].getDatasetSize()
                        + partitions[1].getDatasetSize(),
                "As partições devem conter todos os registos"
        );

        // O objeto original não deve permitir novas consultas.
        DPResult originalResult =
                original.countBelowGlucose(0.5f, 150);

        assertEquals(
                DPResult.ResultCode.FAIL,
                originalResult.getResultCode(),
                "O objeto original deve ficar bloqueado"
        );

        // Cada partição deve poder utilizar o seu orçamento.
        assertEquals(
                DPResult.ResultCode.SUCCESS,
                partitions[0]
                        .countBelowGlucose(1.0f, 150)
                        .getResultCode()
        );

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                partitions[1]
                        .countBelowGlucose(1.0f, 150)
                        .getResultCode()
        );
    }

    @Test
    public void testParallelCompositionPatientDisjointness() {

        DifferentialPrivacyTool original =
                new DifferentialPrivacyTool(2.0f);

        List<Long> allPatients = original.getPatientIds();

        assertTrue(
                allPatients.size() >= 2,
                "São necessários pelo menos dois pacientes"
        );

        DifferentialPrivacyTool[] partitions =
                original.split(1);

        List<Long> leftPatients =
                partitions[0].getPatientIds();

        List<Long> rightPatients =
                partitions[1].getPatientIds();

        // Nenhum paciente pode estar nas duas partições.
        for (Long patientId : leftPatients) {
            assertFalse(
                    rightPatients.contains(patientId),
                    "O paciente " + patientId
                            + " aparece nas duas partições"
            );
        }

        // Todos os pacientes devem continuar representados.
        assertEquals(
                allPatients.size(),
                leftPatients.size() + rightPatients.size()
        );

        // O objeto original fica bloqueado.
        assertEquals(
                0.0f,
                original.getPrivacyBudget(),
                0.0001f
        );

        // Cada partição recebe o orçamento disponível.
        assertEquals(
                2.0f,
                partitions[0].getPrivacyBudget(),
                0.0001f
        );

        assertEquals(
                2.0f,
                partitions[1].getPrivacyBudget(),
                0.0001f
        );

        // Cada partição pode gastar epsilon = 1.
        assertEquals(
                DPResult.ResultCode.SUCCESS,
                partitions[0]
                        .countBelowGlucose(1.0f, 150)
                        .getResultCode()
        );

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                partitions[1]
                        .countBelowGlucose(1.0f, 150)
                        .getResultCode()
        );

        System.out.println(
                "Composição paralela: pacientes disjuntos"
        );

        System.out.println(
                "Partição 1: " + leftPatients
        );

        System.out.println(
                "Partição 2: " + rightPatients
        );
    }
    @Test
    public void testSequentialCompositionValidation() {

        DifferentialPrivacyTool dp =
                new DifferentialPrivacyTool(2.0f);

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                dp.countBelowGlucose(0.5f, 150).getResultCode()
        );

        assertEquals(1.5f, dp.getPrivacyBudget(), 0.0001f);

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                dp.sumGlucoseValues(1.0f).getResultCode()
        );

        assertEquals(0.5f, dp.getPrivacyBudget(), 0.0001f);

        System.out.println("T1 PASSOU: composicao sequencial");
    }
    @Test
    public void testAverageValidation() {

        DifferentialPrivacyTool dp =
                new DifferentialPrivacyTool(2.0f);

        DPResult result = dp.averageGlucoseValues(1.0f);

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                result.getResultCode()
        );

        assertTrue(Float.isFinite(result.getResultValue()));

        assertEquals(1.0f, dp.getPrivacyBudget(), 0.0001f);

        System.out.println("T2 PASSOU: media com ruido");
    }
    @Test
    public void testHistogramValidation() {

        DifferentialPrivacyTool dp =
                new DifferentialPrivacyTool(2.0f);

        DPResult result = dp.histogramGlucoseValues(1.0f);

        assertEquals(
                DPResult.ResultCode.SUCCESS,
                result.getResultCode()
        );

        assertNotNull(result.getHistogramResultValue());

        assertEquals(6, result.getHistogramResultValue().size());

        assertTrue(
                result.getHistogramResultValue()
                        .values()
                        .stream()
                        .allMatch(Float::isFinite)
        );

        assertEquals(1.0f, dp.getPrivacyBudget(), 0.0001f);

        System.out.println("T3 PASSOU: histograma");
    }

}
