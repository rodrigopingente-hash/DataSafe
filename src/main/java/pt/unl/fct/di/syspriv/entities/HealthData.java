package pt.unl.fct.di.syspriv.entities;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "health_data")
public class HealthData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column()
    private Long patientId;

    @Column()
    private int glucose;

    @Column()
    private Date diagnosisDate;

    public HealthData() {}

    public HealthData(Long patientId, int glucose, 
                    Date diagnosisDate) {
        this.patientId = patientId;
        this.glucose = glucose;
        this.diagnosisDate = diagnosisDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getStringId() { return Long.toString(id); }
    public void setId(Long id) { this.id = id; }
    public Long getPatientId() { return this.patientId;}
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public int getGlucose() { return glucose; }
    public void setGlucose(int glucose) { this.glucose = glucose; }
    public Date getDiagnosisDate() { return diagnosisDate; }
    public void setDiagnosisDate(Date diagnosisDate) { this.diagnosisDate = diagnosisDate; }

    // For debugging and logging purposes.
    
    @Override
    public String toString() {
        return "HealthData={" +
                "id=" + getStringId() +
                ", patientId=" + getPatientId() +
                ", glucose=" + getGlucose() +
                ", diagnosisDate=" + getDiagnosisDate() +
                '}';
    }

}