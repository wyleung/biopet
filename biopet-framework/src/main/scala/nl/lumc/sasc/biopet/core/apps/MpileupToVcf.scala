package nl.lumc.sasc.biopet.core.apps

import java.io.File
import java.io.PrintWriter
import nl.lumc.sasc.biopet.core.BiopetJavaCommandLineFunction
import nl.lumc.sasc.biopet.core.config.Configurable
import nl.lumc.sasc.biopet.extensions.samtools.SamtoolsMpileup
import org.broadinstitute.gatk.utils.commandline.{ Input, Output }
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.math.round
import scala.math.floor
import scala.collection.JavaConversions._

class MpileupToVcf(val root: Configurable) extends BiopetJavaCommandLineFunction {
  javaMainClass = getClass.getName
  
  @Input(doc = "Input mpileup file", shortName = "mpileup", required = false)
  var inputMpileup: File = _
  
  @Input(doc = "Input bam file", shortName = "bam", required = false)
  var inputBam: File = _
  
  @Output(doc = "Output tag library", shortName = "output", required = true)
  var output: File = _
  
  var minDP:Option[Int] = config("min_dp")
  var minAP:Option[Int] = config("min_ap")
  var homoFraction:Option[Double] = config("homoFraction")
  var ploidy:Option[Int] = config("ploidy")
  var sample: String = _
  var reference: String = config("reference")
  
  override val defaultVmem = "6G"
  memoryLimit = Option(2.0)
  
  if (config.contains("target_bed")) defaults ++= Map("samtoolsmpileup" -> Map("interval_bed" -> config("target_bed").getStringList.head,
                                                                              "disable_baq" -> true, "min_map_quality" -> 1))
  
  override def afterGraph {
    super.afterGraph
    val samtoolsMpileup = new SamtoolsMpileup(this)
  }
  
  override def commandLine = {
    (if (inputMpileup == null) {
      val samtoolsMpileup = new SamtoolsMpileup(this)
      samtoolsMpileup.input = inputBam
      samtoolsMpileup.cmdPipe + " | "
    } else "") + 
      super.commandLine + 
      required("-o", output) + 
      optional("-minDP", minDP) + 
      optional("-minAP", minAP) +
      optional("-homoFraction", homoFraction) +
      optional("-ploidy", ploidy) +
      required("-sample", sample) + 
      (if (inputBam == null) required("-I", inputMpileup) else "")
  }
}

object MpileupToVcf {
  var input: File = _
  var output: File = _
  var sample: String = _
  var minDP = 8
  var minAP = 2
  var homoFraction = 0.8
  var ploidy = 2
  
