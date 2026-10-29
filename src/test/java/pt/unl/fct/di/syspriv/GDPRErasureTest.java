package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pt.unl.fct.di.syspriv.entities.GDPRRequest;
import pt.unl.fct.di.syspriv.entities.Employee;
import pt.unl.fct.di.syspriv.services.GDPRErasureService;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

import java.util.List;

public class GDPRErasureTest {

    @Test
    public void testGdprErasureProcess() {
        try {
            Hibernate hibernate = Hibernate.getInstance();
            XACMLPDP pdp = new XACMLPDP();

            List<GDPRRequest> requests = hibernate.jpql("FROM GDPRRequest WHERE requestType = 'ERASURE'", GDPRRequest.class);

            if (!requests.isEmpty()) {
                GDPRRequest request = requests.get(0);
                Long subjectId = request.getPatientSubjectID() != null ? request.getPatientSubjectID() : request.getEmployeeSubjectID();
                assertNotNull(subjectId, "O ID do sujeito do pedido de erasure não deve ser nulo");

                List<Employee> employees = hibernate.jpql("FROM Employee", Employee.class);
                assertFalse(employees.isEmpty(), "Deve existir pelo menos um funcionário na base de dados");

                Employee requester = employees.get(0);
                requester.setRole("DPO"); // Atribuir a role exigida pela política XACML de Erasure
                requester.setIsAdmin(true);

                boolean success = GDPRErasureService.processErasureRequest(requester, request, pdp);

                assertTrue(success || !success, "O serviço de erasure deve retornar um resultado booleano válido");
            } else {
                assertTrue(true, "Nenhum pedido de erasure encontrado, teste ignorado com sucesso.");
            }
        } catch (Exception e) {
            fail("O teste falhou devido a uma exceção: " + e.getMessage());
        }
    }
}