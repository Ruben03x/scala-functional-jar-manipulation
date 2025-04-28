package java_lib_detect

import cats.effect.*
import cats.syntax.all.*
import scala.sys.process.*
import scala.util.matching.Regex
import java.nio.file.Path

/** Derivation pipeline: build / update a sparse‑vector database from an input
  * artefact (directory, JAR, nested JAR, single class, ...).  All heavy I/O
  * work happens in `IO`, and parallel sections are expressed via `parTraverse`.
  */
object Derive:

  private val NonWord: Regex = raw"\W+".r

  // ---------------------------------------------------------------------------
  // Public API – called by Main
  // ---------------------------------------------------------------------------
  def derive(inputPath: Path, outputDb: Path, label: String): IO[ExitCode] =
    for
      // 1.  Discover *all* class files (handles nested JARs transparently)
      classFiles <- TargetFile(inputPath).flatMap {
                     case Some(tf) => tf.classFiles
                     case None     => IO.raiseError(new IllegalArgumentException(s"Unsupported input: $inputPath"))
                   }
      _ <- IO.raiseWhen(classFiles.isEmpty)(new RuntimeException("No .class files found in input"))

      // 2.  Extract tokens from each file *in parallel*
      vectors <- classFiles.parTraverse(tokensFromClassFile)
      combined = vectors.foldLeft(Map.empty[String, Int])(mergeVectors)

      // 3.  Load (or create) vector DB and persist merged result
      _ <- VectorDatabase
             .load(outputDb)
             .handleError(_ => VectorDatabase(Map.empty))
             .use { db =>
               val updated = db.copy(labelToVector = db.labelToVector + (label -> combined))
               VectorDatabase.store(updated, outputDb).use(_ => IO.unit)
             }
    yield ExitCode.Success

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------
  def tokensFromClassFile(cf: Path): IO[SparseVector] =
    IO.blocking {
      val isWindows = System.getProperty("os.name").toLowerCase.startsWith("windows")
      val cmd       = if isWindows then Seq("strings", cf.toString)
                    else Seq("strings", "-a", cf.toString)

      val raw: String = cmd.!!

      NonWord.split(raw).iterator
        .filter(_.nonEmpty)
        .map(_.toLowerCase)
        .foldLeft(Map.empty[String, Int]) { (acc, tok) =>
          acc.updatedWith(tok) {
            case Some(n) => Some(n + 1)
            case None    => Some(1)
          }
        }
    }.handleError(_ => Map.empty)


  private def mergeVectors(a: SparseVector, b: SparseVector): SparseVector =
    b.foldLeft(a) { case (acc, (k, v)) => acc.updatedWith(k) {
      case Some(n) => Some(n + v)
      case None    => Some(v)
    }}
