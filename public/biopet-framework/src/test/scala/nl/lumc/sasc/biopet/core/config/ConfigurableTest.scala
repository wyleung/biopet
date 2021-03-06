/**
 * Biopet is built on top of GATK Queue for building bioinformatic
 * pipelines. It is mainly intended to support LUMC SHARK cluster which is running
 * SGE. But other types of HPC that are supported by GATK Queue (such as PBS)
 * should also be able to execute Biopet tools and pipelines.
 *
 * Copyright 2014 Sequencing Analysis Support Core - Leiden University Medical Center
 *
 * Contact us at: sasc@lumc.nl
 *
 * A dual licensing mode is applied. The source code within this project that are
 * not part of GATK Queue is freely available for non-commercial use under an AGPL
 * license; For commercial users or users who do not want to follow the AGPL
 * license, please contact us to obtain a separate license.
 */
package nl.lumc.sasc.biopet.core.config

import nl.lumc.sasc.biopet.utils.{ ConfigUtils, ConfigUtilsTest }
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.Test

/**
 * Created by pjvan_thof on 1/8/15.
 */
class ConfigurableTest extends TestNGSuite with Matchers {
  @Test def testConfigurable: Unit = {
    val classC = new ClassC {
      override def configName = "classc"
      override val globalConfig = new Config(ConfigurableTest.map)
    }

    classC.configPath shouldBe Nil
    classC.configFullPath shouldBe List("classc")
    classC.classB.configPath shouldBe List("classc")
    classC.classB.configFullPath shouldBe List("classc", "classb")
    classC.classB.classA.configPath shouldBe List("classc", "classb")
    classC.classB.classA.configFullPath shouldBe List("classc", "classb", "classa")

    classC.get("k1").asString shouldBe "c1"
    classC.classB.get("k1").asString shouldBe "c1"
    classC.classB.classA.get("k1").asString shouldBe "c1"

    classC.get("notexist", default = "default").asString shouldBe "default"

    classC.get("k1", freeVar = false).asString shouldBe "c1"
    classC.classB.get("k1", freeVar = false).asString shouldBe "b1"
    classC.classB.classA.get("k1", freeVar = false).asString shouldBe "a1"

    classC.get("bla", sample = "sample1", library = "library1").asString shouldBe "bla"
    classC.get("test", sample = "sample1", library = "library1").asString shouldBe "test"
    classC.get("test", sample = "sample1").asString shouldBe "test"
  }
}

abstract class Cfg extends Configurable {
  def get(key: String,
          default: String = null,
          submodule: String = null,
          freeVar: Boolean = true,
          sample: String = null,
          library: String = null) = {
    config(key, default, submodule, freeVar = freeVar, sample = sample, library = library)
  }
}

class ClassA(val root: Configurable) extends Cfg

class ClassB(val root: Configurable) extends Cfg {
  lazy val classA = new ClassA(this)
  // Why this needs to be lazy?
}

class ClassC(val root: Configurable) extends Cfg {
  def this() = this(null)
  lazy val classB = new ClassB(this)
  // Why this needs to be lazy?
}

object ConfigurableTest {
  val map = Map(
    "classa" -> Map(
      "k1" -> "a1"
    ), "classb" -> Map(
      "k1" -> "b1"
    ), "classc" -> Map(
      "k1" -> "c1"
    ), "samples" -> Map(
      "sample1" -> Map(
        "test" -> "test",
        "libraries" -> Map(
          "library1" -> Map(
            "bla" -> "bla"
          )
        )
      )
    )
  )
}
