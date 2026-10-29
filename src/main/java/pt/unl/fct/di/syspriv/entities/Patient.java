package pt.unl.fct.di.syspriv.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.unl.fct.di.syspriv.interfaces.Exportable;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

@Entity
@Table(name = "patients")
public class Patient implements Exportable {

    private static final Logger logger = LoggerFactory.getLogger(Patient.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "birthdate", nullable = false)
    private Date birthdate;

    @Column(length = 50)
    private String insurance;

    @Column(length = 200)
    private String address;

    @ManyToOne
    @JoinColumn(name = "primaryDoctor_id")
    private Employee primaryDoctor;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Appointment> appointments = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    @Column(name = "erasure_requested")
    private Boolean erasureRequested = false;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_subject_id")
    private List<ConsentRecord> consentRecords = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_subject_id")
    private List<GDPRRequest> gdprRequests = new ArrayList<>();

    public Patient() {}

    public Patient(String name, Date birthdate, String insurance, String address, Employee primaryDoctor) {
        this.name = name;
        this.birthdate = birthdate;
        this.insurance = insurance;
        this.address = address;
        this.primaryDoctor = primaryDoctor;
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
    public Date getBirthdate() { return birthdate; }
    public void setBirthdate(Date birthdate) { this.birthdate = birthdate; }
    public String getInsurance() { return insurance; }
    public void setInsurance(String insurance) { this.insurance = insurance; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Employee getPrimaryDoctor() { return primaryDoctor; }
    public void setPrimaryDoctor(Employee primaryDoctor) { this.primaryDoctor = primaryDoctor; }
    public List<Appointment> getAppointments() { return appointments; }
    public void setAppointments(List<Appointment> appointments) { this.appointments = appointments; }

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

            ExportEntity.Redactable<Patient> patientRedactable = allowed
                    ? new ExportEntity.Redactable<>(this, null)
                    : new ExportEntity.Redactable<>(null, this.id);

            List<ExportEntity.Redactable<Employee>> clinicians = new ArrayList<>();
            if (this.primaryDoctor != null) {
                boolean docAllowed = pdp.checkAccess(this, this.primaryDoctor, context, action, lawfulBasis);
                if (docAllowed) {
                    clinicians.add(new ExportEntity.Redactable<>(this.primaryDoctor, null));
                } else {
                    clinicians.add(new ExportEntity.Redactable<>(null, this.primaryDoctor.getId()));
                }
            }

            List<ExportEntity.Redactable<Appointment>> apptRedactables = new ArrayList<>();
            for (Appointment appt : this.appointments) {
                boolean apptAllowed = pdp.checkAccess(this, appt, context, action, lawfulBasis);
                if (apptAllowed) {
                    apptRedactables.add(new ExportEntity.Redactable<>(appt, null));
                } else {
                    apptRedactables.add(new ExportEntity.Redactable<>(null, appt.getId()));
                }
            }

            List<ExportEntity.Redactable<Payment>> paymentRedactables = new ArrayList<>();

            return new ExportEntity(
                    null,
                    patientRedactable,
                    null,
                    clinicians,
                    apptRedactables,
                    paymentRedactables,
                    this.consentRecords,
                    this.gdprRequests
            );
        } catch (Exception e) {
            logger.error("Error exporting patient data: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() {
        return "Patient{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", birthdate=" + birthdate +
                ", insurance='" + insurance + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}