import Config.{DeriveConfig, DetectConfig, JavaLibDetectConfig}
import cats.effect.IO
import scopt.OptionParser

import java.nio.file.{Files, Path}
import scala.util.{Failure, Success, Try}

object ArgParser:

  def parse(args: List[String]): IO[JavaLibDetectConfig] = IO.fromEither {
    optParser
      .parse(args, JavaLibDetectConfig())
      .toRight(new IllegalArgumentException("Invalid command line arguments"))
      .flatMap {
        case JavaLibDetectConfig(None) => Left(new IllegalArgumentException("No command specified"))
        case c: JavaLibDetectConfig    => Right(c)
      }
  }

  private val optParser: OptionParser[JavaLibDetectConfig] =
    new OptionParser[JavaLibDetectConfig]("java-lib-detect") {
      help("help")
        .text("Usage information")

      // -------DERIVE-------
      cmd("derive")
        .text("Derive a vector database entry from the given input.")
        .action((_, config) => config.copy(cmd = Option(DeriveConfig())))
        .children(
          arg[String]("input-path")
            .text("The input class file, directory, or JAR")
            .required()
            .validate(path => validateInputPath(path))
            .action { (x, c) =>
              val upd =
                c.cmd.collect { case d: DeriveConfig => d.copy(inputPath = Right(Path.of(x))) }
              c.copy(cmd = upd)
            },
          opt[String]('o', "output-db")
            .text("The output vector DB")
            .validate(p => validateOutputDb(p))
            .action { (x, c) =>
              val upd =
                c.cmd.collect { case d: DeriveConfig => d.copy(outputDb = Right(Path.of(x))) }
              c.copy(cmd = upd)
            },
          opt[String]('l', "label")
            .text("The label to associate the vectors")
            .required()
            .validate(l => if l.trim.nonEmpty then success else failure("Label may not be empty"))
            .action { (x, c) =>
              val upd = c.cmd.collect { case d: DeriveConfig => d.copy(label = Right(x.trim)) }
              c.copy(cmd = upd)
            }
        )
      // -------DETECT-------
      cmd("detect")
        .text("Detect libraries present in the given artefact using an existing DB.")
        .action((_, cfg) => cfg.copy(cmd = Some(DetectConfig())))
        .children(
          arg[String]("input-path")
            .text("The input class file, directory, or JAR")
            .required()
            .validate(path => validateInputPath(path))
            .action { (x, c) =>
              val upd =
                c.cmd.collect { case d: DetectConfig => d.copy(inputPath = Right(Path.of(x))) }
              c.copy(cmd = upd)
            },
          opt[String]('i', "input-db")
            .text("The input vector DB")
            .required()
            .validate(p => validateExistingDb(p))
            .action { (x, c) =>
              val upd =
                c.cmd.collect { case d: DetectConfig => d.copy(inputDb = Right(Path.of(x))) }
              c.copy(cmd = upd)
            },
          opt[Double]('t', "threshold")
            .text("The vector similarity threshold. Default 0.5")
            .validate(th =>
              if th >= 0.0 && th <= 1.0 then success
              else failure("Threshold must be between 0.0 and 1.0")
            )
            .action { (x, c) =>
              val upd = c.cmd.collect { case d: DetectConfig => d.copy(threshold = x) }
              c.copy(cmd = upd)
            }
        )
      // -------HELPERS-------
      private def validateInputPath(p: String) =
        Try(Path.of(p)) match
          case Failure(ex) => failure(ex.getMessage)
          case Success(path) =>
            if Files.exists(path) then success
            else failure(s"Input path does not exist: $p")

      private def validateOutputDb(p: String) = Try(Path.of(p)) match
        case Failure(ex) => failure(ex.getMessage)
        case Success(path) =>
          val parent = Option(path.getParent).getOrElse(Path.of("."))
          if Files.exists(parent) && Files.isWritable(parent) then success
          else failure(s"Cannot write database file at: $p")

      private def validateExistingDb(p: String) = Try(Path.of(p)) match
        case Failure(ex) => failure(ex.getMessage)
        case Success(path) =>
          if Files.isRegularFile(path) && Files.isReadable(path) then success
          else failure(s"Vector database not found or unreadable: $p")
    }
