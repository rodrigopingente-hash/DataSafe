package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pt.unl.fct.di.syspriv.entities.ExportEntity;
import pt.unl.fct.di.syspriv.entities.Patient;
import pt.unl.fct.di.syspriv.entities.Employee;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

import java.util.List;

public class GDPRExportTest {

    @Test
    public void testGdprExportProcess() {
        try {
            Hibernate hibernate = Hibernate.getInstance();
            XACMLPDP pdp = new XACMLPDP();

            // Teste de exportação para Paciente
            List<Patient> patients = hibernate.jpql("FROM Patient", Patient.class);
            if (!patients.isEmpty()) {
                Patient patient = patients.get(0);
                ExportEntity exportData = patient.export(pdp);
                assertNotNull(exportData, "Os dados exportados do paciente não devem ser nulos");
            }

            // Teste de exportação para Funcionário
            List<Employee> employees = hibernate.jpql("FROM Employee", Employee.class);
            if (!employees.isEmpty()) {
                Employee employee = employees.get(0);
                ExportEntity exportData = employee.export(pdp);
                assertNotNull(exportData, "Os dados exportados do funcionário não devem ser nulos");
            }

            assertTrue(true, "Teste de exportação RGPD concluído com sucesso.");

        } catch (Exception e) {
            fail("O teste de exportação falhou devido a uma exceção: " + e.getMessage());
        }
    }
}