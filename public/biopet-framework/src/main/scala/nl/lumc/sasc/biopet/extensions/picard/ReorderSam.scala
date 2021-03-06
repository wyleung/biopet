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
package nl.lumc.sasc.biopet.extensions.picard

import java.io.File

import org.broadinstitute.gatk.utils.commandline.{ Argument, Input, Output }

import nl.lumc.sasc.biopet.core.config.Configurable

class ReorderSam(val root: Configurable) extends Picard {

  javaMainClass = new picard.sam.ReorderSam().getClass.getName

  @Input(doc = "Input SAM or BAM file", required = true)
  var input: File = null

  @Input(doc = "Reference sequence to reorder reads to match", required = true)
  var reference: File = null

  @Output(doc = "Output SAM or BAM file", required = true)
  var output: File = null

  @Argument(doc = "Allow incomplete dict concordance", required = false)
  var allowIncompleteDictConcordance: Boolean = config("allow_incomplete_dict_concordance", default = false)

  @Argument(doc = "Allow contig length discordance", required = false)
  var allowContigLengthDiscordance: Boolean = config("allow_contig_length_discordance", default = false)

  override def commandLine = super.commandLine +
    conditional(allowIncompleteDictConcordance, "ALLOW_INCOMPLETE_DICT_CONCORDANCE=TRUE") +
    conditional(allowContigLengthDiscordance, "ALLOW_CONTIG_LENGTH_DISCORDANCE=TRUE") +
    required("REFERENCE=", reference, spaceSeparated = false) +
    required("INPUT=", input, spaceSeparated = false) +
    required("OUTPUT=", output, spaceSeparated = false)
}
