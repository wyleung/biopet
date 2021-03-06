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
package nl.lumc.sasc.biopet.pipelines.bamtobigwig

import java.io.File

import nl.lumc.sasc.biopet.core.config.Configurable
import nl.lumc.sasc.biopet.core.{ BiopetQScript, PipelineCommand }
import nl.lumc.sasc.biopet.extensions.WigToBigWig
import nl.lumc.sasc.biopet.extensions.igvtools.IGVToolsCount
import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.utils.commandline.{ Output, Input }

/**
 * Created by pjvan_thof on 1/29/15.
 */
class Bam2Wig(val root: Configurable) extends QScript with BiopetQScript {
  def this() = this(null)

  @Input(doc = "Input bam file", required = true)
  var bamFile: File = null

  def init(): Unit = {
  }

  def biopetScript(): Unit = {
    val bs = new BamToChromSizes(this)
    bs.bamFile = bamFile
    bs.chromSizesFile = bamFile.getAbsoluteFile + ".chrom.sizes"
    bs.isIntermediate = true
    add(bs)

    val igvCount = new IGVToolsCount(this)
    igvCount.input = bamFile
    igvCount.genomeChromSizes = bs.chromSizesFile
    igvCount.wig = Some(new File(outputDir, bamFile.getName + ".wig"))
    igvCount.tdf = Some(new File(outputDir, bamFile.getName + ".tdf"))
    add(igvCount)

    val wigToBigWig = new WigToBigWig(this)
    wigToBigWig.inputWigFile = igvCount.wig.get
    wigToBigWig.inputChromSizesFile = bs.chromSizesFile
    wigToBigWig.outputBigWig = new File(outputDir, bamFile.getName + ".bw")
    add(wigToBigWig)
  }
}

object Bam2Wig extends PipelineCommand {
  def apply(root: Configurable, bamFile: File): Bam2Wig = {
    val bamToBigWig = new Bam2Wig(root)
    bamToBigWig.outputDir = bamFile.getParentFile
    bamToBigWig.bamFile = bamFile
    bamToBigWig.init()
    bamToBigWig.biopetScript()
    bamToBigWig
  }
}