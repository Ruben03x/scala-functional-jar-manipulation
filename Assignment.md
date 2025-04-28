# Functional Programming Scala Assignment

The purpose of this assignment is to construct a pure, functional approach to ingesting large, possibly nested JAR files, and perform some computation on these.

As we live in the JVM, this will mean creating wrappers to be interoperable with IO, while also benefitting from modular, thread-safe, and immutable functional programming.

## Requirements

You may only use the following technologies:

* Scala Version: 3.6.x
* Build Tool: SBT 1.10.x (not scala-cli scripts)
* IDE Options (I cannot assist you with anything else): 
  * IntelliJ + Scala Plugin 
  * VSCode + [metals](http://scalameta.org/metals/)
  * Vim/Emacs/(insert CLI editor) + [metals](http://scalameta.org/metals/)
* External Dependencies (already set up in the build): 
  * [scopt](https://github.com/scopt/scopt)
  * [cats](https://typelevel.org/cats/index.html)
  * [upickle](https://github.com/com-lihaoyi/upickle)
  * [ScalaTest](https://www.scalatest.org)
* Plugins:
  * [Scalafmt](https://scalameta.org/scalafmt/)
  * [SBT Native Packager](https://www.scala-sbt.org/sbt-native-packager/)

## Limitations

* No mutable variables, fields, or collections.
* No functions that have a return type that is effectively `Unit`. This means there are likely unhandled side-effects. The `IO[Unit]` monad is usually what you want.
* You may not add more dependencies to the build.
* Feel free to change the boilerplate code, this was simply to get you off your feet.

## Brief

When performing static code analysis of JVM bytecode, one tedious procedure is unpacking the class files. Static analysis always has some scalability issues, so multi-threading in some way will eventually become part of the solution. Another problem commonly found is that JAR files may be nested, and have a variety of extensions, e.g., .war, .jar, .ear, etc.

The JDK offers `java.io` and `java.nio` which contain a plethora of options when it comes to downloading files, reading files, as well as zipping, unzipping, (and similarly "jarring"). These JDK classes, however, are not without their side-effects and OOP-nature.

### Tasks

You will be creating a very simple Java library detector. This will operate in two modes:
* `derive`
* `detect`
Look at the `scopt` library for how to create CLI commands each with their own set of arguments/options. I have provided most of the implementation for you.

#### Derive

You are tasked with processing a given file, which may or may not be a JAR to begin with, unpacking it, "analyzing" its content in parallel, closing all system resources, and doing so functionally. Adhere to the limitations and tools from the sections above.

You will then call the `strings` Unix command on each class file to extract all strings, associating these with a particular JAR file. These should be tokenized, and you will create a sparse vector from this. A sparse vector is essentially a `Map[String, Integer]` where the `String` is one of the strings (tokens), and `Integer` is the count of the string.

This will effectively leave you with a dataset of labels and vectors. This dataset can be some class file which you will write to disk. Use the `upickle` library for this, specifically the binary serialization. An example of storing and loading has been provided by the template.

If the label for a given vector already exists in the library, then replace the vector with the new one.

At minimum, your CLI argument must take:
* An input which is either a local directory or file. This is what you will derive from.
* An output vector database file path (local path). If one exists, you will append to it.
* A string label, to associate the vectors derived with some "name" for the detect stage.

#### Detect

Given an input local file or URL, some vector database, and some threshold (`Double`) predict the libraries that meet the cosine similarity of the given threshold for the input file. You will derive strings (and thus a resulting sparse vector) from the given file, perform cosine similarity on this vector and all the vectors in the vector database, and return those that meet the threshold value.

Other than the class files we want to target, you are required to handle JAR files and directories. Detect JAR files via their [magic bytes](https://gist.github.com/iahu/396eaf109ed0969382abdbc9c3f0f029), not their file extensions.

At minimum, your CLI argument must take:
* An input which is either a local directory or file. This is what you will derive from in order to compare to values in the database with.
* An input vector database file path (local path).
* An input threshold value, which defines the cosine similarity a vector in the database must meet with the input file to be considered in that file.

### Using Java Libraries

You are required to make use of functors and monads to wrap around Java classes. For example, you may want to create a type class to represent all the file types you expect, and a means of flattening them (if they are directories or archives), and applying some function on the files (e.g., the `files` system command). One design I can think of off the top of my head is something like:

```scala
import java.nio.file.Path

sealed trait TargetFile[+A <: Path] {
    def path: A
}

object TargetFile {
    // Allows for easy construction of oen of the classes below given a path, might be useful
    def apply[A <: Path](path: A): Option[TargetFile[A]] = {
        ???
    }
}

// Represents a file that "contains" others, and may need to be flattened
sealed trait HasChildren[A <: Path] { this: TargetFile[A] =>
    def children: List[TargetFile[A]]
}

// Add other useful traits here

case class JarFile[A <: Path](path: A) extends TargetFile[A] with HasChildren {
    def children: List[TargetFile[A]] = ???
}

case class Directory[A <: Path](path: A) extends TargetFile[A] with HasChildren {
    def children: List[TargetFile[A]] = ???
}

case class ClassFile[A <: Path](path: A) extends TargetFile[A]

implicit val targetFileFunctor: Functor[TargetFile] = new Functor[TargetFile] {
  def map[A, B](fa: TargetFile[A])(f: A => B): TargetFile[B] = fa match {
    case JarFile(path)         => // ...
    case Directory(path)       => // ..
    case ClassFile(path)       => // ..
  }
}
```

Any file input/output handling must account for failure. Consider the `Either` or `Try` types here.

### File processing

From the input argument, we want to end up with a collection of 0 or more class files. Once you have your class files, you are then required
to make a system call for each. Consider `cats.effect.{IO, Resource}` and `scala.sys.process.*`. The return value of such a call must be 
wrapped in `Resource[IO, _]` or just `IO` so that error states can be safely propagated/flatMapped away.

### Multi-Threading

`cats` allows for parallelism via the `Parallel` type class. See the [documentation](https://typelevel.org/cats/typeclasses/parallel.html).

You are tasked with parallelizing the following independent tasks:
* JAR and directory "unpacking". Unpack these to some temporary directory (see the test suite for an example)
* Calling `strings` on class files.
* Performing cosine similarity on each vector pair.

### Testing

You are required to use `ScalaTest` to test your implementation. Make sure to use the `resources` directory under the `test` directory to store any sample class or JAR files you would like to test against. e.g., 

#### Test requirements

Below is a list of requirements that need to be tested for in some number of tests. 

* Paths to class files, JARs, and directories should be correctly cast to object instances, while other file types are ignored. (Could include a test to detect the magic bytes of a JAR file, for example).
* JAR files and directories can be flattened to class files. Test for safety here, e.g., if `JAR` file is deleted during processing.
* Class files can have strings extracted from them safely, also test what happens if the file is deleted before the `strings` call.
* A class file can be detected within a JAR. Again, check various safety related things (what happens if vector DB is deleted during this).
* Deriving a database with a class file works
* Detect a class file within a JAR file works for some non-zero threshold (compile with debug `(-g)` to stop compiler from optimizing dead-code away)

Hint: Use Maven Central to get some test JARs. One could also create a `build.gradle` with some random dependencies and build "uber JARs" with these
to build ground truth datasets. If you build them as WARs, the library dependencies will be in a separate directory within the WAR as JAR files.
