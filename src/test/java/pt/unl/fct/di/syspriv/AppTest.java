package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

/**
 * Unit test for App initialization and core components.
 */
public class AppTest {

    @Test
    public void testApplicationCoreInitialization() {
        try {
            // Validar que o Hibernate inicializa corretamente a ligação à BD de testes
            Hibernate hibernate = Hibernate.getInstance();
            assertNotNull(hibernate, "A instância do Hibernate não deve ser nula");

            // Validar que o motor XACML PDP arranca sem erros de configuração
            XACMLPDP pdp = new XACMLPDP();
            assertNotNull(pdp, "O motor XACML PDP deve ser inicializado com sucesso");

            // Teste de fumaça bem-sucedido
            assertTrue(true, "O núcleo da aplicação inicializou corretamente.");

        } catch (Exception e) {
            fail("A inicialização dos componentes principais da aplicação falhou: " + e.getMessage());
        }
    }
}