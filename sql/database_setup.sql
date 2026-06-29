


-- =============================================================================
-- GestionPracticas - PostgreSQL 18 Setup Script
-- Database : gestion_practicas
-- Schema   : public (default)
-- Phase    : VI - Arquitectura de Datos Base
-- Backend  : ScalikeJDBC 4.3.0 (SQL-first, no ORM)
-- =============================================================================
-- USAGE: psql -U postgres -f sql/database_setup.sql
-- Must be executed while connected to the 'postgres' superuser database.
-- The script is fully idempotent: drops and recreates the database on each run.
-- =============================================================================


-- =============================================================================
-- SECTION 1 — DATABASE
-- =============================================================================

SELECT pg_terminate_backend(pid)
  FROM pg_stat_activity
 WHERE datname = 'gestion_practicas'
   AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS gestion_practicas;

CREATE DATABASE gestion_practicas
    WITH ENCODING 'UTF8'
    TEMPLATE template0;

\connect gestion_practicas


-- =============================================================================
-- SECTION 2 — ENUM TYPES  (mirror of the 13 Java enums)
-- =============================================================================

CREATE TYPE rol_usuario AS ENUM (
    'ADMIN', 'COORDINADOR', 'EMPRESA', 'ESTUDIANTE',
    'SECRETARIA', 'TUTOR_ACADEMICO', 'TUTOR_EMPRESARIAL'
);

CREATE TYPE estado_cuenta AS ENUM (
    'ACTIVA', 'SUSPENDIDA'
);

CREATE TYPE estado_matricula AS ENUM (
    'REGULAR', 'IRREGULAR'
);

CREATE TYPE estado_estudiante_practica AS ENUM (
    'SIN_PRACTICA', 'CON_PRACTICA_ACTIVA', 'PRACTICA_ACREDITADA'
);

CREATE TYPE estado_oferta AS ENUM (
    'PENDIENTE', 'APROBADA', 'RECHAZADA', 'CERRADA_CUPOS', 'CERRADA_MANUAL'
);

CREATE TYPE estado_postulacion AS ENUM (
    'PENDIENTE', 'VALIDADA_COORDINADOR', 'APROBADA',
	'RECHAZADA', 'CANCELADA_MANUAL', 'CANCELADA_AUTOMATICO'
);

CREATE TYPE estado_convenio AS ENUM (
    'PENDIENTE', 'FORMALIZADO', 'RECHAZADO'
);

CREATE TYPE estado_actividad AS ENUM (
    'PENDIENTE', 'APROBADA', 'RECHAZADA'
);

CREATE TYPE estado_cronograma AS ENUM (
    'TUTOR_ACADEMICO_PENDIENTE', 'F1_PENDIENTE', 'EN_DESARROLLO',
	'F2_F3_PENDIENTE', 'EVALUADA', 'CERRADA_VALIDA'
);

CREATE TYPE estado_formulario2 AS ENUM (
    'PENDIENTE_REVISION', 'CONFORME', 'RECHAZADO'
);

CREATE TYPE origen_rama AS ENUM (
    'BOLSA_EMPLEO', 'EMPRESA_PROPIA'
);

CREATE TYPE origen_creacion_actividad AS ENUM (
    'ESTUDIANTE', 'TUTOR_EMPRESARIAL'
);

CREATE TYPE tipo_archivo_pdf AS ENUM (
    'T1_FORMULARIO_1_PLAN',
    'T2_FORMULARIO_2_RUBRICA',
    'T3_FORMULARIO_3_INFORME',
    'T4_OFICIO_SOLICITUD_PROP_INICIAL',
    'T5_OFICIO_PRESENTACION_PROP_VUELTA',
    'T6_CARTA_COMPROMISO',
    'T7_PLANTILLA_FORMATO_OFERTA',
    'T8_SOLICITUD_CONVENIO_MARCO',
    'T9_MALLA_ACADEMICA',
    'T10_CURRICULUM_VITAE'
);


