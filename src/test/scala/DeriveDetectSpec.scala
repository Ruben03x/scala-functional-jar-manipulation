import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.all.*
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import java_lib_detect.{TargetFile, Derive, VectorDatabase, FileUtils, SparseVector}
import java.io.FileOutputStream

/** Integration test covering: derive DB from a class file, then detect that
  * same class embedded inside a JAR using a non‑zero similarity threshold.
  */
class DeriveDetectSpec extends AsyncFunSuite with AsyncIOSpec with Matchers:

  private val NonWord = """\W+""".r

  test("Derive then detect a class inside a JAR with threshold 0.1") {
    val prog = for
      tmpDir   <- IO.blocking(Files.createTempDirectory("it-derive-detect-"))
      // ─── copy Test1.class ───────────────────────────────────────────────
      classPath  = tmpDir.resolve("Test1.class")
      _         <- copyResource("Test1.class", classPath)
      // ─── derive DB ─────────────────────────────────────────────────────
      dbPath     = tmpDir.resolve("vector.db")
      _         <- Derive.derive(classPath, dbPath, "demo")
      // ─── build JAR containing Test1.class ──────────────────────────────
      jarPath    = tmpDir.resolve("sample.jar")
      classBytes <- IO.blocking(Files.readAllBytes(classPath))
      _         <- buildJar(jarPath, "Test1.class" -> classBytes)
      // ─── detect ────────────────────────────────────────────────────────
      tfOpt     <- TargetFile(jarPath)
      jarVec    <- tfOpt.get.classFiles.flatMap { files =>
                    files.parTraverse(tokens).map(_.foldLeft(Map.empty[String, Int])(merge))
                  }
      labels    <- VectorDatabase.load(dbPath).use { db =>
                    IO.pure(db.labelToVector.collect { case (lbl, vec) if cosine(jarVec, vec) >= 0.1 => lbl }.toList)
                  }
      _         <- IO.blocking(FileUtils.recursiveDelete(tmpDir))
    yield labels

    prog.asserting(_ should contain ("demo"))
  }

  // ─────────────────────────── helpers ───────────────────────────────────
  private def copyResource(name: String, dest: Path): IO[Unit] = IO.blocking {
    val in  = getClass.getClassLoader.getResourceAsStream(name)
    val out = Files.newOutputStream(dest)
    in.transferTo(out); in.close(); out.close()
  }

  private def buildJar(jar: Path, entry: (String, Array[Byte])): IO[Unit] = IO.blocking {
    val zos = new ZipOutputStream(new FileOutputStream(jar.toFile))
    zos.putNextEntry(new ZipEntry(entry._1)); zos.write(entry._2); zos.closeEntry(); zos.close()
  }

  private def tokens(cf: Path): IO[SparseVector] = Derive.tokensFromClassFile(cf)

  private def merge(a: SparseVector, b: SparseVector): SparseVector =
    b.foldLeft(a) { case (acc, (k, v)) => acc.updatedWith(k) {
      case Some(n) => Some(n + v)
      case None    => Some(v)
    }}

  private def cosine(a: SparseVector, b: SparseVector): Double =
    val denom = norm(a) * norm(b)
    if denom == 0d then 0d else dot(a, b) / denom

  private def dot(a: SparseVector, b: SparseVector): Double =
    a.iterator.collect { case (k, v) if b.contains(k) => v.toLong * b(k) }.sum.toDouble

  private def norm(v: SparseVector): Double = math.sqrt(v.valuesIterator.map(x => x.toLong * x).sum.toDouble)
