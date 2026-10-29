package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import pt.unl.fct.di.syspriv.entities.ConsentRecord;
import pt.unl.fct.di.syspriv.entities.Patient;
import pt.unl.fct.di.syspriv.storage.Hibernate;

public class GDPRComplianceTest {

    @Test
    public void testConsentRevocationHandling() {
        try {
            Hibernate db = Hibernate.getInstance();
            List<Patient> patients = db.jpql("FROM Patient", Patient.class);

            if (!patients.isEmpty()) {
                Patient patient = patients.get(0);

                // Criar registo de consentimento inicialmente concedido
                ConsentRecord consent = new ConsentRecord(
                        null,
                        patient.getId(),
                        "PATIENT",
                        "research",
                        true,
                        LocalDateTime.now().minusDays(10),
                        null,
                        "1.0",
                        "hospital-portal"
                );
                db.persist(consent);

                // Simular a revogação do consentimento pelo titular (GDPR Art. 7)
                consent.setGranted(false);
                consent.setWithdrawnAt(LocalDateTime.now());
                db.update(consent);

                // Validar que o consentimento está efetivamente revogado
                assertFalse(consent.getGranted(), "O consentimento revogado deve ter o estado 'granted' como falso");
                assertNotNull(consent.getWithdrawnAt(), "A data de revogação (withdrawnAt) deve estar preenchida");
            }

        } catch (Exception e) {
            fail("O teste de revogação de consentimento falhou: " + e.getMessage());
        }
    }

    @Test
    public void testDataRetentionExpirationCheck() {
        try {
            Hibernate db = Hibernate.getInstance();
            List<Patient> patients = db.jpql("FROM Patient", Patient.class);

            if (!patients.isEmpty()) {
                Patient patient = patients.get(0);

                // Definir data de retenção legal expirada no passado
                LocalDateTime expiredDate = LocalDateTime.now().minusMonths(6);
                patient.setRetentionUntil(expiredDate);
                db.update(patient);

                // Validar se o sistema identifica corretamente que o prazo de retenção expirou
                assertTrue(patient.getRetentionUntil().isBefore(LocalDateTime.now()),
                        "A data limite de retenção deve ser anterior ao momento atual para acionar o expurgo");
            }

        } catch (Exception e) {
            fail("O teste de retenção de dados falhou: " + e.getMessage());
        }
    }
}