package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import pt.unl.fct.di.syspriv.interfaces.Exportable;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

@Entity
@Table(name = "employees")
public class Employee implements Exportable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(nullable = false)
    private Boolean isAdmin;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Appointment> appointments;

    @OneToMany(mappedBy = "primaryDoctor", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Patient> patients;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    @Column(name = "erasure_requested")
    private Boolean erasureRequested = false;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_subject_id")
    private List<ConsentRecord> consentRecords = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_subject_id")
    private List<GDPRRequest> gdprRequests = new ArrayList<>();

    public Employee() {}

    public Employee(String name, String role, String department, Boolean isAdmin) {
        this.name = name;
        this.role = role;
        this.department = department;
        this.isAdmin = isAdmin;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.erasureRequested = false;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getStringId() { return Long.toString(id); }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public boolean getIsAdmin() { return isAdmin; }
    public void setIsAdmin(Boolean isAdmin) { this.isAdmin = isAdmin; }
    public List<Appointment> getAppointments() { return appointments; }
    public void setAppointments(List<Appointment> appointments) { this.appointments = appointments; }
    public List<Patient> getPatients() { return patients; }
    public void setPatients(List<Patient> patients) { this.patients = patients; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getRetentionUntil() {
        return retentionUntil;
    }

    public void setRetentionUntil(LocalDateTime retentionUntil) {
        this.retentionUntil = retentionUntil;
    }

    public Boolean getErasureRequested() {
        return erasureRequested;
    }

    public void setErasureRequested(Boolean erasureRequested) {
        this.erasureRequested = erasureRequested;
    }

    public List<ConsentRecord> getConsentRecords() {
        return consentRecords;
    }

    public void setConsentRecords(List<ConsentRecord> consentRecords) {
        this.consentRecords = consentRecords;
    }

    public List<GDPRRequest> getGdprRequests() {
        return gdprRequests;
    }

    public void setGdprRequests(List<GDPRRequest> gdprRequests) {
        this.gdprRequests = gdprRequests;
    }

    public void addConsentRecord(ConsentRecord consentRecord) {
        this.consentRecords.add(consentRecord);
    }

    public void addGdprRequest(GDPRRequest gdprRequest) {
        this.gdprRequests.add(gdprRequest);
    }

    public boolean isRetentionExpired() {
        return retentionUntil != null &&
                retentionUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public ExportEntity export(XACMLPDP pdp) {
        try {
            String context = "Export";
            String action = "read";
            String lawfulBasis = "legal-obligation";

            boolean allowed = pdp.checkAccess(this, this, context, action, lawfulBasis);

            ExportEntity.Redactable<Employee> employeeRedactable = allowed
                    ? new ExportEntity.Redactable<>(this, null)
                    : new ExportEntity.Redactable<>(null, this.id);

            List<ExportEntity.Redactable<Patient>> patientRedactables = new ArrayList<>();
            if (this.patients != null) {
                for (Patient p : this.patients) {
                    boolean pAllowed = pdp.checkAccess(this, p, context, action, lawfulBasis);
                    if (pAllowed) {
                        patientRedactables.add(new ExportEntity.Redactable<>(p, null));
                    } else {
                        patientRedactables.add(new ExportEntity.Redactable<>(null, p.getId()));
                    }
                }
            }

            List<ExportEntity.Redactable<Appointment>> apptRedactables = new ArrayList<>();
            if (this.appointments != null) {
                for (Appointment appt : this.appointments) {
                    boolean apptAllowed = pdp.checkAccess(this, appt, context, action, lawfulBasis);
                    if (apptAllowed) {
                        apptRedactables.add(new ExportEntity.Redactable<>(appt, null));
                    } else {
                        apptRedactables.add(new ExportEntity.Redactable<>(null, appt.getId()));
                    }
                }
            }

            List<ExportEntity.Redactable<Payment>> paymentRedactables = new ArrayList<>();

            return new ExportEntity(
                    employeeRedactable,
                    null,
                    patientRedactables,
                    null,
                    apptRedactables,
                    paymentRedactables,
                    this.consentRecords,
                    this.gdprRequests
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String toString() {
        return "employee{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", role=" + role +
                ", department='" + department + '\'' +
                ", admin='" + isAdmin + '\'' +
                '}';
    }
}