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
package nl.lumc.sasc.biopet.pipelines.shiva

import java.io.File

import com.google.common.io.Files
import nl.lumc.sasc.biopet.core.config.Config
import nl.lumc.sasc.biopet.extensions.Freebayes
import nl.lumc.sasc.biopet.extensions.gatk.CombineVariants
import nl.lumc.sasc.biopet.tools.{ VcfFilter, MpileupToVcf }
import nl.lumc.sasc.biopet.utils.ConfigUtils
import org.apache.commons.io.FileUtils
import org.broadinstitute.gatk.queue.QSettings
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.{ AfterClass, Test, DataProvider }

import scala.collection.mutable.ListBuffer

/**
 * Created by pjvan_thof on 3/2/15.
 */
class ShivaVariantcallingTest extends TestNGSuite with Matchers {
  def initPipeline(map: Map[String, Any]): ShivaVariantcalling = {
    new ShivaVariantcalling {
      override def configName = "shivavariantcalling"
      override def globalConfig = new Config(ConfigUtils.mergeMaps(map, ShivaVariantcallingTest.config))
      qSettings = new QSettings
      qSettings.runName = "test"
    }
  }

  @DataProvider(name = "shivaVariantcallingOptions")
  def shivaVariantcallingOptions = {
    val bool = Array(true, false)
    (for (bams <- 0 to 3; raw <- bool; bcftools <- bool; freebayes <- bool) yield Array(bams, raw, bcftools, freebayes)).toArray
  }

  @Test(dataProvider = "shivaVariantcallingOptions")
  def testShivaVariantcalling(bams: Int, raw: Boolean, bcftools: Boolean, freebayes: Boolean) = {
    val callers: ListBuffer[String] = ListBuffer()
    if (raw) callers.append("raw")
    if (bcftools) callers.append("bcftools")
    if (freebayes) callers.append("freebayes")
    val map = Map("variantcallers" -> callers.toList)
    val pipeline = initPipeline(map)

    pipeline.inputBams = (for (n <- 1 to bams) yield new File("bam_" + n + ".bam")).toList

    val illegalArgumentException = pipeline.inputBams.isEmpty || (!raw && !bcftools && !freebayes)

    if (illegalArgumentException) intercept[IllegalArgumentException] {
      pipeline.script()
    }

    if (!illegalArgumentException) {
      pipeline.script()

      pipeline.functions.count(_.isInstanceOf[CombineVariants]) shouldBe 1 + (if (raw) 1 else 0)
      //pipeline.functions.count(_.isInstanceOf[Bcftools]) shouldBe (if (bcftools) 1 else 0)
      //FIXME: Can not check for bcftools because of piping
      pipeline.functions.count(_.isInstanceOf[Freebayes]) shouldBe (if (freebayes) 1 else 0)
      pipeline.functions.count(_.isInstanceOf[MpileupToVcf]) shouldBe (if (raw) bams else 0)
      pipeline.functions.count(_.isInstanceOf[VcfFilter]) shouldBe (if (raw) bams else 0)
    }
  }

  @AfterClass def removeTempOutputDir() = {
    FileUtils.deleteDirectory(ShivaVariantcallingTest.outputDir)
  }
}

object ShivaVariantcallingTest {
  val outputDir = Files.createTempDir()

  val config = Map(
    "name_prefix" -> "test",
    "output_dir" -> outputDir,
    "reference" -> "test",
    "gatk_jar" -> "test",
    "samtools" -> Map("exe" -> "test"),
    "bcftools" -> Map("exe" -> "test"),
    "freebayes" -> Map("exe" -> "test")
  )
}