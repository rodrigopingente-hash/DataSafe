package pt.unl.fct.di.syspriv.entities;

import pt.unl.fct.di.syspriv.anonymity.Anonymizable;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnonymousHealthData implements Anonymizable {

    private Long id;
    private float glucose;
    private float patientBirthYear;
    private float patientPostalCode;
    private float diagnosisDate;

    // Generalized quasi-identifiers
    private String generalizedBirthYear;
    private String generalizedPostalCode;
    private String generalizedDiagnosisYear;

    public AnonymousHealthData() {}

    public AnonymousHealthData(
            Long id,
            float patientBirthYear,
            float patientPostalCode,
            float glucose,
            Date diagnosisDate) {

        this.id = id;
        this.patientBirthYear = patientBirthYear;
        this.patientPostalCode = patientPostalCode;
        this.glucose = glucose;
        setDiagnosisDate(diagnosisDate);
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public float getGlucose() {
        return glucose;
    }

    public void setGlucose(float glucose) {
        this.glucose = glucose;
    }

    public float getPatientBirthYear() {
        return patientBirthYear;
    }

    public void setPatientBirthYear(float year) {
        this.patientBirthYear = year;
    }

    public float getPatientPostalCode() {
        return patientPostalCode;
    }

    public void setPatientPostalCode(float code) {
        this.patientPostalCode = code;
    }

    public float getDiagnosisDate() {
        return diagnosisDate;
    }

    public void setDiagnosisDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        this.diagnosisDate = cal.get(Calendar.YEAR);
    }

    public void setDiagnosisYear(float year) {
        this.diagnosisDate = year;
    }

    // Generalization methods

    public void setGeneralizedBirthYear(String value) {
        this.generalizedBirthYear = value;
    }

    public void setGeneralizedPostalCode(String value) {
        this.generalizedPostalCode = value;
    }

    public void setGeneralizedDiagnosisYear(String value) {
        this.generalizedDiagnosisYear = value;
    }

    public void clearGeneralization() {
        generalizedBirthYear = null;
        generalizedPostalCode = null;
        generalizedDiagnosisYear = null;
    }

    @Override
    public Map<String, Object> getQuasiIdentifiers() {

        Map<String, Object> qi = new LinkedHashMap<>();

        qi.put("birthYear",
                generalizedBirthYear != null
                        ? generalizedBirthYear
                        : patientBirthYear);

        qi.put("postalCode",
                generalizedPostalCode != null
                        ? generalizedPostalCode
                        : patientPostalCode);

        qi.put("diagnosisYear",
                generalizedDiagnosisYear != null
                        ? generalizedDiagnosisYear
                        : diagnosisDate);

        return qi;
    }

    @Override
    public Object getSensitiveValue() {
        return glucose;
    }

    @Override
    public String toString() {
        return "AnonymousHealthData{" +
                "quasiIdentifiers=" + getQuasiIdentifiers() +
                ", glucose=" + glucose +
                '}';
    }
}


