import java.nio.file.Path

object Config:

  case class JavaLibDetectConfig(cmd: Option[CommandConfig] = None)

  sealed trait CommandConfig {
    def inputPath: Either[String, Path]
  }

  case class DeriveConfig(
    inputPath: Either[String, Path] = Left("Input path unspecified"),
    outputDb: Either[String, Path] = Right(Path.of("vector.db")),
    label: Either[String, String] = Left("Label unspecified")
  ) extends CommandConfig

  case class DetectConfig(
    inputPath: Either[String, Path] = Left("Input path unspecified"),
    inputDb:  Either[String, Path] = Right(Path.of("vector.db")),
    threshold: Double = 0.5
  ) extends CommandConfig
