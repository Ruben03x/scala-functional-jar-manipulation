package java_lib_detect

import cats.effect.{IO, Resource}
import cats.syntax.all.*

import java.io.{BufferedInputStream, FileInputStream}
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipInputStream}
import scala.jdk.StreamConverters.*

/** Algebraic‑data‑type representing any artefact we may receive from the CLI. */
sealed trait TargetFile:
  def path: Path

  /** Recursively collects *all* `.class` files contained in this artefact.
    * Implemented in a stack‑safe, effect‑aware way so we can call it from
    * any part of the pipeline (Derive or Detect).
    */
  def classFiles: IO[List[Path]]

object TargetFile:

  /* ――― Public factory ――― */
  /** Attempt to wrap `p` in the correct [[TargetFile]] — returns `None` when
    * the path is neither a directory, jar/zip‑like, nor a single class file.
    */
  def apply(p: Path): IO[Option[TargetFile]] =
    for
      isDir   <- IO.blocking(Files.isDirectory(p))
      isClass <- IO.blocking(p.getFileName.toString.endsWith(".class"))
      isJar   <- FileUtils.isJarLike(p)
      tf      <-
        if isDir then IO.pure(Some(Directory(p)))
        else if isJar then IO.pure(Some(JarFile(p)))
        else if isClass then IO.pure(Some(ClassFile(p)))
        else IO.pure(None)
    yield tf

  /* ――― Helpers used by sub‑classes ――― */
  private def recurse(paths: List[Path]): IO[List[Path]] =
    paths.parFlatTraverse { p =>
      TargetFile(p).flatMap {
        case Some(t) => t.classFiles
        case None    => IO.pure(Nil)
      }
    }

  // -------------------------------------------------------------------------
  // Concrete sub‑types
  // -------------------------------------------------------------------------

  final case class ClassFile(path: Path) extends TargetFile:
    override def classFiles: IO[List[Path]] = IO.pure(List(path))

  final case class Directory(path: Path) extends TargetFile:
    override def classFiles: IO[List[Path]] =
      IO.blocking(
        Files.walk(path).toScala(List).filter(p => Files.isRegularFile(p))
      ).flatMap(recurse)

  /** A JAR/WAR/EAR/ZIP artefact. We unzip it into a temporary directory (a
    * [[Resource]] so we guarantee cleanup) and then delegate to [[Directory]].
    */
  final case class JarFile(path: Path) extends TargetFile:
    override def classFiles: IO[List[Path]] =
      unzipToTempDir(path).use { tempDir =>
        Directory(tempDir).classFiles
      }

    /** Unzips the given JAR to a *managed* temp directory.  The directory is
      * recursively deleted when the Resource is released.
      */
    private def unzipToTempDir(jar: Path): Resource[IO, Path] =
      val mkTmp = IO.blocking(Files.createTempDirectory("jar‑"))
      Resource.make(mkTmp) { tmp =>
        IO.blocking(FileUtils.recursiveDelete(tmp)).handleErrorWith(_ => IO.unit)
      }.evalTap { tmpDir =>
        FileUtils.unzip(jar, tmpDir)
      }

end TargetFile

// ============================================================================
//                 Low‑level helpers (magic‑byte detection & unzip)
// ============================================================================
object FileUtils:

  /** Magic bytes that identify a ZIP/JAR/…   50 4B 03 04 */
  private val ZipHeader: Array[Byte] = Array(0x50, 0x4B, 0x03, 0x04).map(_.toByte)

  /** Returns true iff the path *looks like* a JAR/WAR/EAR/ZIP.
    * We never rely on file‑extensions.
    */
  def isJarLike(p: Path): IO[Boolean] = IO.blocking {
    if !Files.isRegularFile(p) then false
    else
      val in = new BufferedInputStream(new FileInputStream(p.toFile))
      try
        val header = Array.ofDim[Byte](4)
        val n      = in.read(header)
        n == 4 && header.sameElements(ZipHeader)
      finally in.close()
  }

  /** Unzips `jar` into `dest`. Nested directory entries are created as needed. */
  def unzip(jar: Path, dest: Path): IO[Unit] = IO.blocking {
    val buffer = Array.ofDim[Byte](8192)
    val zis    = new ZipInputStream(new BufferedInputStream(new FileInputStream(jar.toFile)))
    try
      var entry: ZipEntry | Null = zis.getNextEntry
      while entry != null do
        val targetPath = dest.resolve(entry.getName)
        if entry.isDirectory then Files.createDirectories(targetPath)
        else
          Files.createDirectories(targetPath.getParent)
          val out = Files.newOutputStream(
            targetPath,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
            java.nio.file.StandardOpenOption.WRITE
          )
          try
            var len = zis.read(buffer)
            while len > 0 do
              out.write(buffer, 0, len)
              len = zis.read(buffer)
          finally out.close()
        entry = zis.getNextEntry
      zis.closeEntry()
    finally zis.close()
  }

  /** Recursively deletes `p` if it exists. */
  def recursiveDelete(p: Path): Unit =
    if Files.exists(p) then
      if Files.isDirectory(p) then
        Files.walk(p).toScala(List).reverseIterator.foreach(Files.deleteIfExists)
      else Files.deleteIfExists(p)

