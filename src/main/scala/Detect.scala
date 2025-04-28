package java_lib_detect

import cats.effect.*
import cats.syntax.all.*
import java.nio.file.Path
import scala.sys.process.*
import scala.util.matching.Regex
import math.sqrt

/** Detection pipeline: build a vector for the candidate artefact, load the
  * vector‑database and return labels whose cosine similarity is >= threshold.
  */
object Detect:

  private val NonWord: Regex = raw"\W+".r

  // ---------------------------------------------------------------------------
  // Public API – called by Main
  // ---------------------------------------------------------------------------
  def detect(inputPath: Path, inputDb: Path, threshold: Double): IO[ExitCode] =
    for
      candidateVec <- buildVector(inputPath)
      _            <- IO.raiseWhen(candidateVec.isEmpty)(new RuntimeException("No strings extracted from input"))
      matches      <- queryDatabase(inputDb, candidateVec, threshold)
      _            <- render(matches, threshold)
    yield ExitCode.Success

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------
  private def buildVector(path: Path): IO[SparseVector] =
    TargetFile(path).flatMap {
      case Some(tf) => tf.classFiles
      case None     => IO.raiseError(new IllegalArgumentException(s"Unsupported input: $path"))
    }.flatMap { files =>
      files.parTraverse(tokensFromClassFile).map(_.foldLeft(Map.empty[String, Int])(mergeVectors))
    }

  private def queryDatabase(dbPath: Path, candidate: SparseVector, th: Double): IO[List[(String, Double)]] =
    VectorDatabase.load(dbPath).use { db =>
      db.labelToVector.toList.parTraverse { case (label, vec) =>
        IO.pure(label -> cosine(candidate, vec))
      }.map(_.filter(_._2 >= th).sortBy(-_._2))
    }

  private def render(matches: List[(String, Double)], th: Double): IO[Unit] =
    if matches.isEmpty then IO.println(f"No libraries matched threshold $th%.2f")
    else
      val msg = matches
        .map { case (lbl, sim) => f"  - $lbl (similarity = $sim%.3f)" }
        .mkString("\n")
      IO.println(s"Matches (>= $th):\n$msg")

  // ---------- vector maths ----------
  private def cosine(a: SparseVector, b: SparseVector): Double =
    val denom = norm(a) * norm(b)
    if denom == 0d then 0d else dot(a, b) / denom

  private def dot(a: SparseVector, b: SparseVector): Double =
    a.iterator.collect { case (k, v) if b.contains(k) => v.toLong * b(k) }.sum.toDouble

  private def norm(v: SparseVector): Double =
    sqrt(v.valuesIterator.map(x => x.toLong * x).sum.toDouble)

  // ---------- token extraction ----------
  private def tokensFromClassFile(cf: Path): IO[SparseVector] =
    IO.blocking {
      val isWindows = System.getProperty("os.name").toLowerCase.startsWith("windows")
      val cmd       = if isWindows then Seq("strings", cf.toString) else Seq("strings", "-a", cf.toString)
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
