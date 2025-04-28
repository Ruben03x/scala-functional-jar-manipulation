import Config.{DeriveConfig, DetectConfig, JavaLibDetectConfig}
import cats.data.EitherT
import cats.effect.*
import cats.effect.std.Console
import java_lib_detect.{Derive, Detect}

object Main extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    val result: IO[ExitCode] = for {
      config: JavaLibDetectConfig <- ArgParser.parse(args)
      exitCode <- config.cmd match {
        case Some(deriveConfig: DeriveConfig) =>
          EitherT
            .fromEither[IO](for {
              inputPath <- deriveConfig.inputPath
              outputDb  <- deriveConfig.outputDb
              label     <- deriveConfig.label
            } yield (inputPath, outputDb, label))
            .leftMap(_ => ExitCode.Error)
            .flatMap { case (inputPath, outputDb, label) =>
              EitherT.liftF(
                Derive.derive(inputPath, outputDb, label)
              )
            }
            .merge

        case Some(detectConfig: DetectConfig) =>
          EitherT
            .fromEither[IO](for {
              inputPath <- detectConfig.inputPath
              inputDb   <- detectConfig.inputDb
              threshold = detectConfig.threshold
            } yield (inputPath, inputDb, threshold))
            .leftMap(_ => ExitCode.Error)
            .flatMap { case (inputPath, inputDb, threshold) =>
              EitherT.liftF(
                Detect.detect(inputPath, inputDb, threshold)
              )
            }
            .merge

        case _ => IO.pure(ExitCode.Error)
      }
    } yield exitCode

    result.handleErrorWith {
      case t: IllegalArgumentException if t.getMessage == "Invalid command line arguments" =>
        // Scopt will automatically print out usage info in this case
        IO.pure(ExitCode.Error)
      case t: Throwable => Console[IO].errorln(t.getMessage).as(ExitCode.Error)
    }
