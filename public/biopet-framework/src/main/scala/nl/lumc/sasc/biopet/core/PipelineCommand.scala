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

import org.broadinstitute.gatk.queue.util.{ Logging => GatkLogging }
import java.io.File
import nl.lumc.sasc.biopet.core.config.Config
import nl.lumc.sasc.biopet.core.workaround.BiopetQCommandLine

/** Wrapper around executable from Queue */
trait PipelineCommand extends MainCommand with GatkLogging {

  /**
   * Gets location of compiled class of pipeline
   * @return path from classPath to class file
   */
  def pipeline = "/" + getClass.getName.stripSuffix("$").replaceAll("\\.", "/") + ".class"

  /** Class can be used directly from java with -cp option */
  def main(args: Array[String]): Unit = {
    val argsSize = args.size
    for (t <- 0 until argsSize) {
      if (args(t) == "-config" || args(t) == "--config_file") {
        if (t >= argsSize) throw new IllegalStateException("-config needs a value")
        Config.global.loadConfigFile(new File(args(t + 1)))
      }

      if (args(t) == "-cv" || args(t) == "--config_value") {
        val v = args(t + 1).split("=")
        require(v.size == 2, "Value should be formatted like 'key=value' or 'path:path:key=value'")
        val value = v(1)
        val p = v(0).split(":")
        val key = p.last
        val path = p.dropRight(1).toList
        Config.global.addValue(key, value, path)
      }

      if (args(t) == "--logging_level" || args(t) == "-l") {
        args(t + 1).toLowerCase match {
          case "debug" => Logging.logger.setLevel(org.apache.log4j.Level.DEBUG)
          case "info"  => Logging.logger.setLevel(org.apache.log4j.Level.INFO)
          case "warn"  => Logging.logger.setLevel(org.apache.log4j.Level.WARN)
          case "error" => Logging.logger.setLevel(org.apache.log4j.Level.ERROR)
          case _       =>
        }
      }
    }
    for (t <- 0 until argsSize) {
      if (args(t) == "--outputDir" || args(t) == "-outDir") {
        throw new IllegalArgumentException("Commandline argument is deprecated, should use config for this now")
      }
    }

    var argv: Array[String] = Array()
    argv ++= Array("-S", pipeline)
    argv ++= args
    BiopetQCommandLine.main(argv)
  }
}