Synthetic Java serialization fixture, no patient data.

Generated from ea68502883a41d44502b043d237c4e4b93376cc1 with Kotlin 2.0.21 and its Compose compiler plugin (same as Android release). Contains an invented hospital, a WBC value of 5.00, an editable DraftRow and embedded LabReport/LabResult. The Compose plugin adds public $stable fields, which affect default Java serialVersionUID; standalone Kotlin compilation does not reproduce those signatures. Production models explicitly preserve the original Android signatures.