  /**
   * @param args the command line arguments
   */
  def main(args: Array[String]): Unit = {
    import scala.collection.mutable.Map
    for (t <- 0 until args.size) {
      args(t) match {
        case "-I" => input = new File(args(t+1))
        case "-o" => output = new File(args(t+1))
        case "-minDP" => minDP = args(t+1).toInt
        case "-minAP" => minAP = args(t+1).toInt
        case "-sample" => sample = args(t+1)
        case "-homoFraction" => homoFraction = args(t+1).toDouble
        case "-ploidy" => ploidy = args(t+1).toInt
        case _ =>
      }
    }
    if (input != null && !input.exists) throw new IllegalStateException("Input file does not exist")
    if (output == null) throw new IllegalStateException("Output file not found, use -o")
    if (sample == null) throw new IllegalStateException("sample not given, pls use -sample")
        
    val writer = new PrintWriter(output)
    writer.println("##fileformat=VCFv4.1")
    writer.println("##INFO=<ID=DP,Number=1,Type=Integer,Description=\"Total Depth\">")
    writer.println("##INFO=<ID=AF,Number=A,Type=Float,Description=\"Allele Frequency, for each ALT allele, in the same order as listed\">")
    writer.println("##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"Total Depth\">")
    writer.println("##FORMAT=<ID=AD,Number=.,Type=Integer,Description=\"Total Allele Depth\">")
    writer.println("##FORMAT=<ID=FREQ,Number=A,Type=Float,Description=\"Allele Frequency\">")
    writer.println("##FORMAT=<ID=RFC,Number=1,Type=Integer,Description=\"Reference Forward Reads\">")
    writer.println("##FORMAT=<ID=RRC,Number=1,Type=Integer,Description=\"Reference Reverse Reads\">")
    writer.println("##FORMAT=<ID=AFC,Number=A,Type=Integer,Description=\"Alternetive Forward Reads\">")
    writer.println("##FORMAT=<ID=ARC,Number=A,Type=Integer,Description=\"Alternetive Reverse Reads\">")
    writer.println("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">")
    writer.println("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t" + sample)
    val inputStream = if (input != null) Source.fromFile(input).getLines else Source.stdin.getLines
    class Counts(var forward:Int, var reverse:Int)
    for (line <- inputStream; 
         val values = line.split("\t");
         if values.size > 5) {
      val chr = values(0)
      val pos = values(1)
      val ref = values(2)
      val reads = values(3).toInt
      val mpileup = values(4)
      val qual = values(5)
      
      val counts: Map[String, Counts] = Map(ref.toUpperCase -> new Counts(0,0))
      
      def addCount(s:String) {
        val upper = s.toUpperCase
        if (!counts.contains(upper)) counts += upper -> new Counts(0,0)
        if (s(0).isLower) counts(upper).reverse += 1
        else counts(upper).forward += 1
      }
      
      var t = 0
      while(t < mpileup.size) {
        mpileup(t) match {
          case ',' => {
              addCount(ref.toLowerCase)
              t += 1
          }
          case '.' => {
              addCount(ref.toUpperCase)
              t += 1
          }
          case '^' => t += 2
          case '$' => t += 1
          case '+' | '-' => {
              t += 1
              var size = ""
              var insert = ""
              while (mpileup(t).isDigit) {
                size += mpileup(t)
                t += 1
              }
              for (c <- t until t + size.toInt) insert = insert + mpileup(c)
              t += size.toInt
          }
          case 'a' | 'c' | 't' | 'g' | 'A' | 'C' | 'T' | 'G' =>  {
              addCount(mpileup(t).toString)
              t += 1
          }
          case _ => t += 1
        }
      }
      
      val info: ArrayBuffer[String] = ArrayBuffer("DP=" + reads)
      val format: Map[String, String] = Map("DP" -> reads.toString)
      val alt: ArrayBuffer[String] = new ArrayBuffer
      format += ("RFC" -> counts(ref.toUpperCase).forward.toString)
      format += ("RRC" -> counts(ref.toUpperCase).reverse.toString)
      format += ("AD" -> (counts(ref.toUpperCase).forward + counts(ref.toUpperCase).reverse).toString)
      if (reads >= minDP) for ((key, value) <- counts if key != ref.toUpperCase if value.forward+value.reverse >= minAP) {
        alt += key
        format += ("AD" -> (format("AD") + "," + (value.forward + value.reverse).toString))
        format += ("AFC" -> ( (if (format.contains("AFC")) format("AFC") + "," else "") + value.forward))
        format += ("ARC" -> ( (if (format.contains("ARC")) format("ARC") + "," else "") + value.reverse))
        format += ("FREQ" -> ( (if (format.contains("FREQ")) format("FREQ") + "," else "") + 
                              round((value.forward+value.reverse).toDouble/reads*1E4).toDouble/1E2))
      }
      
      if (alt.size > 0) {
        val ad = for (ad <- format("AD").split(",")) yield ad.toInt
        var left = reads
        val gt = ArrayBuffer[Int]()
        
        for (p <- 0 to alt.size if gt.size < ploidy) {
          var max = -1
          for (a <- 0 until ad.length if ad(a) > (if (max >= 0) ad(max) else -1) && !gt.exists(_ == a)) max = a
          val f = ad(max).toDouble / left
          for (a <- 0 to floor(f).toInt if gt.size < ploidy) gt.append(max)
          if (f - floor(f) >= homoFraction) {
            for (b <- p to ploidy if gt.size < ploidy) gt.append(max)
          }
          left -= ad(max)
        }
        writer.println(Array(chr, pos, ".", ref.toUpperCase, alt.mkString(","), ".", ".", info.mkString(";"), 
                                    "GT:" + format.keys.mkString(":"), gt.sortWith(_ < _).mkString("/") + ":" + format.values.mkString(":")
                            ).mkString("\t"))
      }
    }
    writer.close
  }
}