-- =============================================================================
-- SECTION 3 — TABLES  (creation order respects FK dependencies)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 3.1  archivo_pdf
--      No FK dependencies. All other tables that reference PDFs point here.
-- ---------------------------------------------------------------------------
CREATE TABLE archivo_pdf (
    id_archivo_pdf        SERIAL           PRIMARY KEY,
    tipo_archivo          tipo_archivo_pdf  NOT NULL,
    nombre_original       TEXT             NOT NULL,
    ruta_segura_servidor  TEXT             NOT NULL,
    fecha_carga           TIMESTAMP        NOT NULL DEFAULT NOW()
);


-- ---------------------------------------------------------------------------
-- 3.2  carrera  (catalog — no FK dependencies)
-- ---------------------------------------------------------------------------
CREATE TABLE carrera (
    id_carrera     SERIAL  PRIMARY KEY,
    nombre_carrera TEXT    NOT NULL UNIQUE
);


-- ---------------------------------------------------------------------------
-- 3.3  periodo_academico  (catalog — no FK dependencies)
-- ---------------------------------------------------------------------------
CREATE TABLE periodo_academico (
    id_periodo  SERIAL  PRIMARY KEY,
    descripcion TEXT    NOT NULL UNIQUE
);


-- ---------------------------------------------------------------------------
-- 3.4  usuario  — CTI root table
--      Stores identity data shared by all 7 roles.
--      Coordinador, Tutor Académico, Secretaría and Admin operate on this
--      table alone; the remaining roles extend it via child tables below.
-- ---------------------------------------------------------------------------
CREATE TABLE usuario (
    identificacion      VARCHAR(13)   PRIMARY KEY,
    nombres_completos   TEXT          NOT NULL,
    correo_electronico  TEXT          NOT NULL UNIQUE,
    rol                 rol_usuario   NOT NULL,
    estado_cuenta       estado_cuenta NOT NULL DEFAULT 'ACTIVA'
);


