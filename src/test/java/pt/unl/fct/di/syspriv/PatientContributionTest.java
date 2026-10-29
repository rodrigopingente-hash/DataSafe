package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PatientContributionTest {

    @Test
    public void testOneRecordPerPatient() throws Exception {

        try (Connection conn =
                     DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE health_data (
                    id INTEGER PRIMARY KEY,
                    patientId INTEGER,
                    glucose INTEGER
                )
                """);

            stmt.execute("""
                INSERT INTO health_data VALUES
                (1, 1, 85),
                (2, 1, 120),
                (3, 2, 160),
                (4, 3, 210),
                (5, 4, 300)
                """);

            List<Integer> selectedValues = new ArrayList<>();

            String sql = """
                SELECT glucose
                FROM health_data
                WHERE id IN (
                    SELECT MIN(id)
                    FROM health_data
                    WHERE patientId IS NOT NULL
                      AND glucose IS NOT NULL
                    GROUP BY patientId
                )
                ORDER BY id
                """;

            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    selectedValues.add(rs.getInt("glucose"));
                }
            }

            assertEquals(4, selectedValues.size());

            assertEquals(
                    List.of(85, 160, 210, 300),
                    selectedValues
            );

            assertEquals(
                    755,
                    selectedValues.stream()
                            .mapToInt(Integer::intValue)
                            .sum()
            );

            System.out.println(
                    "Teste concluído: um registo por paciente"
            );
            System.out.println(
                    "Valores selecionados: " + selectedValues
            );
        }
    }
}
