package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "consent_records")
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_subject_id")
    private Long employeeSubjectID;

    @Column(name = "patient_subject_id")
    private Long patientSubjectID;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType; // 'EMPLOYEE' ou 'PATIENT'

    @Column(nullable = false, length = 50)
    private String purpose; // 'marketing', 'research', 'sharing'

    @Column(nullable = false)
    private Boolean granted;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "policy_version", length = 20)
    private String policyVersion;

    @Column(length = 20)
    private String source; // 'web', 'paper', 'phone'

    public ConsentRecord() {}

    // Construtor exato chamado no GDPRTestData.java
    public ConsentRecord(Long employeeSubjectID, Long patientSubjectID, String subjectType,
                         String purpose, Boolean granted, LocalDateTime grantedAt,
                         LocalDateTime withdrawnAt, String policyVersion, String source) {
        this.employeeSubjectID = employeeSubjectID;
        this.patientSubjectID = patientSubjectID;
        this.subjectType = subjectType;
        this.purpose = purpose;
        this.granted = granted;
        this.grantedAt = grantedAt;
        this.withdrawnAt = withdrawnAt;
        this.policyVersion = policyVersion;
        this.source = source;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeSubjectID() { return employeeSubjectID; }
    public void setEmployeeSubjectID(Long employeeSubjectID) { this.employeeSubjectID = employeeSubjectID; }

    public Long getPatientSubjectID() { return patientSubjectID; }
    public void setPatientSubjectID(Long patientSubjectID) { this.patientSubjectID = patientSubjectID; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public Boolean getGranted() { return granted; }
    public void setGranted(Boolean granted) { this.granted = granted; }

    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }

    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(LocalDateTime withdrawnAt) { this.withdrawnAt = withdrawnAt; }

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}