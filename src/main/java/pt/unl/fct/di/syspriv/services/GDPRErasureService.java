package pt.unl.fct.di.syspriv.services;

import pt.unl.fct.di.syspriv.entities.GDPRRequest;
import pt.unl.fct.di.syspriv.entities.Patient;
import pt.unl.fct.di.syspriv.entities.Employee;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

import java.time.LocalDateTime;
import java.util.UUID;

public class GDPRErasureService {

    public static boolean processErasureRequest(Object requester, GDPRRequest request, XACMLPDP pdp) {
        try {
            String context = "Erasure";
            String action = "delete";
            String lawfulBasis = "legal-obligation";

            Hibernate hibernate = Hibernate.getInstance();

            // Carregar o recurso alvo (Paciente ou Funcionário) para validação correta no PDP
            Object targetResource = null;
            if ("Patient".equalsIgnoreCase(request.getSubjectType()) || "PATIENT".equalsIgnoreCase(request.getSubjectType())) {
                if (request.getPatientSubjectID() != null) {
                    targetResource = hibernate.get(Patient.class, request.getPatientSubjectID());
                }
            } else if ("Employee".equalsIgnoreCase(request.getSubjectType()) || "EMPLOYEE".equalsIgnoreCase(request.getSubjectType())) {
                if (request.getEmployeeSubjectID() != null) {
                    targetResource = hibernate.get(Employee.class, request.getEmployeeSubjectID());
                }
            }

            if (targetResource == null) {
                System.out.println("-> Erro: Recurso alvo do pedido de erasure não encontrado.");
                return false;
            }

            // 1. Verificar permissão via PDP passando o recurso real (Paciente/Funcionário)
            boolean allowed = pdp.checkAccess(requester, targetResource, context, action, lawfulBasis);
            if (!allowed) {
                System.out.println("Acesso negado pelo PDP para o apagamento do sujeito ID.");
                return false;
            }

            String anonToken = "ANON-" + UUID.randomUUID().toString();

            // 2. Aplicar a anonimização lógica
            if (targetResource instanceof Patient patient) {
                patient.setErasureRequested(true);
                patient.setName(anonToken);
                patient.setAddress(anonToken);
                hibernate.update(patient);

                request.setStatus("COMPLETED");
                request.setCompletedAt(LocalDateTime.now());
                hibernate.update(request);

                System.out.println("-> Paciente anonimizado/apagado logicamente com sucesso (Token: " + anonToken + ").");
                return true;
            } else if (targetResource instanceof Employee employee) {
                employee.setErasureRequested(true);
                employee.setName(anonToken);
                hibernate.update(employee);

                request.setStatus("COMPLETED");
                request.setCompletedAt(LocalDateTime.now());
                hibernate.update(request);

                System.out.println("-> Funcionário anonimizado/apagado logicamente com sucesso (Token: " + anonToken + ").");
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}