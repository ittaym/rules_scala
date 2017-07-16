package third_party.plugin.src.test.io.bazel.rulesscala.dependencyanalyzer

import TestUtil._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class DependencyAnalyzerTest {

  @Test
  def `error on indirect dependency target`(): Unit = {
    val testCode =
      """object Foo {
        |  org.apache.commons.lang3.ArrayUtils.EMPTY_BOOLEAN_ARRAY.length
        |}
      """.stripMargin
    val commonsTarget = "//commons:Target".encode()
    val indirect = Map(apacheCommonsClasspath -> commonsTarget)
    run(testCode, withIndirect = indirect).expectErrorOn(indirect(apacheCommonsClasspath).decoded)
  }

  @Test
  def `error on multiple indirect dependency targets`(): Unit = {
    val testCode =
      """object Foo {
        |  org.apache.commons.lang3.ArrayUtils.EMPTY_BOOLEAN_ARRAY.length
        |  com.google.common.base.Strings.commonPrefix("abc", "abcd")
        |}
      """.stripMargin
    val commonsTarget = "commonsTarget"

    val guavaTarget = "guavaTarget"

    val indirect = Map(apacheCommonsClasspath -> commonsTarget, guavaClasspath -> guavaTarget)
    run(testCode, withIndirect = indirect).expectErrorOn(commonsTarget, guavaTarget)
  }

  @Test
  def `do not give error on direct dependency target`(): Unit = {
    val testCode =
      """object Foo {
        |  org.apache.commons.lang3.ArrayUtils.EMPTY_BOOLEAN_ARRAY.length
        |}
      """.stripMargin
    val commonsTarget = "commonsTarget"

    val direct = Seq(apacheCommonsClasspath)
    val indirect = Map(apacheCommonsClasspath -> commonsTarget)
    run(testCode, withDirect = direct, withIndirect = indirect).noErrorOn(commonsTarget)
  }


  implicit class `nice errors on sequence of strings`(infos: Seq[String]) {

    private def checkErrorContainsMessage(target: String) = (_: String).contains(targetErrorMessage(target))

    private def targetErrorMessage(target: String) = s"Target '$target' is used but isn't explicitly declared, please add it to the deps"

    def expectErrorOn(targets: String*) = targets.foreach(target => assert(
      infos.exists(checkErrorContainsMessage(target)),
      s"expected an error on $target to appear in errors!")
    )

    def noErrorOn(target: String) = assert(
      !infos.exists(checkErrorContainsMessage(target)),
      s"error on $target should not appear in errors!")
  }

  implicit class `decode bazel labels`(targetLabel: String) {
    def decoded() = {
      targetLabel.replace(";", ":")
    }

    def encode() = {
      targetLabel.replace(":", ";")
    }
  }
}
