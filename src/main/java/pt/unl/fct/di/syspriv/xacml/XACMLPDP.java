package pt.unl.fct.di.syspriv.xacml;

import pt.unl.fct.di.syspriv.util.ConfigLoader;
import pt.unl.fct.di.syspriv.entities.*;

import org.ow2.authzforce.core.pdp.api.*;
import org.ow2.authzforce.core.pdp.api.value.*;
import org.ow2.authzforce.core.pdp.impl.BasePdpEngine;
import org.ow2.authzforce.core.pdp.impl.PdpEngineConfiguration;
import org.ow2.authzforce.xacml.identifiers.XacmlStatusCode;
import org.ow2.authzforce.core.pdp.api.value.Bags;
import org.ow2.authzforce.core.pdp.api.value.Datatype;
import org.ow2.authzforce.core.pdp.api.value.StandardDatatypes;
import org.ow2.authzforce.core.pdp.api.value.StringValue;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.DecisionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.logging.log4j.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.apache.commons.io.IOUtils;

public class XACMLPDP {

    // Standard XACML category URNs
    private static final String SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject";
    private static final String RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource";
    private static final String ACTION_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action";
    private static final String ENV_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:environment";

    private final BasePdpEngine pdp;
    private Logger logger;

    public XACMLPDP() throws Exception {
        java.net.URL resourceUrl = XACMLPDP.class.getClassLoader().getResource("pdp.xml");
        String configPath;
        if (resourceUrl != null) {
            configPath = java.net.URLDecoder.decode(resourceUrl.getPath(), StandardCharsets.UTF_8);
            if (configPath.startsWith("/") && configPath.contains(":")) {
                configPath = configPath.substring(1);
            }
        } else {
            configPath = "src/main/resources/pdp.xml";
        }

        PdpEngineConfiguration config = PdpEngineConfiguration.getInstance(configPath);
        this.pdp = new BasePdpEngine(config);
        this.logger = LoggerFactory.getLogger(BasePdpEngine.class);
    }

    private String loadPolicyXml(String policyFile) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(policyFile);
        if (is != null) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (Files.exists(Paths.get(policyFile))) {
            return new String(Files.readAllBytes(Paths.get(policyFile)), StandardCharsets.UTF_8);
        }

        throw new IllegalStateException("Policy file not found: " + policyFile);
    }

    public boolean checkAccess(Object requester, Object resource, String context, String action, String lawfulBasis) throws Exception {
        Map<String, String> subjectAttrs = new HashMap<>();
        Map<String, String> resourceAttrs = new HashMap<>();
        Map<String, String> actionAttrs = new HashMap<>();
        Map<String, String> envAttrs = new HashMap<>();

        // 1. Extrair os atributos do Sujeito (Requester)
        if (requester instanceof Employee emp) {
            subjectAttrs.put("id", String.valueOf(emp.getId()));
            subjectAttrs.put("role", emp.getRole());
        } else if (requester instanceof Patient pat) {
            subjectAttrs.put("id", String.valueOf(pat.getId()));
            subjectAttrs.put("role", "Patient");
        }

        // 2. Extrair os atributos do Recurso (Resource)
        if (resource instanceof Patient pat) {
            resourceAttrs.put("id", String.valueOf(pat.getId()));
            resourceAttrs.put("resource-type", "PatientData");

            if (pat.getPrimaryDoctor() != null) {
                Object doc = pat.getPrimaryDoctor();
                if (doc instanceof Employee empDoc) {
                    resourceAttrs.put("doctor-id", String.valueOf(empDoc.getId()));
                } else {
                    resourceAttrs.put("doctor-id", String.valueOf(doc));
                }
            }
        } else if (resource instanceof Employee emp) {
            resourceAttrs.put("id", String.valueOf(emp.getId()));
            resourceAttrs.put("resource-type", "EmployeeData");
        } else if (resource instanceof Appointment app) {
            resourceAttrs.put("id", String.valueOf(app.getId()));
            resourceAttrs.put("resource-type", "AppointmentData");
        } else if (resource instanceof Payment pay) {
            resourceAttrs.put("id", String.valueOf(pay.getId()));
            resourceAttrs.put("resource-type", "PaymentData");
        } else if (resource instanceof HealthData health) {
            resourceAttrs.put("id", String.valueOf(health.getId()));
            resourceAttrs.put("resource-type", "HealthData");
        } else if (resource instanceof GDPRRequest req) {
            resourceAttrs.put("id", String.valueOf(req.getId()));
            resourceAttrs.put("resource-type", "GDPRRequestData");
            if ("ERASURE".equalsIgnoreCase(req.getRequestType())) {
                resourceAttrs.put("erasure-requested", "true");
            }
        }

        // 3. Atributos de Ambiente (Contexto e Base Legal)
        if (context != null && !context.trim().isEmpty()) {
            envAttrs.put("context", context);
        }

        if (lawfulBasis != null && !lawfulBasis.trim().isEmpty()) {
            envAttrs.put("lawful-basis", lawfulBasis);
        }

        // 4. Ação (ex: "read", "marketing", etc.)
        actionAttrs.put("action-id", (action != null && !action.isEmpty()) ? action : "read");

        DecisionRequestBuilder<?> requestBuilder = pdp.newRequestBuilder(-1, -1);

        addAttributes(requestBuilder, SUBJECT_CATEGORY, subjectAttrs);
        addAttributes(requestBuilder, RESOURCE_CATEGORY, resourceAttrs);
        addAttributes(requestBuilder, ACTION_CATEGORY, actionAttrs);
        addAttributes(requestBuilder, ENV_CATEGORY, envAttrs);

        var request = requestBuilder.build(true);
        logger.debug(request.toString());
        DecisionResult result = pdp.evaluate(request);

        logger.debug(result.toString());
        return result.getDecision() == DecisionType.PERMIT;
    }

    private void addAttributes(DecisionRequestBuilder<?> requestBuilder, String category, Map<String, String> attrs) {
        for (var entry : attrs.entrySet()) {
            AttributeFqn fqn = AttributeFqns.newInstance(category, Optional.empty(), entry.getKey());
            AttributeBag<?> bag = Bags.singletonAttributeBag(
                    StandardDatatypes.STRING,
                    new StringValue(entry.getValue())
            );
            requestBuilder.putNamedAttributeIfAbsent(fqn, bag);
        }
    }
}