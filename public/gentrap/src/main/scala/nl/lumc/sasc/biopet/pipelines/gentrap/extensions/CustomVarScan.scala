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
package nl.lumc.sasc.biopet.pipelines.gentrap.extensions

import java.io.File

import org.broadinstitute.gatk.utils.commandline.{ Input, Output }

import nl.lumc.sasc.biopet.core.BiopetCommandLineFunction
import nl.lumc.sasc.biopet.core.config.Configurable
import nl.lumc.sasc.biopet.extensions.{ Bgzip, PythonCommandLineFunction, Tabix }
import nl.lumc.sasc.biopet.extensions.samtools.SamtoolsMpileup
import nl.lumc.sasc.biopet.extensions.varscan.Mpileup2cns

/** Ad-hoc extension for VarScan variant calling that involves 6-command pipe */
// FIXME: generalize piping instead of building something by hand like this!
// Better to do everything quick and dirty here rather than something half-implemented with the objects
class CustomVarScan(val root: Configurable) extends BiopetCommandLineFunction { wrapper =>

  @Input(doc = "Input BAM file", required = true)
  var input: File = null

  @Input(doc = "Reference FASTA file", required = true)
  var reference: File = config("reference")

  @Output(doc = "Output VCF file", required = true)
  var output: File = null

  @Output(doc = "Output VCF file index", required = true)
  lazy val outputIndex: File = new File(output.toString + ".tbi")

  // mpileup, varscan, fix_mpileup.py, binom_test.py, bgzip, tabix
  private def mpileup = new SamtoolsMpileup(wrapper.root) {
    this.input = List(wrapper.input)
    disableBaq = true
    reference = config("reference")
    depth = Option(1000000)
    outputMappingQuality = true
  }

  private def fixMpileup = new PythonCommandLineFunction {
    setPythonScript("fix_mpileup.py", "/nl/lumc/sasc/biopet/pipelines/gentrap/scripts/")
    override val root: Configurable = wrapper.root
    def cmdLine = getPythonCommand
  }

  private def removeEmptyPile = new BiopetCommandLineFunction {
    override val root: Configurable = wrapper.root
    executable = config("exe", default = "grep", freeVar = false)
    override def cmdLine: String = required(executable) + required("-vP") + required("""\t\t""")
  }

  private val varscan = new Mpileup2cns(wrapper.root) {
    strandFilter = Option(0)
    outputVcf = Option(1)
  }

  private val compress = new Bgzip(wrapper.root)

  private val index = new Tabix(wrapper.root) {
    input = compress.output
    p = Option("vcf")
  }

  override def freezeFieldValues(): Unit = {
    varscan.output = Option(new File(wrapper.output.toString.stripSuffix(".gz")))
    compress.input = List(varscan.output.get)
    compress.output = this.output
    super.freezeFieldValues()
    varscan.qSettings = this.qSettings
    varscan.freezeFieldValues()
  }

  override def beforeGraph: Unit = {
    require(output.toString.endsWith(".gz"), "Output must have a .gz file extension")
  }

  def cmdLine: String = {
    // FIXME: manual trigger of commandLine for version retrieval
    mpileup.commandLine
    mpileup.cmdPipe + " | " + fixMpileup.commandLine + " | " + removeEmptyPile.commandLine + " | " +
      varscan.commandLine + " && " + compress.commandLine + " && " + index.commandLine
  }
}
