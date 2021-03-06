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
package nl.lumc.sasc.biopet.extensions.sambamba

import nl.lumc.sasc.biopet.core.config.Configurable
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }
import java.io.File

/** Extension for sambemba merge  */
class SambambaMerge(val root: Configurable) extends Sambamba {
  override val defaultThreads = 4

  @Input(doc = "Bam File[s]")
  var input: List[File] = Nil

  @Output(doc = "Output merged bam PATH")
  var output: File = _

  // @doc: compression_level 6 is average, 0 = no compression, 9 = best
  val compression_level: Option[Int] = config("compression_level", default = 6)

  /** Returns command to execute */
  def cmdLine = required(executable) +
    required("merge") +
    optional("-t", nCoresRequest) +
    optional("-l", compression_level) +
    required(output) +
    repeat("", input)
}
