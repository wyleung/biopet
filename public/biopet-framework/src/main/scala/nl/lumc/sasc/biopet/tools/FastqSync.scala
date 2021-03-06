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
import nl.lumc.sasc.biopet.core.summary.Summarizable

import scala.io.Source
import scala.util.matching.Regex

import scala.annotation.tailrec
import scala.collection.JavaConverters._

import htsjdk.samtools.fastq.{ AsyncFastqWriter, BasicFastqWriter, FastqReader, FastqRecord }
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }

import nl.lumc.sasc.biopet.core.{ BiopetExecutable, BiopetJavaCommandLineFunction, ToolCommand }
import nl.lumc.sasc.biopet.core.config.Configurable

/**
 * FastqSync function class for usage in Biopet pipelines
 *
 * @param root Configuration object for the pipeline
 */
class FastqSync(val root: Configurable) extends BiopetJavaCommandLineFunction with Summarizable {

  javaMainClass = getClass.getName

  @Input(doc = "Original FASTQ file (read 1 or 2)", shortName = "r", required = true)
  var refFastq: File = null

  @Input(doc = "Input read 1 FASTQ file", shortName = "i", required = true)
  var inputFastq1: File = null

  @Input(doc = "Input read 2 FASTQ file", shortName = "j", required = true)
  var inputFastq2: File = null

  @Output(doc = "Output read 1 FASTQ file", shortName = "o", required = true)
  var outputFastq1: File = null

  @Output(doc = "Output read 2 FASTQ file", shortName = "p", required = true)
  var outputFastq2: File = null

  @Output(doc = "Sync statistics", required = true)
  var outputStats: File = null

  override val defaultCoreMemory = 4.0

  // executed command line
  override def commandLine =
    super.commandLine +
      required("-r", refFastq) +
      required("-i", inputFastq1) +
      required("-j", inputFastq2) +
      required("-o", outputFastq1) +
      required("-p", outputFastq2) + " > " +
      required(outputStats)

  def summaryFiles: Map[String, File] = Map()

  def summaryStats: Map[String, Any] = {
    val regex = new Regex("""Filtered (\d*) reads from first read file.
                            |Filtered (\d*) reads from second read file.
                            |Synced read files contain (\d*) reads.""".stripMargin,
      "R1", "R2", "RL")

    val (countFilteredR1, countFilteredR2, countRLeft) =
      if (outputStats.exists) {
        val text = Source
          .fromFile(outputStats)
          .getLines()
          .mkString("\n")
        regex.findFirstMatchIn(text) match {
          case None         => (0, 0, 0)
          case Some(rmatch) => (rmatch.group("R1").toInt, rmatch.group("R2").toInt, rmatch.group("RL").toInt)
        }
      } else (0, 0, 0)

    Map("num_reads_discarded_R1" -> countFilteredR1,
      "num_reads_discarded_R2" -> countFilteredR2,
      "num_reads_kept" -> countRLeft
    )
  }

  override def resolveSummaryConflict(v1: Any, v2: Any, key: String): Any = {
    (v1, v2) match {
      case (v1: Int, v2: Int) => v1 + v2
      case _                  => v1
    }
  }
}

object FastqSync extends ToolCommand {

  /** Regex for capturing read ID ~ taking into account its read pair mark (if present) */
  private val idRegex = "[_/][12]\\s??|\\s".r

  /** Implicit class to allow for lazy retrieval of FastqRecord ID without any read pair mark */
  private implicit class FastqPair(fq: FastqRecord) {
    lazy val fragId = idRegex.split(fq.getReadHeader)(0)
  }

