package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.Test;
import pt.unl.fct.di.syspriv.dp.DifferentialPrivacyTool;
import pt.unl.fct.di.syspriv.entities.DPResult;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Integration test against the application's SQLite database. Inserts one temporary
 * duplicate patient record and deletes it in finally. Run only on a disposable/test
 * database: an abrupt process termination can prevent cleanup. */
public class DPIntegrationRepeatedPatientTest {

    private static final String DB_URL = "jdbc:sqlite:privsys.db";
    private static final float EPSILON = 1000f;

    @SuppressWarnings("unchecked")
    private static List<Integer> selectedGlucose(DifferentialPrivacyTool tool) throws Exception {
        Method method = DifferentialPrivacyTool.class
                .getDeclaredMethod("getGlucoseValuesOnePerPatient");
        method.setAccessible(true);
        return (List<Integer>) method.invoke(tool);
    }

    @Test
    void duplicatePatientDoesNotChangeDPContributions() throws Exception {
        Long patientId = null;
        Long insertedId = null;
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT patientId FROM health_data WHERE patientId IS NOT NULL " +
                            "AND glucose IS NOT NULL ORDER BY id LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "A base de dados de teste precisa de um paciente existente");
                patientId = rs.getLong(1);
            }

            // Use a record ID list fixed before inserting the duplicate.
            List<Long> originalIds = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM health_data ORDER BY id")) {
                while (rs.next()) originalIds.add(rs.getLong(1));
            }
            DifferentialPrivacyTool baseline = new DifferentialPrivacyTool(5000f, originalIds);
            List<Integer> expectedValues = selectedGlucose(baseline);
            assertFalse(expectedValues.isEmpty());

            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO health_data (patientId, glucose) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, patientId);
                    insert.setInt(2, 599); // deliberately different reading for same patient
                    assertEquals(1, insert.executeUpdate());
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        assertTrue(keys.next());
                        insertedId = keys.getLong(1);
                    }
                }

                List<Long> idsWithDuplicate = new ArrayList<>(originalIds);
                idsWithDuplicate.add(insertedId);
                DifferentialPrivacyTool tool = new DifferentialPrivacyTool(5000f, idsWithDuplicate);
                assertEquals(originalIds.size() + 1, tool.getDatasetSize());

                // Deterministic assertion on the actual private selection method.
                List<Integer> actualValues = selectedGlucose(tool);
                assertEquals(expectedValues, actualValues,
                        "Um segundo registo do mesmo paciente não deve alterar a seleção");

                double trueSum = actualValues.stream().mapToDouble(Integer::doubleValue).sum();
                double trueCount = actualValues.size();
                double trueAverage = trueSum / trueCount;
                int[] trueHistogram = new int[6];
                for (int glucose : actualValues) {
                    int bucket = Math.min(Math.max(glucose, 0) / 100, 5);
                    trueHistogram[bucket]++;
                }

                // Large epsilon makes Laplace noise very small for this *test only*.
                DPResult sum = tool.sumGlucoseValues(EPSILON);
                DPResult count = tool.countBelowGlucose(EPSILON, 601);
                DPResult average = tool.averageGlucoseValues(EPSILON);
                DPResult histogram = tool.histogramGlucoseValues(EPSILON);

                assertEquals(DPResult.ResultCode.SUCCESS, sum.getResultCode());
                assertEquals(DPResult.ResultCode.SUCCESS, count.getResultCode());
                assertEquals(DPResult.ResultCode.SUCCESS, average.getResultCode());
                assertEquals(DPResult.ResultCode.SUCCESS, histogram.getResultCode());

                assertEquals(trueSum, sum.getResultValue(), 15.0, "SUM inclui duplicados?");
                assertEquals(trueCount, count.getResultValue(), 0.1, "COUNT inclui duplicados?");
                assertEquals(trueAverage, average.getResultValue(), 5.0, "AVERAGE inclui duplicados?");

                Map<String, Float> bins = histogram.getHistogramResultValue();
                assertEquals(6, bins.size());
                for (int i = 0; i < 6; i++) {
                    String label = (i * 100) + "-" + ((i + 1) * 100);
                    assertTrue(bins.containsKey(label), "Intervalo em falta: " + label);
                    assertEquals(trueHistogram[i], bins.get(label), 0.1,
                            "HISTOGRAM inclui duplicados no intervalo " + label + "?");
                }

                System.out.println("Integração DP OK: " + idsWithDuplicate.size()
                        + " registos, " + actualValues.size() + " contribuições únicas");
                System.out.println("SUM=" + sum.getResultValue()
                        + " COUNT=" + count.getResultValue()
                        + " AVG=" + average.getResultValue());
                System.out.println("HISTOGRAM=" + bins);
            } finally {
                if (insertedId != null) {
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM health_data WHERE id = ?")) {
                        delete.setLong(1, insertedId);
                        delete.executeUpdate();
                    }
                }
            }
        }
    }
}
