
package pt.unl.fct.di.syspriv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseCheck {

    public static void main(String[] args) {

        String url = "jdbc:sqlite:file:privsys.db?mode=ro";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            System.out.println("=== TABELAS EXISTENTES ===");

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' ORDER BY name")) {

                while (rs.next()) {
                    System.out.println(rs.getString("name"));
                }
            }

            System.out.println("\n=== REGISTOS EXISTENTES ===");

            String[] tables = {
                    "employees",
                    "patients",
                    "health_data"
            };

            for (String table : tables) {

                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM " + table)) {

                    System.out.println(
                            table + ": " + rs.getInt(1)
                    );

                } catch (Exception e) {
                    System.out.println(
                            table + ": tabela ainda não existe"
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
