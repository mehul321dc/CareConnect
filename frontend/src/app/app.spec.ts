import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { App } from './app';
import { EhrService } from './ehr.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [{
        provide: EhrService,
        useValue: {
          getPatients: () => of([]),
          getEncounters: () => of([]),
          getMedications: () => of([]),
        },
      }],
    }).compileComponents();
  });

  it('renders the EHR patient workspace', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent).toContain('Patient records');
  });

  it('starts with no selected patient when the directory is empty', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.componentInstance.selectedPatientId).toBeNull();
    expect(fixture.componentInstance.activeMedicationCount).toBe(0);
  });
});