# GestionPracticas

Aplicación de escritorio (JavaFX + Scala) para gestionar el ciclo de vida completo de las **prácticas pre-profesionales** de la Facultad de Ingeniería de la Universidad de Cuenca: desde la publicación de una vacante o el registro de un oficio de empresa propia, hasta la evaluación final y el cierre del expediente del estudiante.

El sistema reemplaza un proceso hoy disperso entre oficios físicos, hojas de cálculo y correos, centralizando en una sola herramienta a los 7 roles que intervienen en el proceso.

## Tabla de Contenidos

- [Roles del Sistema](#roles-del-sistema)
- [Flujo de Negocio](#flujo-de-negocio)
- [Documentos PDF Gestionados](#documentos-pdf-gestionados)
- [Modelo de Dominio](#modelo-de-dominio)
- [Stack Tecnológico](#stack-tecnológico)
- [Arquitectura](#arquitectura)
- [Puesta en Marcha](#puesta-en-marcha)
- [Comandos](#comandos)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Licencia](#licencia)

## Roles del Sistema

| Rol | Responsabilidad principal |
|---|---|
| **Administrador** | Gestiona cuentas de usuario, monitorea el estado del sistema, revisa trazabilidad y genera estadísticas globales. |
| **Coordinador de Vinculación** | Aprueba/rechaza ofertas y postulaciones, asigna tutores académicos, autoriza el inicio de prácticas, audita el cierre de expedientes y asienta la nota final. |
| **Empresa** | Publica ofertas para la bolsa de empleo, gestiona candidatos postulados, acepta estudiantes asignando un tutor empresarial, y tramita su convenio institucional. |
| **Estudiante** | Busca y postula a vacantes (o registra una solicitud de empresa propia), propone actividades, sube documentos (malla, CV) y firma los formularios de su práctica. |
| **Secretaría** | Valida la entrega física de cartas compromiso (para empresas sin convenio) y audita solicitudes de convenio marco. |
| **Tutor Académico** | Docente de la carrera; valida actividades del cronograma, firma el Formulario 1, revisa el Formulario 2 y emite el Formulario 3 (informe de cierre). |
| **Tutor Empresarial** | Supervisor técnico externo; propone actividades y evalúa el desempeño del estudiante en el Formulario 2. |

## Flujo de Negocio

Toda práctica nace por una de dos rutas y converge en un único flujo de ejecución y cierre.

### Ruta 1 — Bolsa de Empleo

1. Una **empresa** publica una oferta (1–10 cupos, 40–400 horas). Queda oculta hasta ser aprobada.
2. El **coordinador** aprueba (se hace visible a estudiantes desde 6º ciclo) o rechaza (con comentario obligatorio, la empresa debe crear una oferta nueva).
3. El **estudiante** (sin práctica activa, desde 6º ciclo) postula a una o varias ofertas.
4. El **coordinador** valida la postulación revisando CV y malla académica; si aprueba, pasa al panel de la empresa; si rechaza, el estudiante queda libre para postular de nuevo.
5. La **empresa** acepta a un candidato asignando en el acto un tutor empresarial.
   - La oferta se cierra automáticamente al llenar cupos o al cerrarla manualmente.
   - Al aceptar a un estudiante, **todas sus demás postulaciones activas se cancelan automáticamente**.
   - Al cerrarse una oferta, las postulaciones pendientes que quedaban se rechazan automáticamente por finalización de convocatoria.
6. El **coordinador** asigna el tutor académico — en este instante se crea formalmente el registro de `PracticaRegistro`.

### Ruta 2 — Empresa Propia

1. El **estudiante** sube un oficio digital con los datos de la empresa externa que consiguió por su cuenta.
2. El **coordinador** aprueba (genera el oficio de vuelta institucional con formato `UCUENCA-VINC-YYYY-NNNN`, asigna tutor académico y crea la práctica) o rechaza (con comentario obligatorio; el estudiante puede volver a intentarlo).
3. Los datos de la empresa y del supervisor externo se capturan como texto plano dentro de la solicitud (patrón *Just-In-Time Profile Creation*) y solo se materializan como perfiles reales (`empresa_perfil`, `tutor_empresarial_perfil`) en el momento de la aprobación — si se rechaza, no quedan registros huérfanos.

### Convergencia — Formulario 1 (arranque formal)

- Estudiante y tutor empresarial proponen actividades; cada una la valida o rechaza el tutor académico (con comentario obligatorio en caso de rechazo).
- Se requieren **entre 3 y 6 actividades aprobadas** para habilitar el Formulario 1 (el formato físico tiene 6 casillas).
- Si la empresa no tiene convenio vigente, el flujo se congela hasta que **Secretaría** valide la entrega física de 3 copias firmadas de la Carta Compromiso.
- El estudiante presenta el Formulario 1; tutor empresarial y tutor académico lo firman digitalmente; el coordinador lo aprueba, arrancando oficialmente el conteo de horas.

### Desarrollo y Cierre — Formularios 2 y 3

1. Al completar las horas requeridas, el estudiante solicita la evaluación final.
2. El **tutor empresarial** llena el Formulario 2 (rúbrica sobre 100 puntos).
3. El **tutor académico** solo puede acceder al Formulario 3 (informe de cierre) **una vez que el Formulario 2 esté enviado**; si lo rechaza, se genera una nueva versión del Formulario 2 y el Formulario 3 permanece bloqueado.
4. Con ambos formularios completos, el expediente pasa al **coordinador**, quien audita los tres formularios y la validación física de Secretaría:
   - Si aprueba el cierre, asienta la **nota final** y la práctica queda `CERRADA_VALIDA`.
   - Si rechaza, el expediente vuelve con observaciones y se reinicia el ciclo de auditoría con una nueva versión secuencial.

## Documentos PDF Gestionados

El sistema centraliza 10 tipos de documentos bajo la entidad type-safe `ArchivoPDF` / enum `TipoArchivoPDF`:

| # | Documento | Quién lo sube | Propósito |
|---|---|---|---|
| 1 | Formulario 1 — Plan de Aprendizaje | Estudiante / tutores | Cronograma inicial firmado por las 3 partes |
| 2 | Formulario 2 — Rúbrica de Desempeño | Tutor Empresarial | Evaluación cuantitativa (100 pts) |
| 3 | Formulario 3 — Informe Académico | Tutor Académico | Cierre e informe final |
| 4 | Oficio de Solicitud (Empresa Propia, inicial) | Estudiante | Petición de la empresa externa |
| 5 | Oficio de Presentación (vuelta) | Coordinador | Respuesta institucional oficial |
| 6 | Carta Compromiso | Secretaría (digitalizado) | Bypass legal sin convenio marco |
| 7 | Plantilla de Oferta | Empresa | Formato oficial de solicitud de personal |
| 8 | Solicitud de Convenio Marco | Empresa | Expediente legal para alianza permanente |
| 9 | Malla Académica | Estudiante | Certifica prerrequisitos de ciclo |
| 10 | Curriculum Vitae | Estudiante | Presentación ante empresas |

## Modelo de Dominio

El dominio está normalizado en **3FN** y se apoya en dos tipos de objetos:

- **13 Enums de Java** — máquinas de estado y catálogos fijos (`RolUsuario`, `EstadoOferta`, `EstadoPostulacion`, `EstadoCronograma`, `TipoArchivoPDF`, etc.).
- **19 Case classes de Scala** — entidades inmutables persistidas vía ScalikeJDBC, entre ellas:
  - `Usuario` (raíz) → `EstudiantePerfil`, `EmpresaPerfil`, `TutorEmpresarialPerfil` (herencia por tabla de clases)
  - `OfertaConvocatoria` → `PostulacionBolsa` (pipeline de bolsa de empleo)
  - `SolicitudEmpresaPropia` (pipeline de empresa propia, con creación JIT de perfiles)
  - `PracticaRegistro` — contrato central que une estudiante, empresa y ambos tutores
  - `ActividadCronograma`, `ExpedienteFormulario1`, `Formulario2Evaluacion`, `Formulario3Informe`, `AuditoriaCierre`
  - `ArchivoPDF` — contenedor seguro para los 10 documentos del sistema

## Stack Tecnológico

- **Scala 2.13.18** con **JavaFX 21** (interfaz basada en FXML, diseñada con Gluon Scene Builder)
- **ScalikeJDBC 4.3.0** — wrapper JDBC SQL-first (sin ORM, SQL crudo en la capa `db/`)
- **PostgreSQL 18** como motor de base de datos
- **ScalaTest 3.2.18** para pruebas unitarias (estilo `AnyFlatSpec`)
- **jBCrypt** para el hash de contraseñas

## Arquitectura

Patrón **MVC estricto** con navegación de ventana única (*Single Window Navigation*): un `StackPane` raíz reemplaza dinámicamente su contenido al navegar, sin abrir ventanas nuevas. Las vistas FXML no contienen lógica de negocio; esa responsabilidad vive exclusivamente en `logic/`.

Paquetes bajo `com.ucuenca.gestion`:

| Paquete | Propósito |
|---|---|
| `Main.scala` | Punto de entrada; inicia el stage de JavaFX |
| `views/` | Mapea las rutas FXML de cada rol a objetos de carga (`DynamicViewTemplate`) |
| `controllers/` | Manejadores de eventos JavaFX; enlazan las vistas FXML con la capa de modelo |
| `models/entities/` | Case classes inmutables para los objetos de dominio |
| `models/logic/` | Reglas de negocio, validaciones y máquinas de estado |
| `models/db/` | Repositorios ScalikeJDBC; SQL crudo, sin ORM |
| `models/dto/` | Objetos de transferencia entre controladores y lógica |
| `utils/` | Utilidades compartidas (sesión, navegación, ayuda) |

Las vistas FXML están en `src/main/resources/fxml/`, organizadas por rol: `global/`, `admin/`, `coordinador/`, `empresa/`, `estudiante/`, `secretaria/`, `tutor_acad/`, `tutor_emp/`.

## Puesta en Marcha

### Requisitos

- JDK 25
- sbt 1.10.7
- PostgreSQL 18 en ejecución localmente

### Base de Datos

```bash
psql -U postgres -f sql/database_setup.sql
```

El script es idempotente: elimina y recrea la base `gestion_practicas` (con sus 13 tipos ENUM y tablas en 3FN) en cada ejecución.

### Configuración de Conexión

Ajusta `src/main/resources/database.properties` si tus credenciales difieren de los valores por defecto (`localhost:5432/gestion_practicas`, usuario `postgres`).

## Comandos

```bash
sbt compile                  # Compilar el proyecto
sbt run                      # Ejecutar la aplicación JavaFX
sbt test                     # Ejecutar todas las pruebas unitarias
sbt "testOnly *SmokeTest"    # Ejecutar una clase de prueba específica
```

El build detecta automáticamente el sistema operativo para seleccionar el clasificador correcto de JavaFX (`win`, `mac` o `linux`).

## Estructura del Proyecto

```
├── build.sbt
├── sql/database_setup.sql          # Esquema PostgreSQL (enums + tablas 3FN)
├── src/main/java/.../enums/        # 13 enums Java (estados y catálogos)
├── src/main/scala/.../models/      # entities/, logic/, db/, dto/
├── src/main/scala/.../controllers/ # Controladores JavaFX por pantalla
├── src/main/scala/.../views/       # Registro declarativo de vistas FXML
├── src/main/resources/fxml/        # Plantillas FXML por rol
└── src/test/scala/                 # Pruebas ScalaTest (db/, logic/)
```

## Licencia

Este proyecto está licenciado bajo la Licencia MIT. Ver [LICENSE](LICENSE) para más detalles.
