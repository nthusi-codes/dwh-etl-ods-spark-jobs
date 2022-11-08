
SELECT
    PatientID,
    PatientPK,
    FacilityName,
    SiteCode,
    VisitID,
    Drug,
    DispenseDate,
    Duration,
    ExpectedReturn,
    TreatmentType,
    PeriodTaken,
    ProphylaxisType,
    Emr,
    Project,
    DateImported,
    CKV,
    RegimenLine,
    RegimenChangedSwitched,
    RegimenChangeSwitchReason,
    StopRegimenReason,
    StopRegimenDate,
    PatientUID,
    DeletedFlag
FROM
    [ODS].[dbo].CT_PatientPharmacy
