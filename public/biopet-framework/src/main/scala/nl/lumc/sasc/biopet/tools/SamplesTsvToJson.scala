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
package nl.lumc.sasc.biopet.tools

import java.io.File
import nl.lumc.sasc.biopet.core.ToolCommand
import scala.io.Source
import nl.lumc.sasc.biopet.core.config.Config
import nl.lumc.sasc.biopet.utils.ConfigUtils._

/**
 * This tool can convert a tsv to a json file
 */
object SamplesTsvToJson extends ToolCommand {
  case class Args(inputFiles: List[File] = Nil) extends AbstractArgs

  class OptParser extends AbstractOptParser {
    opt[File]('i', "inputFiles") required () unbounded () valueName ("<file>") action { (x, c) =>
      c.copy(inputFiles = x :: c.inputFiles)
    } text ("Input must be a tsv file, first line is seen as header and must at least have a 'sample' column, 'library' column is optional, multiple files allowed")
  }

  /**
   * Executes SamplesTsvToJson
   * @param args
   */
  def main(args: Array[String]): Unit = {
    val argsParser = new OptParser
    val commandArgs: Args = argsParser.parse(args, Args()) getOrElse sys.exit(1)

    val fileMaps = for (inputFile <- commandArgs.inputFiles) yield {
      val reader = Source.fromFile(inputFile)
      val lines = reader.getLines.toList.filter(!_.isEmpty)
      val header = lines.head.split("\t")
      val sampleColumn = header.indexOf("sample")
      val libraryColumn = header.indexOf("library")
      if (sampleColumn == -1) throw new IllegalStateException("sample column does not exist in: " + inputFile)

      val librariesValues: List[Map[String, Any]] = for (tsvLine <- lines.tail) yield {
        val values = tsvLine.split("\t")
        val sample = values(sampleColumn)
        val library = if (libraryColumn != -1) values(libraryColumn) else null
        val valuesMap = (for (
          t <- 0 until values.size;
          if !values(t).isEmpty && t != sampleColumn && t != libraryColumn
        ) yield (header(t) -> values(t))).toMap
        val map: Map[String, Any] = if (library != null) {
          Map("samples" -> Map(sample -> Map("libraries" -> Map(library -> valuesMap))))
        } else {
          Map("samples" -> Map(sample -> valuesMap))
        }
        map
      }
      librariesValues.foldLeft(Map[String, Any]())((acc, kv) => mergeMaps(acc, kv))
    }
    val map = fileMaps.foldLeft(Map[String, Any]())((acc, kv) => mergeMaps(acc, kv))
    val json = mapToJson(map)
    println(json.spaces2)
  }
}
