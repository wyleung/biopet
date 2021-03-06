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
package nl.lumc.sasc.biopet.pipelines.kopisu

import java.io.{ FileFilter, BufferedWriter, FileWriter, File }

import nl.lumc.sasc.biopet.core.{ PipelineCommand, _ }
import nl.lumc.sasc.biopet.core.config._
import nl.lumc.sasc.biopet.extensions.Ln
import nl.lumc.sasc.biopet.extensions.conifer.{ ConiferAnalyze, ConiferCall, ConiferRPKM }
import org.broadinstitute.gatk.queue.QScript

import scala.io.Source

class ConiferPipeline(val root: Configurable) extends QScript with BiopetQScript {
  //*
  // Kopisu - Coniferpipeline is a pipeline that can run standalone
  // */
  def this() = this(null)

  /** Input bamfile  */
  @Input(doc = "Bamfile to start from", fullName = "bam", shortName = "bam", required = true)
  var inputBam: File = _

  @Argument(doc = "Label this sample with a name/ID [0-9a-zA-Z] and [-_]",
    fullName = "label",
    shortName = "label", required = false)
  var sampleLabel: String = _

  /** Exon definitions in bed format */
  @Input(doc = "Exon definition file in bed format", fullName = "exon_bed", shortName = "bed", required = false)
  var probeFile: File = config("probeFile")

  @Input(doc = "Previous RPKM files (controls)", fullName = "rpkm_controls", shortName = "rc", required = false)
  var controlsDir: File = config("controlsDir")

  @Argument(doc = "Enable RPKM only mode, generate files for reference db", shortName = "rpkmonly", required = false)
  var RPKMonly: Boolean = false

  val summary = new ConiferSummary(this)

  def init() {

  }

  def input2RPKM(inputBam: File): String = {
    if (!sampleLabel.isEmpty) sampleLabel ++ ".txt"
    else swapExt(inputBam.getName, ".bam", ".txt")
  }

  def input2HDF5(inputBam: File): String = {
    if (!sampleLabel.isEmpty) sampleLabel ++ ".hdf5"
    else swapExt(inputBam.getName, ".bam", ".hdf5")
  }
  def input2Calls(inputBam: File): String = {
    if (!sampleLabel.isEmpty) sampleLabel ++ ".calls.txt"
    else swapExt(inputBam.getName, ".bam", "calls.txt")
  }

  def biopetScript(): Unit = {

    /** Setup RPKM directory */
    val sampleDir: String = outputDir
    val RPKMdir: File = new File(sampleDir + File.separator + "RPKM" + File.separator)
    RPKMdir.mkdir()

    val coniferRPKM = new ConiferRPKM(this)
    coniferRPKM.bamFile = this.inputBam.getAbsoluteFile
    coniferRPKM.probes = this.probeFile
    coniferRPKM.output = new File(RPKMdir, input2RPKM(inputBam))
    add(coniferRPKM)

    if (!RPKMonly) {
      /** Collect the rpkm_output to a temp directory, where we merge with the control files */
      var refRPKMlist: List[File] = Nil
      // Sync the .txt only, these files contain the RPKM Values
      for (controlRPKMfile <- controlsDir.list.filter(_.toLowerCase.endsWith(".txt"))) {
        val target = new File(RPKMdir, controlRPKMfile)
        val source = new File(controlsDir, controlRPKMfile)

        if (!target.exists) {
          add(Ln(this, source, target, false))
          refRPKMlist :+= target
        } else if (!target.equals(source)) {
          target.delete()
          add(Ln(this, source, target, false))
          refRPKMlist :+= target
        }
      }

      val coniferAnalyze = new ConiferAnalyze(this)
      coniferAnalyze.deps = List(coniferRPKM.output) ++ refRPKMlist
      coniferAnalyze.probes = this.probeFile
      coniferAnalyze.rpkmDir = RPKMdir
      coniferAnalyze.output = new File(sampleDir, input2HDF5(inputBam))
      add(coniferAnalyze)

      val coniferCall = new ConiferCall(this)
      coniferCall.input = coniferAnalyze.output
      coniferCall.output = new File(sampleDir, "calls.txt")
      add(coniferCall)

      summary.deps = List(coniferCall.output)
      summary.label = sampleLabel
      summary.calls = coniferCall.output
      summary.out = new File(sampleDir, input2Calls(inputBam))

      add(summary)
    }

  }
}

object ConiferPipeline extends PipelineCommand
