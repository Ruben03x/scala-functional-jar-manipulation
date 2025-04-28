package java_lib_detect

import cats.effect.{IO, Resource}
import upickle.default.{ReadWriter as RW, *}

import java.nio.file.{Files, Path, StandardOpenOption}

/** Simple binary MessagePack store: label -> sparse-vector (token counts). */
case class VectorDatabase(labelToVector: Map[String, SparseVector])

type SparseVector = Map[String, Int]

object VectorDatabase:

  given RW[VectorDatabase] = macroRW

  /** Persist the database to disk (creates parent dirs if needed). */
  def store(db: VectorDatabase, path: Path): Resource[IO, Path] =
    val bytes = upack.writeToByteArray(writeMsg(db))
    Resource.eval {
      IO.blocking {
        Option(path.getParent).foreach(p => Files.createDirectories(p))
        Files.write(
          path,
          bytes,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
      }
    }

  /** Load database from disk. */
  def load(path: Path): Resource[IO, VectorDatabase] =
    Resource.eval(IO.blocking(Files.readAllBytes(path))).map(bytes => readBinary[VectorDatabase](bytes))
