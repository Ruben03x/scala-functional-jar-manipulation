import cats.effect.{IO, Resource}

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}

trait TestResourceUtils:

  /** Reads a resource from `src/main/test/resources`, and will auto-close it once complete.
    * @param path
    *   the path of the resource relative to the base of `src/main/test/resources`.
    * @return
    *   the resource as a Resource.
    */
  def readResource(path: String): Resource[IO, Array[Byte]] =
    Resource
      .make {
        IO.blocking {
          Option(getClass.getClassLoader.getResourceAsStream(path))
            .getOrElse(throw new RuntimeException(s"Resource not found: $path"))
        }
      } { inStream =>
        IO.blocking(inStream.close()).handleErrorWith(_ => IO.unit)
      }
      .flatMap { inStream =>
        Resource.eval {
          IO.blocking {
            val buffer    = new Array[Byte](1024)
            val output    = new ByteArrayOutputStream
            var bytesRead = inStream.read(buffer)

            while (bytesRead != -1) {
              output.write(buffer, 0, bytesRead)
              bytesRead = inStream.read(buffer)
            }

            output.toByteArray
          }
        }
      }

  /** @param fileExtension
    *   the file extension to suffix the temporary file with.
    * @return
    *   a temporary file with a randomly generated name, prefixed with `test-`, and suffixed with
    *   `fileExtension` at the system's temporary file location.
    */
  protected def createTempFile(fileExtension: String): Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempFile("test-", fileExtension))) { tempFile =>
      IO.blocking(Files.deleteIfExists(tempFile)).void
    }
