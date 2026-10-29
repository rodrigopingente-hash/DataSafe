
package pt.unl.fct.di.syspriv;

import java.time.LocalDateTime;

import pt.unl.fct.di.syspriv.entities.ConsentRecord;
import pt.unl.fct.di.syspriv.entities.GDPRRequest;
import pt.unl.fct.di.syspriv.entities.Patient;
import pt.unl.fct.di.syspriv.storage.Hibernate;

public class GDPRTestData {

    public static void main(String[] args) {
        GDPRTestData data = new GDPRTestData();
        data.createGdprTestData();
    }

    public void createGdprTestData() {
        try {
            Hibernate db = Hibernate.getInstance();

            System.out.println("=== Preparação dos dados GDPR ===");

            // ================================================
            // CENÁRIO 1 - Consentimento de marketing
            // ================================================
            Patient patient1 = db.get(Patient.class, 1001L);

            if (patient1 != null) {
                ConsentRecord marketingConsent = new ConsentRecord(
                        null,
                        patient1.getId(),
                        "PATIENT",
                        "marketing",
                        true,
                        LocalDateTime.now(),
                        null,
                        "1.0",
                        "web"
                );

                db.persist(marketingConsent);

                System.out.println(
                        "Consentimento de marketing criado para o paciente 1001."
                );
            } else {
                System.out.println(
                        "Paciente 1001 não encontrado. Cenário ignorado."
                );
            }

            // ================================================
            // CENÁRIO 2 - Pedido de apagamento (ERASURE)
            // ================================================
            Patient patient2 = db.get(Patient.class, 1002L);

            if (patient2 != null) {
                patient2.setErasureRequested(true);
                db.update(patient2);

                GDPRRequest erasureRequest = new GDPRRequest();

                erasureRequest.setEmployeeSubjectId(null);
                erasureRequest.setPatientSubjectId(patient2.getId());
                erasureRequest.setSubjectType("PATIENT");
                erasureRequest.setRequestType("ERASURE");
                erasureRequest.setRequestedAt(LocalDateTime.now());
                erasureRequest.setStatus("PENDING");

                db.persist(erasureRequest);

                System.out.println(
                        "Pedido de apagamento criado para o paciente 1002."
                );
            } else {
                System.out.println(
                        "Paciente 1002 não encontrado. Cenário ignorado."
                );
            }

            // ================================================
            // CENÁRIO 3 - Retenção de dados expirada
            // ================================================
            Patient patient3 = db.get(Patient.class, 1003L);

            if (patient3 != null) {
                patient3.setRetentionUntil(
                        LocalDateTime.now().minusDays(1)
                );

                db.update(patient3);

                System.out.println(
                        "Prazo de retenção expirado definido para o paciente 1003."
                );
            } else {
                System.out.println(
                        "Paciente 1003 não encontrado. Cenário ignorado."
                );
            }

            System.out.println("=== Preparação GDPR terminada ===");

        } catch (Exception e) {
            throw new RuntimeException(
                    "Erro ao preparar os dados de teste GDPR", e
            );
        }
    }
}
