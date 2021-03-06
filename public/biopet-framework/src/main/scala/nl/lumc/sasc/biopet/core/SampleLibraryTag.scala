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
package nl.lumc.sasc.biopet.core

import nl.lumc.sasc.biopet.core.config.Configurable
import org.broadinstitute.gatk.utils.commandline.Argument

/**
 * Created by pjvan_thof on 2/16/15.
 *
 * Default implementation for sample and library arguments for pipelines, mainly used for typecasting.
 */
trait SampleLibraryTag extends Configurable {
  @Argument(doc = "Sample ID", shortName = "sample", required = false)
  var sampleId: Option[String] = root match {
    case tag: SampleLibraryTag => tag.sampleId
    case _                     => None
  }

  @Argument(doc = "Library ID", shortName = "library", required = false)
  var libId: Option[String] = root match {
    case tag: SampleLibraryTag => tag.libId
    case _                     => None
  }
}
