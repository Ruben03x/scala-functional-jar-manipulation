# Java Lib Detect

This is the repository for the Java library detection tool.

## Requirements

- JDK 21
- SBT 1.10.x

My suggestion is to use Coursier or SDKMan to install the above.

## IntelliJ Setup

- Install IntelliJ and its Scala3 plugin
- Choose File -> New -> Project from existing sources and select this directory
- Choose "BSP"

## Usage

To compile the code, run `sbt compile`. This will generate all the build artifacts under `target`. To build the code
executable, run `sbt stage`. This will include a universal binaries. 

I recommend building symlinks at the root of this project that points to these binaries which will allow for simplified
execution once built, by simpling calling `./java-lib-detect` or `./java-lib-detect.bat`. Do this with:
```
ln -s target/universal/stage/bin/java-lib-detect java-lib-detect
ln -s target/universal/stage/bin/java-lib-detect.bat java-lib-detect.bat
```

The Scopt CLI parsing is largely already been set up (minus input validation, that is up to you). See the `ArgParser`,
`Config`, and `Main` classes for that. Calling `./java-lib-detect --help` will give the following:

```
Usage: java-lib-detect [derive|detect] [options] <args>...

  --help                   Usage information
Command: derive [options] input-path
Derive a vector database entry from the given input.
  input-path               The input class file, directory, or JAR file.
  -o, --output-db <value>  The output vector DB
  -l, --label <value>      The label to associate the vectors generated from the inputs in the database.
Command: detect [options] input-path
Detect the libraries from the vector database entries for the given input.
  input-path               The input class file, directory, or JAR file.
  -i, --input-db <value>   The input vector DB
  -t, --threshold <value>  The vector similarity threshold. Default 0.5.
```

Tests can be run with `sbt test` (explicitly compiled with `sbt Test/compile`), and code can be formatted using
`sbt scalafmt Test/scalafmt`. Example test suites and fixtures have been created as an example.
