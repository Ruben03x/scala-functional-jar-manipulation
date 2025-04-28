import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import java_lib_detect.{FileUtils, TargetFile}
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import java.io.FileOutputStream

class TargetFileSpec extends AsyncFunSuite with AsyncIOSpec with Matchers:

  /** Creates a tiny jar file containing a single entry (hello.txt) so we can test magic‑byte
    * detection *and* flattening.
    */
  private def makeJar(tmpDir: Path): IO[Path] = IO.blocking {
    val jarPath = tmpDir.resolve("sample.jar")
    val zos     = new ZipOutputStream(new FileOutputStream(jarPath.toFile))
    val entry   = new ZipEntry("hello.txt")
    zos.putNextEntry(entry)
    zos.write("hello".getBytes())
    zos.closeEntry()
    zos.close()
    jarPath
  }

  test("FileUtils detects jar via magic bytes") {
    val prog = for
      tmp   <- IO.blocking(Files.createTempDirectory("tf-spec-"))
      jar   <- makeJar(tmp)
      isJar <- FileUtils.isJarLike(jar)
      _     <- IO.blocking(FileUtils.recursiveDelete(tmp))
    yield isJar

    prog.asserting(_ shouldBe true)
  }

  test("TargetFile.flatten returns class files inside nested jar") {
    /* Build:  Inner jar containing Test1.class  -> packed into outer jar  */
    val prog = for
      tmpDir <- IO.blocking(Files.createTempDirectory("tf-spec-"))
      classBytes <- IO.blocking {
        val stream = getClass.getClassLoader.getResourceAsStream("Test1.class")
        stream.readAllBytes.nn
      }
      // — build inner.jar with Test1.class —
      innerJar <- IO.blocking {
        val p   = tmpDir.resolve("inner.jar")
        val zos = new ZipOutputStream(new FileOutputStream(p.toFile))
        val e   = new ZipEntry("Test1.class")
        zos.putNextEntry(e)
        zos.write(classBytes)
        zos.closeEntry()
        zos.close()
        p
      }
      // — build outer.jar where entry is the *bytes* of inner.jar —
      outerJar <- IO.blocking {
        val p   = tmpDir.resolve("outer.jar")
        val zos = new ZipOutputStream(new FileOutputStream(p.toFile))
        val e   = new ZipEntry("lib/inner.jar")
        zos.putNextEntry(e)
        zos.write(Files.readAllBytes(innerJar))
        zos.closeEntry()
        zos.close()
        p
      }
      // Use TargetFile to flatten
      tfOpt   <- TargetFile(outerJar)
      classes <- tfOpt.get.classFiles
      _       <- IO.blocking(FileUtils.recursiveDelete(tmpDir))
    yield classes

    prog.asserting { files =>
      files.map(_.getFileName.toString) should contain("Test1.class")
    }
  }

  test("Non-class/non-jar files are ignored by TargetFile.apply") {
    val prog = for
      tmp <- IO.blocking {
        val p = Files.createTempFile("random-", ".txt"); Files.writeString(p, "hello"); p
      }
      res <- TargetFile(tmp)
      _   <- IO.blocking(Files.deleteIfExists(tmp))
    yield res
    prog.asserting(_ shouldBe None)
  }
