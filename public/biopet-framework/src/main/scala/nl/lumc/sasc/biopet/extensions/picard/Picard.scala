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
import scala.io.Source

import org.broadinstitute.gatk.utils.commandline.Argument

import nl.lumc.sasc.biopet.core.BiopetJavaCommandLineFunction
import nl.lumc.sasc.biopet.utils.tryToParseNumber

/**
 * General picard extension
 *
 * This is based on using class files directly from the jar, if needed other picard jar can be used
 */
abstract class Picard extends BiopetJavaCommandLineFunction {
  override def subPath = "picard" :: super.subPath

  if (config.contains("picard_jar")) jarFile = config("picard_jar")

  @Argument(doc = "VERBOSITY", required = false)
  var verbosity: Option[String] = config("verbosity")

  @Argument(doc = "QUIET", required = false)
  var quiet: Boolean = config("quiet", default = false)

  @Argument(doc = "VALIDATION_STRINGENCY", required = false)
  var stringency: Option[String] = config("validationstringency")

  @Argument(doc = "COMPRESSION_LEVEL", required = false)
  var compression: Option[Int] = config("compressionlevel")

  @Argument(doc = "MAX_RECORDS_IN_RAM", required = false)
  var maxRecordsInRam: Option[Int] = config("maxrecordsinram")

  @Argument(doc = "CREATE_INDEX", required = false)
  var createIndex: Boolean = config("createindex", default = true)

  @Argument(doc = "CREATE_MD5_FILE", required = false)
  var createMd5: Boolean = config("createmd5", default = false)

  override def versionCommand = {
    if (jarFile != null) executable + " -cp " + jarFile + " " + javaMainClass + " -h"
    else null
  }
  override val versionRegex = """Version: (.*)""".r
  override val versionExitcode = List(0, 1)

  override val defaultCoreMemory = 3.0

  override def commandLine = super.commandLine +
    required("TMP_DIR=" + jobTempDir) +
    optional("VERBOSITY=", verbosity, spaceSeparated = false) +
    conditional(quiet, "QUIET=TRUE") +
    optional("VALIDATION_STRINGENCY=", stringency, spaceSeparated = false) +
    optional("COMPRESSION_LEVEL=", compression, spaceSeparated = false) +
    optional("MAX_RECORDS_IN_RAM=", maxRecordsInRam, spaceSeparated = false) +
    conditional(createIndex, "CREATE_INDEX=TRUE") +
    conditional(createMd5, "CREATE_MD5_FILE=TRUE")
}

object Picard {

  /**
   * This function parse a metrics file in separated values
   * @param file input metrics file
   * @return (header, content)
   */
  def getMetrics(file: File, tag: String = "METRICS CLASS"): Option[Map[String, Any]] =
    if (!file.exists) None
    else {
      val lines = Source.fromFile(file).getLines().toArray

      val start = lines.indexWhere(_.startsWith("## " + tag)) + 1
      val end = lines.indexOf("", start)

      val header = lines(start).split("\t").toList
      val content = (for (i <- (start + 1) until end) yield {
        lines(i).split("\t").map(v => tryToParseNumber(v, true).getOrElse(v)).toList
      }).toList

      Some(Map("content" -> (header :: content)))
    }
}