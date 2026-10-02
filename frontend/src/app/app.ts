import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { Encounter, EncounterDraft, MedicationDraft, MedicationOrder, Patient, PatientDraft } from './ehr.model';
import { EhrService } from './ehr.service';
import { AssistantMessage, PendingAssistantAction } from './assistant.model';
import { AssistantService } from './assistant.service';

type WorkspaceView = 'patients' | 'encounters' | 'medications' | 'portal';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  readonly todayDate = new Date().toISOString().slice(0, 10);
  patients: Patient[] = [];
  encounters: Encounter[] = [];
  medications: MedicationOrder[] = [];
  activeView: WorkspaceView = 'patients';
  selectedPatientId: number | null = null;
  patientSearch = '';
  editingPatientId: number | null = null;
  loading = false;
  saving = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  chatOpen = false;
  chatDraft = '';
  chatBusy = false;
  chatError = '';
  chatMessages: AssistantMessage[] = [{
    role: 'assistant',
    content: 'Hi, I can look up chart details or prepare a record change for your review. I do not provide diagnoses or treatment recommendations.',
  }];
  pendingAssistantAction: PendingAssistantAction | null = null;

  patientForm: PatientDraft = this.emptyPatientForm();
  encounterForm: Omit<EncounterDraft, 'patientId'> = {
    providerName: '',
    visitType: 'Office visit',
    chiefComplaint: '',
    diagnosis: '',
    clinicalNotes: '',
  };
  medicationForm: Omit<MedicationDraft, 'patientId'> = {
    encounterId: null,
    medicationName: '',
    dose: '',
    route: 'Oral',
    frequency: '',
    startDate: new Date().toISOString().slice(0, 10),
    endDate: '',
    instructions: '',
  };

  constructor(
    private ehrService: EhrService,
    private assistantService: AssistantService,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadWorkspace();
  }

  get selectedPatient(): Patient | undefined {
    return this.patients.find((patient) => patient.id === this.selectedPatientId);
  }

  get visiblePatients(): Patient[] {
    const query = this.patientSearch.trim().toLocaleLowerCase();
    return this.patients.filter((patient) =>
      patient.fullName.toLocaleLowerCase().includes(query)
      || patient.medicalRecordNumber.toLocaleLowerCase().includes(query),
    );
  }

  get selectedEncounters(): Encounter[] {
    return this.encounters.filter((encounter) => encounter.patientId === this.selectedPatientId);
  }

  get selectedMedications(): MedicationOrder[] {
    return this.medications.filter((order) => order.patientId === this.selectedPatientId);
  }

  get activeMedicationCount(): number {
    return this.medications.filter((order) => order.status === 'ACTIVE').length;
  }

  loadWorkspace(): void {
    this.loading = true;
    forkJoin({
      patients: this.ehrService.getPatients(),
      encounters: this.ehrService.getEncounters(),
      medications: this.ehrService.getMedications(),
    }).subscribe({
      next: ({ patients, encounters, medications }) => {
        this.patients = patients;
        this.encounters = encounters;
        this.medications = medications;
        if (!this.patients.some((patient) => patient.id === this.selectedPatientId)) {
          this.selectedPatientId = this.patients[0]?.id ?? null;
        }
        this.loading = false;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.setMessage('Unable to load the EHR workspace. Check the API and database connection.', 'error');
        this.changeDetector.markForCheck();
      },
    });
  }

  setView(view: WorkspaceView): void {
    this.activeView = view;
    this.message = '';
  }

  selectPatient(patient: Patient): void {
    this.selectedPatientId = patient.id;
    this.message = '';
  }

  selectPatientId(value: string | number | null): void {
    this.selectedPatientId = value === null || value === '' ? null : Number(value);
  }

  getPatientAge(patient: Patient): number {
    const birthDate = new Date(`${patient.dateOfBirth}T00:00:00`);
    const today = new Date();
    let age = today.getFullYear() - birthDate.getFullYear();
    const beforeBirthday = today.getMonth() < birthDate.getMonth()
      || (today.getMonth() === birthDate.getMonth() && today.getDate() < birthDate.getDate());
    if (beforeBirthday) age -= 1;
    return age;
  }

  savePatient(): void {
    const draft: PatientDraft = {
      ...this.patientForm,
      fullName: this.patientForm.fullName.trim(),
      phone: this.patientForm.phone.trim(),
      email: this.patientForm.email.trim(),
      allergies: this.patientForm.allergies.trim(),
    };

    if (!draft.fullName || !draft.dateOfBirth || !draft.sex) {
      this.setMessage('Name, date of birth, and sex are required.', 'error');
      return;
    }

    this.saving = true;
    const request = this.editingPatientId === null
      ? this.ehrService.createPatient(draft)
      : this.ehrService.updatePatient(this.editingPatientId, draft);

    request.subscribe({
      next: (patient) => {
        if (this.editingPatientId === null) {
          this.patients = [...this.patients, patient].sort((a, b) => a.fullName.localeCompare(b.fullName));
          this.setMessage(`Patient ${patient.fullName} was added.`, 'success');
        } else {
          this.patients = this.patients.map((item) => item.id === patient.id ? patient : item)
            .sort((a, b) => a.fullName.localeCompare(b.fullName));
          this.setMessage(`Patient ${patient.fullName} was updated.`, 'success');
        }
        this.selectedPatientId = patient.id;
        this.resetPatientForm();
        this.saving = false;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.saving = false;
        this.setMessage('Could not save this patient. Review the fields and try again.', 'error');
        this.changeDetector.markForCheck();
      },
    });
  }

  editSelectedPatient(): void {
    const patient = this.selectedPatient;
    if (!patient) return;
    this.editingPatientId = patient.id;
    this.patientForm = {
      fullName: patient.fullName,
      dateOfBirth: patient.dateOfBirth,
      sex: patient.sex,
      phone: patient.phone ?? '',
      email: patient.email ?? '',
      allergies: patient.allergies ?? '',
    };
    this.message = '';
  }

  cancelPatientEdit(): void {
    this.resetPatientForm();
    this.message = '';
  }

  saveEncounter(): void {
    if (this.selectedPatientId === null) return;
    const draft: EncounterDraft = {
      ...this.encounterForm,
      patientId: this.selectedPatientId,
      providerName: this.encounterForm.providerName.trim(),
      chiefComplaint: this.encounterForm.chiefComplaint.trim(),
      diagnosis: this.encounterForm.diagnosis.trim(),
      clinicalNotes: this.encounterForm.clinicalNotes.trim(),
    };

    this.saving = true;
    this.ehrService.createEncounter(draft).subscribe({
      next: (encounter) => {
        this.encounters = [encounter, ...this.encounters];
        this.encounterForm = { providerName: '', visitType: 'Office visit', chiefComplaint: '', diagnosis: '', clinicalNotes: '' };
        this.saving = false;
        this.setMessage('Visit note saved to the patient record.', 'success');
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.saving = false;
        this.setMessage('Could not save the visit note.', 'error');
        this.changeDetector.markForCheck();
      },
    });
  }

  saveMedication(): void {
    if (this.selectedPatientId === null) return;
    const draft: MedicationDraft = { ...this.medicationForm, patientId: this.selectedPatientId };

    this.saving = true;
    this.ehrService.createMedication(draft).subscribe({
      next: (order) => {
        this.medications = [order, ...this.medications];
        this.medicationForm = {
          encounterId: null,
          medicationName: '',
          dose: '',
          route: 'Oral',
          frequency: '',
          startDate: new Date().toISOString().slice(0, 10),
          endDate: '',
          instructions: '',
        };
        this.saving = false;
        this.setMessage('Medication order saved to the patient record.', 'success');
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.saving = false;
        this.setMessage('Could not save the medication order.', 'error');
        this.changeDetector.markForCheck();
      },
    });
  }

  toggleMedicationStatus(order: MedicationOrder): void {
    const status = order.status === 'ACTIVE' ? 'DISCONTINUED' : 'ACTIVE';
    this.ehrService.updateMedicationStatus(order.id, status).subscribe({
      next: (updated) => {
        this.medications = this.medications.map((item) => item.id === updated.id ? updated : item);
        this.setMessage(`${updated.medicationName} marked ${updated.status.toLocaleLowerCase()}.`, 'success');
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.setMessage('Could not update medication status.', 'error');
        this.changeDetector.markForCheck();
      },
    });
  }

  sendAssistantMessage(): void {
    const content = this.chatDraft.trim();
    if (!content || this.chatBusy || this.pendingAssistantAction) return;
    this.chatDraft = '';
    this.chatError = '';
    this.chatMessages = [...this.chatMessages, { role: 'user', content }];
    this.chatBusy = true;
    this.assistantService.chat(this.selectedPatientId, this.chatMessages).pipe(finalize(() => {
      this.chatBusy = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (response) => {
        this.chatMessages = [...this.chatMessages, { role: 'assistant', content: response.reply }];
        this.pendingAssistantAction = response.pendingAction;
        this.changeDetector.markForCheck();
      },
      error: (error: { error?: { detail?: string; message?: string } }) => {
        this.chatError = error.error?.detail ?? error.error?.message ?? 'The assistant could not respond. Check GROQ_API_KEY and try again.';
        this.changeDetector.markForCheck();
      },
    });
  }

  confirmAssistantAction(): void {
    const action = this.pendingAssistantAction;
    if (!action || this.chatBusy) return;
    this.chatBusy = true;
    this.chatError = '';
    this.assistantService.confirm(action).pipe(finalize(() => {
      this.chatBusy = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (response) => {
        this.chatMessages = [...this.chatMessages, { role: 'assistant', content: response.result }];
        this.pendingAssistantAction = null;
        this.loadWorkspace();
        this.changeDetector.markForCheck();
      },
      error: (error: { error?: { detail?: string; message?: string } }) => {
        this.chatError = error.error?.detail ?? error.error?.message ?? 'The proposed change could not be saved.';
        this.changeDetector.markForCheck();
      },
    });
  }

  cancelAssistantAction(): void {
    this.pendingAssistantAction = null;
    this.chatMessages = [...this.chatMessages, { role: 'assistant', content: 'No changes were made.' }];
  }

  exportPatientSummary(): void {
    const patient = this.selectedPatient;
    if (!patient) return;
    const summary = [
      'CareConnect patient summary',
      `Name: ${patient.fullName}`,
      `MRN: ${patient.medicalRecordNumber}`,
      `Date of birth: ${patient.dateOfBirth}`,
      `Allergies: ${patient.allergies || 'Not recorded'}`,
      '',
      'Medication orders:',
      ...this.selectedMedications.map((order) => `${order.medicationName} ${order.dose}, ${order.route}, ${order.frequency} (${order.status.toLowerCase()})`),
      '',
      'Recent visits:',
      ...this.selectedEncounters.map((visit) => `${visit.visitDate}: ${visit.visitType} - ${visit.chiefComplaint}`),
    ].join('\n');
    const url = URL.createObjectURL(new Blob([summary], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `${patient.medicalRecordNumber}-summary.txt`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  private resetPatientForm(): void {
    this.patientForm = this.emptyPatientForm();
    this.editingPatientId = null;
  }

  private emptyPatientForm(): PatientDraft {
    return { fullName: '', dateOfBirth: '', sex: 'Prefer not to say', phone: '', email: '', allergies: '' };
  }

  private setMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
  }
}