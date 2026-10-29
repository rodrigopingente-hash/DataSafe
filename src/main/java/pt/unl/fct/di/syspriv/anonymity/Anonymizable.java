package pt.unl.fct.di.syspriv.anonymity;

import java.util.Map;

public interface Anonymizable {
    /** Returns the QI values as a map (name → value). */
    Map<String, Object> getQuasiIdentifiers();

    /** Returns the sensitive attribute value. */
    Object getSensitiveValue();
}
