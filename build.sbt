name := "GestionPracticas"

version := "1.0.0"

scalaVersion := "2.13.18"

// Detectar el sistema operativo para cargar la librería nativa de JavaFX correspondiente
val osName = System.getProperty("os.name").toLowerCase
val javaFXClassifier = if (osName.contains("win")) {
  "win"
} else if (osName.contains("mac")) {
  "mac"
} else if (osName.contains("nix") || osName.contains("nux")) {
  "linux"
} else {
  throw new RuntimeException("Sistema operativo no soportado: " + osName)
}

libraryDependencies ++= Seq(
  // Interfaz Gráfica (JavaFX)
  "org.openjfx" % "javafx-controls" % "21" classifier javaFXClassifier,
  "org.openjfx" % "javafx-fxml"     % "21" classifier javaFXClassifier,

  // Persistencia (ScalikeJDBC + PostgreSQL)
  "org.scalikejdbc" %% "scalikejdbc"        % "4.3.0",
  "org.scalikejdbc" %% "scalikejdbc-config" % "4.3.0",
  "org.postgresql"   % "postgresql"         % "42.7.3",
  "org.mindrot"      % "jbcrypt"            % "0.4",

  // Pruebas Unitarias (ScalaTest)
  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)

// Opciones de compilación
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-encoding", "utf8"
)

Test / parallelExecution := false

