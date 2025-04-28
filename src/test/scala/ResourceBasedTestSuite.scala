import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, Resource}
import org.scalatest.compatible.Assertion
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path, StandardOpenOption}

trait ResourceBasedTestSuite extends AsyncFunSuite with AsyncIOSpec with Matchers with TestResourceUtils:

  /**
   * Provides the test assertion a [[Path]] pointing to a copy of the resource given by `resourceName` at a temporary 
   * location.
   * @param resourceName the name of the resource.
   * @param resourceExt the file extension for the resource's temporary copy.
   * @param assertion the test assertions against the resource.
   * @return the assertion wrapped by the [[Resource]]
   */
  protected def assertAgainstFile(resourceName: String, resourceExt: String, assertion: Path => Assertion): Resource[IO, Assertion] = for {
    contentAsBytes <- readResource(resourceName)
    tempFile <- createTempFile(resourceExt)
    _ <- Resource.eval(IO.blocking(Files.write(tempFile, contentAsBytes, StandardOpenOption.TRUNCATE_EXISTING)))
  } yield assertion(tempFile)

