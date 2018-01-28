package io.bazel.rules_scala.jmh_support

import java.net.URLClassLoader

import scala.annotation.tailrec
import scala.collection.JavaConverters._

import org.openjdk.jmh.generators.core.{ BenchmarkGenerator => JMHGenerator, FileSystemDestination }
import org.openjdk.jmh.generators.asm.ASMGeneratorSource
import org.openjdk.jmh.runner.{ Runner, RunnerException }
import org.openjdk.jmh.runner.options.{ Options, OptionsBuilder }

import java.net.URI
import scala.collection.JavaConverters._
import java.nio.file.{Files, FileSystems, Path}

import io.bazel.rulesscala.jar.JarCreator


/**
 * Wrapper around JMH generator code to find JMH benchmarks and emit generated
 * code for running them.
 *
 * This implementation is derived from Thomas Switzer's excellent `sbt` plugin,
 * `sbt-benchmark`. His original implementation may be found here:
 * https://github.com/tixxit/sbt-benchmark/blob/master/src/main/scala/net/tixxit/sbt/benchmark/BenchmarkPlugin.scala
 */
object BenchmarkGenerator {

  case class BenchmarkGeneratorArgs(
    inputJar: Path,
    resultSourceJar: Path,
    resultResourceJar: Path,
    classPath: List[Path]
  )

  def main(argv: Array[String]): Unit = {
    val args = parseArgs(argv)
    generateJmhBenchmark(
      args.resultSourceJar,
      args.resultResourceJar,
      args.inputJar,
      args.classPath
    )
  }

  private def parseArgs(argv: Array[String]): BenchmarkGeneratorArgs = {
    if (argv.length < 3) {
      System.err.println(
        "Usage: BenchmarkGenerator INPUT_JAR RESULT_JAR RESOURCE_JAR [CLASSPATH_ELEMENT] [CLASSPATH_ELEMENT...]"
      )
      System.exit(1)
    }
    val fs = FileSystems.getDefault

    BenchmarkGeneratorArgs(
      fs.getPath(argv(0)),
      fs.getPath(argv(1)),
      fs.getPath(argv(2)),
      argv.slice(3, argv.length).map { s => fs.getPath(s) }.toList
    )
  }

  private def listFiles(dir: Path): List[Path] = {
    val stream = Files.newDirectoryStream(dir)
    stream.asScala.toList
  }

  private def listFilesRecursively(root: Path)(pred: Path => Boolean): List[Path] = {
    def loop(fs0: List[Path], files: List[Path]): List[Path] = fs0 match {
      case f :: fs if Files.isDirectory(f) => loop(fs ++ listFiles(f), files)
      case f :: fs if pred(f) => loop(fs, f :: files)
      case _ :: fs => loop(fs, files)
      case Nil => files.reverse
    }

    loop(root :: Nil, Nil)
  }

  private def collectClassesFromJar(root: Path): List[Path] = {
    val uri = new URI("jar:file", null, root.toFile.getAbsolutePath, null)
    val fs = FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)
    fs.getRootDirectories.asScala.toList.flatMap { rootDir =>
      listFilesRecursively(rootDir) { (path: Path) =>
        path.getFileName.toString.endsWith(".class")
      }
    }
  }

  // Courtesy of Doug Tangren (https://groups.google.com/forum/#!topic/simple-build-tool/CYeLHcJjHyA)
  private def withClassLoader[A](cp: Seq[Path])(f: => A): A = {
    val originalLoader = Thread.currentThread.getContextClassLoader
    val jmhLoader = classOf[JMHGenerator].getClassLoader
    val classLoader = new URLClassLoader(cp.map(_.toUri.toURL).toArray, jmhLoader)
    try {
      Thread.currentThread.setContextClassLoader(classLoader)
      f
    } finally {
      Thread.currentThread.setContextClassLoader(originalLoader)
    }
  }

  private def withTempDirectory[A](f: Path => A): A = {
    val baseDir = System.getProperty("java.io.tmpdir")
    val tempDir = Files.createTempDirectory("jmh_benchmark_generator")
    try {
      f(tempDir)
    } finally {
      listFilesRecursively(tempDir)(_ => true).reverse.foreach {file =>
        Files.delete(file)
      }
    }
  }

  private def constructJar(output: Path, fileDir: Path): Unit = {
    val creator = new JarCreator(output.toAbsolutePath.toFile.toString)
    creator.addDirectory(fileDir.toFile)
    creator.execute
  }

  private def generateJmhBenchmark(
    sourceJarOut: Path,
    resourceJarOut: Path,
    benchmarkJarPath: Path,
    classpath: List[Path]
  ): Unit = {
    withTempDirectory { tempDir =>
      val tmpResourceDir = tempDir.resolve("resources")
      val tmpSourceDir = tempDir.resolve("sources")

      tmpResourceDir.toFile.mkdir()
      tmpSourceDir.toFile.mkdir()

      withClassLoader(benchmarkJarPath :: classpath) {
        val source = new ASMGeneratorSource
        val destination = new FileSystemDestination(tmpResourceDir.toFile, tmpSourceDir.toFile)
        val generator = new JMHGenerator

        collectClassesFromJar(benchmarkJarPath).foreach { path =>
          source.processClass(Files.newInputStream(path))
        }
        generator.generate(source, destination)
        generator.complete(source, destination)
        if (destination.hasErrors) {
          log("JMH Benchmark generator failed")
          for (e <- destination.getErrors.asScala) {
            log(e.toString)
          }
        }
      }
      constructJar(sourceJarOut, tmpSourceDir)
      constructJar(resourceJarOut, tmpResourceDir)
    }
  }

  private def log(str: String): Unit = {
    System.err.println(s"JMH benchmark generation: $str")
  }
}