  /**
   * Filters out FastqRecord that are not present in the input iterators, using
   * a reference sequence object
   *
   * @param pre FastqReader over reference FASTQ file
   * @param seqA FastqReader over read 1
   * @param seqB FastqReader over read 2
   * @return
   */
  def syncFastq(pre: FastqReader, seqA: FastqReader, seqB: FastqReader,
                seqOutA: AsyncFastqWriter, seqOutB: AsyncFastqWriter): (Long, Long, Long) = {
    // counters for discarded A and B seqections + total kept
    // NOTE: we are reasigning values to these variables in the recursion below
    var (numDiscA, numDiscB, numKept) = (0, 0, 0)

    /**
     * Syncs read pairs recursively
     *
     * @param pre Reference sequence, assumed to be a superset of both seqA and seqB
     * @param seqA Sequence over read 1
     * @param seqB Sequence over read 2
     * @return
     */
    @tailrec def syncIter(pre: Stream[FastqRecord],
                          seqA: Stream[FastqRecord], seqB: Stream[FastqRecord]): Unit =
      (pre.headOption, seqA.headOption, seqB.headOption) match {
        // where the magic happens!
        case (Some(r), Some(a), Some(b)) =>
          val (nextA, nextB) = (a.fragId == r.fragId, b.fragId == r.fragId) match {
            // all IDs are equal to ref
            case (true, true) =>
              numKept += 1
              seqOutA.write(a)
              seqOutB.write(b)
              (seqA.tail, seqB.tail)
            // B not equal to ref and A is equal, then we discard A and progress
            case (true, false) =>
              numDiscA += 1
              (seqA.tail, seqB)
            // A not equal to ref and B is equal, then we discard B and progress
            case (false, true) =>
              numDiscB += 1
              (seqA, seqB.tail)
            case (false, false) =>
              (seqA, seqB)
          }
          syncIter(pre.tail, nextA, nextB)
        // recursion base case: both iterators have been exhausted
        case (_, None, None) => ;
        // illegal state: reference sequence exhausted but not seqA or seqB
        case (None, Some(_), _) | (None, _, Some(_)) =>
          throw new NoSuchElementException("Reference record stream shorter than expected")
        // keep recursion going if A still has items (we want to count how many)
        case (_, _, None) =>
          numDiscA += 1
          syncIter(pre.tail, seqA.tail, seqB)
        // like above but for B
        case (_, None, _) =>
          numDiscB += 1
          syncIter(pre.tail, seqA, seqB.tail)
      }

    syncIter(pre.iterator.asScala.toStream, seqA.iterator.asScala.toStream, seqB.iterator.asScala.toStream)

    (numDiscA, numDiscB, numKept)
  }

  case class Args(refFastq: File = new File(""),
                  inputFastq1: File = new File(""),
                  inputFastq2: File = new File(""),
                  outputFastq1: File = new File(""),
                  outputFastq2: File = new File("")) extends AbstractArgs

  class OptParser extends AbstractOptParser {

    head(
      s"""
        |$commandName - Sync paired-end FASTQ files.
        |
        |This tool works with gzipped or non-gzipped FASTQ files. The output
        |file will be gzipped when the input is also gzipped.
      """.stripMargin)

    opt[File]('r', "ref") required () valueName "<fastq>" action { (x, c) =>
      c.copy(refFastq = x)
    } validate {
      x => if (x.exists) success else failure("Reference FASTQ file not found")
    } text "Reference FASTQ file"

    opt[File]('i', "in1") required () valueName "<fastq>" action { (x, c) =>
      c.copy(inputFastq1 = x)
    } validate {
      x => if (x.exists) success else failure("Input FASTQ file 1 not found")
    } text "Input FASTQ file 1"

    opt[File]('j', "in2") required () valueName "<fastq[.gz]>" action { (x, c) =>
      c.copy(inputFastq2 = x)
    } validate {
      x => if (x.exists) success else failure("Input FASTQ file 2 not found")
    } text "Input FASTQ file 2"

    opt[File]('o', "out1") required () valueName "<fastq[.gz]>" action { (x, c) =>
      c.copy(outputFastq1 = x)
    } text "Output FASTQ file 1"

    opt[File]('p', "out2") required () valueName "<fastq>" action { (x, c) =>
      c.copy(outputFastq2 = x)
    } text "Output FASTQ file 2"
  }

  /**
   * Parses the command line argument
   *
   * @param args Array of arguments
   * @return
   */
  def parseArgs(args: Array[String]): Args = new OptParser()
    .parse(args, Args())
    .getOrElse(sys.exit(1))

  def main(args: Array[String]): Unit = {

    val commandArgs: Args = parseArgs(args)

    val refReader = new FastqReader(commandArgs.refFastq)
    val AReader = new FastqReader(commandArgs.inputFastq1)
    val BReader = new FastqReader(commandArgs.inputFastq2)
    val AWriter = new AsyncFastqWriter(new BasicFastqWriter(commandArgs.outputFastq1), 3000)
    val BWriter = new AsyncFastqWriter(new BasicFastqWriter(commandArgs.outputFastq2), 3000)

    try {
      val (numDiscA, numDiscB, numKept) = syncFastq(refReader, AReader, BReader, AWriter, BWriter)
      println(s"Filtered $numDiscA reads from first read file.")
      println(s"Filtered $numDiscB reads from second read file.")
      println(s"Synced files contain $numKept reads.")
    } finally {
      refReader.close()
      AReader.close()
      BReader.close()
      AWriter.close()
      BWriter.close()
    }
  }
}
