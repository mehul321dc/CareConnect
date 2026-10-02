export interface Patient {
  id: number;
  medicalRecordNumber: string;
  fullName: string;
  dateOfBirth: string;
  sex: string;
  phone: string | null;
  email: string | null;
  allergies: string | null;
  createdAt: string;
}

export interface PatientDraft {
  fullName: string;
  dateOfBirth: string;
  sex: string;
  phone: string;
  email: string;
  allergies: string;
}

export interface Encounter {
  id: number;
  patientId: number;
  patientName: string;
  visitDate: string;
  providerName: string;
  visitType: string;
  chiefComplaint: string;
  diagnosis: string | null;
  clinicalNotes: string;
  status: string;
}

export interface EncounterDraft {
  patientId: number;
  providerName: string;
  visitType: string;
  chiefComplaint: string;
  diagnosis: string;
  clinicalNotes: string;
}

export interface MedicationOrder {
  id: number;
  patientId: number;
  encounterId: number | null;
  patientName: string;
  medicationName: string;
  dose: string;
  route: string;
  frequency: string;
  startDate: string;
  endDate: string | null;
  instructions: string | null;
  status: 'ACTIVE' | 'DISCONTINUED';
  createdAt: string;
}

export interface MedicationDraft {
  patientId: number;
  encounterId: number | null;
  medicationName: string;
  dose: string;
  route: string;
  frequency: string;
  startDate: string;
  endDate: string;
  instructions: string;
}