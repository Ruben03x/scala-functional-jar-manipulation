import cats.effect.IO
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers.*

class FileTestSpec extends ResourceBasedTestSuite:

  test("The class file 'Test1.class' should end with .class and be non-empty") {
    assertAgainstFile(
      "Test1.class",
      ".class",
      file =>
        // TODO: Test against `file`
        file.getFileName.toString should endWith(".class")
        file.toFile.length().toInt should be > 0
    ).use(IO.pure).unsafeToFuture()
  }