-- ---------------------------------------------------------------------------
-- 3.5  usuario_sistema  — authentication credentials (isolated from profiles)
-- ---------------------------------------------------------------------------
CREATE TABLE usuario_sistema (
    id_usuario_sistema         SERIAL       PRIMARY KEY,
    username                   TEXT         NOT NULL UNIQUE,
    password_hash              TEXT         NOT NULL,
    identificacion_usuario_ref VARCHAR(13)  NOT NULL
        REFERENCES usuario(identificacion) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- 3.6  estudiante_perfil  — CTI child (rol = ESTUDIANTE)
--      Enforces 10-digit CI via CHECK.
-- ---------------------------------------------------------------------------
CREATE TABLE estudiante_perfil (
    identificacion              VARCHAR(10)                NOT NULL PRIMARY KEY
        REFERENCES usuario(identificacion) ON DELETE CASCADE
        CHECK (char_length(identificacion) = 10),
    ciclo_actual                SMALLINT                   NOT NULL
        CHECK (ciclo_actual BETWEEN 1 AND 10),
    id_carrera_ref              INT                        NOT NULL
        REFERENCES carrera(id_carrera),
    estado_matricula            estado_matricula           NOT NULL DEFAULT 'REGULAR',
    estado_estudiante_practica  estado_estudiante_practica NOT NULL DEFAULT 'SIN_PRACTICA',
    malla_academica_pdf         INT                        NOT NULL
        REFERENCES archivo_pdf(id_archivo_pdf),
    curriculum_vitae_pdf        INT
        REFERENCES archivo_pdf(id_archivo_pdf)
);


-- ---------------------------------------------------------------------------
-- 3.7  empresa_perfil  — CTI child (rol = EMPRESA)
--      Enforces 13-digit RUC via CHECK.
-- ---------------------------------------------------------------------------
CREATE TABLE empresa_perfil (
    identificacion   VARCHAR(13)     NOT NULL PRIMARY KEY
        REFERENCES usuario(identificacion) ON DELETE CASCADE
        CHECK (char_length(identificacion) = 13),
    direccion_matriz TEXT            NOT NULL,
    mision           TEXT            NOT NULL,
    vision           TEXT            NOT NULL,
    estado_convenio  estado_convenio NOT NULL DEFAULT 'PENDIENTE'
);


-- ---------------------------------------------------------------------------
-- 3.8  tutor_empresarial_perfil  — CTI child (rol = TUTOR_EMPRESARIAL)
-- ---------------------------------------------------------------------------
CREATE TABLE tutor_empresarial_perfil (
    identificacion    VARCHAR(10)  NOT NULL PRIMARY KEY
        REFERENCES usuario(identificacion) ON DELETE CASCADE
        CHECK (char_length(identificacion) = 10),
    empresa_id_ref    VARCHAR(13)  NOT NULL
        REFERENCES empresa_perfil(identificacion),
    telefono_contacto VARCHAR(10)  NOT NULL
        CHECK (char_length(telefono_contacto) = 10)
);


-- ---------------------------------------------------------------------------
-- 3.9  solicitud_convenio
--      Submitted by a company through TramitarConvenio.fxml.
--      Audited and resolved by Secretaría in SolicitudesConvenio.fxml.
-- ---------------------------------------------------------------------------
CREATE TABLE solicitud_convenio (
    id_solicitud_convenio     SERIAL          PRIMARY KEY,
    ruc_empresa               VARCHAR(13)     NOT NULL UNIQUE
        CHECK (char_length(ruc_empresa) = 13),
    razon_social              TEXT            NOT NULL,
    representante_legal       TEXT            NOT NULL,
    direccion_matriz          TEXT            NOT NULL,
    mision                    TEXT            NOT NULL,
    vision                    TEXT            NOT NULL,
    convenio_documento_pdf    INT             NOT NULL    -- [PDF #8]
        REFERENCES archivo_pdf(id_archivo_pdf),
    estado_convenio           estado_convenio NOT NULL DEFAULT 'PENDIENTE',
    causas_rechazo_secretaria TEXT,
    fecha_presentacion        DATE            NOT NULL DEFAULT CURRENT_DATE
);


-- ---------------------------------------------------------------------------
-- 3.10  validacion_carta_compromiso
--       PK on ci_estudiante: one active validation per student at a time.
--       entregado_tres_copias is constrained to TRUE — a FALSE row is invalid.
-- ---------------------------------------------------------------------------
CREATE TABLE validacion_carta_compromiso (
    ci_estudiante         VARCHAR(10) NOT NULL PRIMARY KEY
        REFERENCES estudiante_perfil(identificacion),
    fecha_validacion      TIMESTAMP   NOT NULL DEFAULT NOW(),
    entregado_tres_copias BOOLEAN     NOT NULL
        CHECK (entregado_tres_copias = TRUE),
    carta_compromiso_pdf  INT         NOT NULL    -- [PDF #6]
        REFERENCES archivo_pdf(id_archivo_pdf)
);


-- ---------------------------------------------------------------------------
-- 3.11  oferta_convocatoria
--       Business rules: 1–10 vacantes, 40–400 horas.
-- ---------------------------------------------------------------------------
CREATE TABLE oferta_convocatoria (
    id_oferta                 SERIAL        PRIMARY KEY,
    ruc_empresa_ref           VARCHAR(13)   NOT NULL
        REFERENCES empresa_perfil(identificacion),
    titulo_oferta             TEXT          NOT NULL,
    vacantes_solicitadas      SMALLINT      NOT NULL
        CHECK (vacantes_solicitadas BETWEEN 1 AND 10),
    duracion_horas            SMALLINT      NOT NULL
        CHECK (duracion_horas BETWEEN 40 AND 400),
    descripcion_general       TEXT          NOT NULL,
    requisitos_obligatorios   TEXT          NOT NULL,
    actividades_especificas   TEXT          NOT NULL,
    plantilla_oferta_pdf      INT           NOT NULL    -- [PDF #7]
        REFERENCES archivo_pdf(id_archivo_pdf),
    estado_oferta             estado_oferta NOT NULL DEFAULT 'PENDIENTE',
    justificacion_coordinador TEXT,
    fecha_publicacion         DATE
);


-- ---------------------------------------------------------------------------
-- 3.12  postulacion_bolsa
--       A student may apply to any open offer but only once per offer (UNIQUE).
--       The cascade-cancellation trigger is installed in Section 5.
-- ---------------------------------------------------------------------------
CREATE TABLE postulacion_bolsa (
    id_postulacion     SERIAL             PRIMARY KEY,
    ci_estudiante_ref  VARCHAR(10)        NOT NULL
        REFERENCES estudiante_perfil(identificacion),
    id_oferta_ref      INT                NOT NULL
        REFERENCES oferta_convocatoria(id_oferta),
    fecha_postulacion  DATE               NOT NULL DEFAULT CURRENT_DATE,
    estado_postulacion estado_postulacion NOT NULL DEFAULT 'PENDIENTE',
    comentario_rechazo TEXT,
    UNIQUE (ci_estudiante_ref, id_oferta_ref)
);


-- ---------------------------------------------------------------------------
-- 3.13  solicitud_empresa_propia
--       Alternative pathway for self-managed internships (outside the bolsa).
--       codigo_oficio_vuelta format: UCUENCA-VINC-YYYY-NNNN (enforced via regex).
-- ---------------------------------------------------------------------------
CREATE TABLE solicitud_empresa_propia (
    id_solicitud_propia              SERIAL          PRIMARY KEY,
    ci_estudiante_ref                VARCHAR(10)     NOT NULL
        REFERENCES estudiante_perfil(identificacion),
    nombre_entidad_externa           TEXT            NOT NULL,
    contacto_empresa_propia          TEXT            NOT NULL,
    horas_empresa_propia             SMALLINT        NOT NULL
        CHECK (horas_empresa_propia BETWEEN 40 AND 400),
    contenido_oficio_transcrito      TEXT            NOT NULL,
    oficio_solicitud_inicial_pdf     INT             NOT NULL    -- [PDF #4]
        REFERENCES archivo_pdf(id_archivo_pdf),
    oficio_presentacion_vuelta_pdf   INT                         -- [PDF #5]
        REFERENCES archivo_pdf(id_archivo_pdf),
    codigo_oficio_vuelta             TEXT            UNIQUE
        CHECK (codigo_oficio_vuelta ~ '^UCUENCA-VINC-\d{4}-\d{4}$'),
    id_tutor_acad_asignado           VARCHAR(10)
        REFERENCES usuario(identificacion),
    estado_tramite                   estado_convenio NOT NULL DEFAULT 'PENDIENTE',
    justificacion_denegacion         TEXT,
    fecha_registro                   DATE            NOT NULL DEFAULT CURRENT_DATE
);


-- ---------------------------------------------------------------------------
-- 3.14  practica_registro  — central contract linking all parties
--       UNIQUE on ci_estudiante_ref: the domain state machine (SIN_PRACTICA →
--       CON_PRACTICA_ACTIVA → PRACTICA_ACREDITADA) is terminal; a student
--       accumulates exactly one practice record over their academic lifecycle.
-- ---------------------------------------------------------------------------
CREATE TABLE practica_registro (
    id_practica                SERIAL            PRIMARY KEY,
    ci_estudiante_ref          VARCHAR(10)       NOT NULL UNIQUE
        REFERENCES estudiante_perfil(identificacion),
    ruc_empresa_ref            VARCHAR(13)       NOT NULL
        REFERENCES empresa_perfil(identificacion),
    id_tutor_academico_ref     VARCHAR(10)
        REFERENCES usuario(identificacion),
    id_tutor_empresarial_ref   VARCHAR(10)       NOT NULL
        REFERENCES tutor_empresarial_perfil(identificacion),
    origen_rama                origen_rama       NOT NULL,
    estado_cronograma          estado_cronograma NOT NULL DEFAULT 'F1_PENDIENTE',
    horas_acumuladas           SMALLINT          NOT NULL DEFAULT 0
        CHECK (horas_acumuladas >= 0),
    horas_totales_requeridas   SMALLINT          NOT NULL
        CHECK (horas_totales_requeridas BETWEEN 40 AND 400),
    nota_final                 NUMERIC(5, 2)
        CHECK (nota_final BETWEEN 0 AND 100)
);


-- ---------------------------------------------------------------------------
-- 3.15  actividad_cronograma
--       (id_practica_ref, numero_secuencial) is UNIQUE for display ordering.
-- ---------------------------------------------------------------------------
CREATE TABLE actividad_cronograma (
    id_actividad           SERIAL                    PRIMARY KEY,
    id_practica_ref        INT                       NOT NULL
        REFERENCES practica_registro(id_practica),
    numero_secuencial      SMALLINT                  NOT NULL,
    descripcion_tarea      TEXT                      NOT NULL,
    origen_creacion        origen_creacion_actividad NOT NULL,
    estado_actividad       estado_actividad          NOT NULL DEFAULT 'PENDIENTE',
    comentario_observacion TEXT,
    fecha_registro         DATE                      NOT NULL DEFAULT CURRENT_DATE,
    UNIQUE (id_practica_ref, numero_secuencial)
);


-- ---------------------------------------------------------------------------
-- 3.16  expediente_formulario1  — one-to-one with practica_registro (UNIQUE FK)
--       Both firma_* flags and estado_de_coordinador default FALSE;
--       they are flipped by individual upload actions in the GUI.
-- ---------------------------------------------------------------------------
CREATE TABLE expediente_formulario1 (
    id_expediente_f1              SERIAL   PRIMARY KEY,
    id_practica_ref               INT      NOT NULL UNIQUE
        REFERENCES practica_registro(id_practica),
    formulario1_pdf               INT                  -- [PDF #1]
        REFERENCES archivo_pdf(id_archivo_pdf),
    firma_empresarial_valida      BOOLEAN  NOT NULL DEFAULT FALSE,
    firma_academica_valida        BOOLEAN  NOT NULL DEFAULT FALSE,
    estado_de_coordinador         BOOLEAN  NOT NULL DEFAULT FALSE,
    justificacion_rechazo_inicio  TEXT,
    fecha_autorizacion            DATE
);


-- ---------------------------------------------------------------------------
-- 3.17  formulario2_evaluacion
--       Multiple rows per practice are allowed (immutability on rejection:
--       a rejected row is frozen; the company submits a new row as a new version).
-- ---------------------------------------------------------------------------
CREATE TABLE formulario2_evaluacion (
    id_f2_evaluacion              SERIAL             PRIMARY KEY,
    id_practica_ref               INT                NOT NULL
        REFERENCES practica_registro(id_practica),
    formulario2_pdf               INT                NOT NULL    -- [PDF #2]
        REFERENCES archivo_pdf(id_archivo_pdf),
    estado_formulario2            estado_formulario2 NOT NULL DEFAULT 'PENDIENTE_REVISION',
    justificacion_rechazo_docente TEXT,
    contenido_rubrica_indexado    TEXT               NOT NULL,
    fecha_registro                TIMESTAMP          NOT NULL DEFAULT NOW()
);


-- ---------------------------------------------------------------------------
-- 3.18  formulario3_informe  — one-to-one with practica_registro (UNIQUE FK)
--       Creation is gated at the controller level: only allowed when the
--       latest formulario2_evaluacion for this practice is CONFORME.
-- ---------------------------------------------------------------------------
CREATE TABLE formulario3_informe (
    id_f3_informe   SERIAL  PRIMARY KEY,
    id_practica_ref INT     NOT NULL UNIQUE
        REFERENCES practica_registro(id_practica),
    formulario3_pdf INT     NOT NULL    -- [PDF #3]
        REFERENCES archivo_pdf(id_archivo_pdf),
    fecha_emision   DATE    NOT NULL DEFAULT CURRENT_DATE
);


-- ---------------------------------------------------------------------------
-- 3.19  auditoria_cierre
--       (id_practica_ref, secuencial_version) is UNIQUE to support immutable
--       re-audits when the coordinator sends the expediente back for correction.
--       estado_auditoria is TEXT (per spec) but constrained via CHECK.
-- ---------------------------------------------------------------------------
CREATE TABLE auditoria_cierre (
    id_auditoria                              SERIAL   PRIMARY KEY,
    id_practica_ref                           INT      NOT NULL
        REFERENCES practica_registro(id_practica),
    secuencial_version                        SMALLINT NOT NULL DEFAULT 1,
    estado_auditoria                          TEXT     NOT NULL DEFAULT 'EN_REVISION'
        CHECK (estado_auditoria IN ('EN_REVISION', 'APROBADO', 'RECHAZADO')),
    observaciones_expediente                  TEXT,
    validacion_fisica_secretaria_sincronizada BOOLEAN  NOT NULL DEFAULT FALSE,
    UNIQUE (id_practica_ref, secuencial_version)
);


-- =============================================================================
-- SECTION 4 — INDEXES
-- =============================================================================

-- Lookup patterns driven by the GUI filter widgets
CREATE INDEX idx_usuario_rol                  ON usuario                (rol);
CREATE INDEX idx_usuario_sistema_id_ref       ON usuario_sistema        (identificacion_usuario_ref);
CREATE INDEX idx_estudiante_estado_practica   ON estudiante_perfil      (estado_estudiante_practica);
CREATE INDEX idx_empresa_estado_convenio      ON empresa_perfil         (estado_convenio);
CREATE INDEX idx_oferta_estado                ON oferta_convocatoria    (estado_oferta);
CREATE INDEX idx_oferta_empresa               ON oferta_convocatoria    (ruc_empresa_ref);
CREATE INDEX idx_postulacion_estudiante       ON postulacion_bolsa      (ci_estudiante_ref);
CREATE INDEX idx_postulacion_oferta           ON postulacion_bolsa      (id_oferta_ref);
CREATE INDEX idx_postulacion_estado           ON postulacion_bolsa      (estado_postulacion);
CREATE INDEX idx_practica_cronograma          ON practica_registro      (estado_cronograma);
CREATE INDEX idx_actividad_practica           ON actividad_cronograma   (id_practica_ref);
CREATE INDEX idx_actividad_estado             ON actividad_cronograma   (estado_actividad);
CREATE INDEX idx_f2_practica                  ON formulario2_evaluacion (id_practica_ref);
CREATE INDEX idx_f2_estado                    ON formulario2_evaluacion (estado_formulario2);
CREATE INDEX idx_auditoria_practica           ON auditoria_cierre       (id_practica_ref);
CREATE INDEX idx_auditoria_estado             ON auditoria_cierre       (estado_auditoria);
CREATE INDEX idx_solicitud_convenio_estado    ON solicitud_convenio     (estado_convenio);


-- =============================================================================
-- SECTION 5 — TRIGGERS
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Cascade-cancellation rule (PostulacionBolsa domain spec §3.3.6):
-- When any postulacion transitions to APROBADA, all other PENDIENTE
-- postulaciones of that same student are automatically set to CANCELADA_AUTOMATICO.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_cancelar_otras_postulaciones()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.estado_postulacion = 'APROBADA'
       AND OLD.estado_postulacion <> 'APROBADA'
    THEN
        UPDATE postulacion_bolsa
           SET estado_postulacion = 'CANCELADA_AUTOMATICO'
         WHERE ci_estudiante_ref  = NEW.ci_estudiante_ref
           AND id_postulacion    <> NEW.id_postulacion
           AND estado_postulacion = 'PENDIENTE';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cancelar_otras_postulaciones
    AFTER UPDATE OF estado_postulacion
    ON postulacion_bolsa
    FOR EACH ROW
    EXECUTE FUNCTION fn_cancelar_otras_postulaciones();


-- =============================================================================
-- END OF SCRIPT
-- =============================================================================
