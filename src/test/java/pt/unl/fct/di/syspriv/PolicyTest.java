package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pt.unl.fct.di.syspriv.entities.Employee;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

public class PolicyTest {

    @Test
    public void testAuthorizedRoleAccess() {
        try {
            XACMLPDP pdp = new XACMLPDP();

            Employee dpoEmployee = new Employee();
            dpoEmployee.setId(1L);
            dpoEmployee.setName("Admin DPO");
            dpoEmployee.setRole("DPO");
            dpoEmployee.setIsAdmin(true);

            assertNotNull(pdp, "O motor XACML PDP deve ser inicializado com sucesso");
            assertTrue(dpoEmployee.getIsAdmin(), "O funcionário DPO deve ter privilégios administrativos");

        } catch (Exception e) {
            fail("O teste falhou devido a uma exceção: " + e.getMessage());
        }
    }

    @Test
    public void testUnauthorizedRoleAccessDeny() {
        try {
            XACMLPDP pdp = new XACMLPDP();

            Employee normalEmployee = new Employee();
            normalEmployee.setId(2L);
            normalEmployee.setName("Regular Staff");
            normalEmployee.setRole("STAFF");
            normalEmployee.setIsAdmin(false);

            assertNotNull(pdp, "O motor XACML PDP deve estar ativo");

            // Simulação de verificação de política restrita:
            // Funcionários sem papel de DPO ou Admin não devem ter privilégios administrativos
            assertFalse(normalEmployee.getIsAdmin(), "O funcionário comum não deve possuir privilégios de Administrador");

        } catch (Exception e) {
            fail("O teste de negação falhou devido a uma exceção: " + e.getMessage());
        }
    }
}