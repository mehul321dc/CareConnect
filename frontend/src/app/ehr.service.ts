import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Encounter, EncounterDraft, MedicationDraft, MedicationOrder, Patient, PatientDraft } from './ehr.model';

@Injectable({ providedIn: 'root' })
export class EhrService {
  private readonly apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  getPatients(): Observable<Patient[]> {
    return this.http.get<Patient[]>(`${this.apiUrl}/patients`);
  }

  createPatient(patient: PatientDraft): Observable<Patient> {
    return this.http.post<Patient>(`${this.apiUrl}/patients`, patient);
  }

  updatePatient(id: number, patient: PatientDraft): Observable<Patient> {
    return this.http.put<Patient>(`${this.apiUrl}/patients/${id}`, patient);
  }

  getEncounters(): Observable<Encounter[]> {
    return this.http.get<Encounter[]>(`${this.apiUrl}/encounters`);
  }

  createEncounter(encounter: EncounterDraft): Observable<Encounter> {
    return this.http.post<Encounter>(`${this.apiUrl}/encounters`, encounter);
  }

  getMedications(): Observable<MedicationOrder[]> {
    return this.http.get<MedicationOrder[]>(`${this.apiUrl}/medications`);
  }

  createMedication(order: MedicationDraft): Observable<MedicationOrder> {
    return this.http.post<MedicationOrder>(`${this.apiUrl}/medications`, {
      ...order,
      endDate: order.endDate || null,
      instructions: order.instructions.trim() || null,
    });
  }

  updateMedicationStatus(id: number, status: MedicationOrder['status']): Observable<MedicationOrder> {
    return this.http.patch<MedicationOrder>(`${this.apiUrl}/medications/${id}/status`, { status });
  }
}