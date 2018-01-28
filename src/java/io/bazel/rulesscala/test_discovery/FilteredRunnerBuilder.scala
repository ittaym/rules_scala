package io.bazel.rulesscala.test_discovery

import java.lang.annotation.Annotation
import java.util
import java.util.regex.Pattern

import io.bazel.rulesscala.test_discovery.FilteredRunnerBuilder.FilteringRunnerBuilder
import io.bazel.rulesscala.test_discovery.ReflectionUtils.getAllFields
import org.junit.Test
import org.junit.runner.Runner
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.{FrameworkMethod, RunnerBuilder, TestClass}

import scala.collection.JavaConversions._

object FilteredRunnerBuilder {
  type FilteringRunnerBuilder = PartialFunction[(Runner, Class[_], Pattern), Runner]
}

class FilteredRunnerBuilder(builder: RunnerBuilder, filteringRunnerBuilder: FilteringRunnerBuilder) extends RunnerBuilder {
  // Defined by --test_filter bazel flag.
  private val maybePattern = sys.env.get("TESTBRIDGE_TEST_ONLY").map(Pattern.compile)

  def runnerFilter(runner: Runner, testClass: Class[_], pattern: Pattern): Runner = runner

  override def runnerForClass(testClass: Class[_]): Runner = {
    val runner = builder.runnerForClass(testClass)
    maybePattern.flatMap(pattern =>
      filteringRunnerBuilder.lift((runner, testClass, pattern))
    ).getOrElse(runner)
  }
}

class FilteredTestClass(testClass: Class[_], pattern: Pattern) extends TestClass(testClass) {
  override def getAnnotatedMethods(aClass: Class[_ <: Annotation]): util.List[FrameworkMethod] = {
    val methods = super.getAnnotatedMethods(aClass)
    if (aClass == classOf[Test]) methods.filter(method => methodMatchesPattern(method, pattern))
    else methods
  }

  private def methodMatchesPattern(method: FrameworkMethod, pattern: Pattern): Boolean = {
    val testCase = method.getDeclaringClass.getName + "#" + method.getName
    pattern.matcher(testCase).find
  }
}

object JUnitFilteringRunnerBuilder {

  private def hackRunner(runner: BlockJUnit4ClassRunner, testClass: Class[_], pattern: Pattern) = {
    getAllFields(runner.getClass)
      .find(f => f.getName == "testClass" || f.getName == "fTestClass")
      .foreach(field => {
        field.setAccessible(true)
        field.set(runner, new FilteredTestClass(testClass, pattern))
      })
    runner
  }

  val f: FilteringRunnerBuilder = {
    case (runner: BlockJUnit4ClassRunner, testClass: Class[_], pattern: Pattern) =>
      hackRunner(runner, testClass, pattern)
  }
}
