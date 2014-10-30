/**
 * Copyright (c) 2014 Leiden University Medical Center - Sequencing Analysis Support Core <sasc@lumc.nl>
 * @author Wibowo Arindrarto <w.arindrarto@lumc.nl>
 */
package nl.lumc.sasc.biopet.tools

import java.io.File
import java.nio.file.Paths
import scala.collection.JavaConverters._

import htsjdk.samtools.SAMFileHeader
import htsjdk.samtools.SAMLineParser
import htsjdk.samtools.SAMReadGroupRecord
import htsjdk.samtools.SAMRecord
import htsjdk.samtools.SAMSequenceRecord
import htsjdk.samtools.SamReader
import htsjdk.samtools.SamReaderFactory
import htsjdk.samtools.ValidationStringency
import htsjdk.samtools.util.Interval
import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.Test

class WipeReadsUnitTest extends TestNGSuite with Matchers {

  import WipeReads._

  private def resourcePath(p: String): String =
    Paths.get(getClass.getResource(p).toURI).toString

  private val samP: SAMLineParser = {
    val samh = new SAMFileHeader
    samh.addSequence(new SAMSequenceRecord("chrQ", 10000))
    samh.addReadGroup(new SAMReadGroupRecord("001"))
    samh.addReadGroup(new SAMReadGroupRecord("002"))
    new SAMLineParser(samh)
  }

  private def makeSams(raws: String*): Seq[SAMRecord] =
    raws.map(s => samP.parseLine(s))

  private def makeTempBam(): File =
    File.createTempFile("WipeReads", java.util.UUID.randomUUID.toString + ".bam")

  private def makeTempBamIndex(bam: File): File =
    new File(bam.getAbsolutePath.stripSuffix(".bam") + ".bai")

  private def makeSamReader(f: File): SamReader = SamReaderFactory
    .make()
    .validationStringency(ValidationStringency.LENIENT)
    .open(f)

  val bloomSize: Long = 1000
  val bloomFp: Double = 1e-10

  val sBamFile1 = new File(resourcePath("/single01.bam"))
  val sBamRecs1 = makeSams(
    "r02\t0\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001",
    "r01\t16\tchrQ\t190\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001",
    "r01\t16\tchrQ\t290\t60\t10M\t*\t0\t0\tGGGGGAAAAA\tGGGGGGGGGG\tRG:Z:001",
    "r04\t0\tchrQ\t450\t60\t10M\t*\t0\t0\tCGTACGTACG\tEEFFGGHHII\tRG:Z:001",
    "r03\t16\tchrQ\t690\t60\t10M\t*\t0\t0\tCCCCCTTTTT\tHHHHHHHHHH\tRG:Z:001",
    "r05\t0\tchrQ\t890\t60\t5M200N5M\t*\t0\t0\tGATACGATAC\tFEFEFEFEFE\tRG:Z:001",
    "r06\t4\t*\t0\t0\t*\t*\t0\t0\tATATATATAT\tHIHIHIHIHI\tRG:Z:001"
  )

  val sBamFile2 = new File(resourcePath("/single02.bam"))
  val sBamRecs2 = makeSams(
    "r02\t0\tchrQ\t50\t60\t10M\t*\t0\t0\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001",
    "r01\t16\tchrQ\t190\t30\t10M\t*\t0\t0\tGGGGGAAAAA\tGGGGGGGGGG\tRG:Z:002",
    "r01\t16\tchrQ\t290\t30\t10M\t*\t0\t0\tGGGGGAAAAA\tGGGGGGGGGG\tRG:Z:002",
    "r04\t0\tchrQ\t450\t60\t10M\t*\t0\t0\tCGTACGTACG\tEEFFGGHHII\tRG:Z:001",
    "r07\t16\tchrQ\t460\t60\t10M\t*\t0\t0\tCGTACGTACG\tEEFFGGHHII\tRG:Z:001",
    "r07\t16\tchrQ\t860\t30\t10M\t*\t0\t0\tCGTACGTACG\tEEFFGGHHII\tRG:Z:001",
    "r06\t4\t*\t0\t0\t*\t*\t0\t0\tATATATATAT\tHIHIHIHIHI\tRG:Z:001",
    "r08\t4\t*\t0\t0\t*\t*\t0\t0\tATATATATAT\tHIHIHIHIHI\tRG:Z:002"
  )

  val sBamFile3 = new File(resourcePath("/single03.bam"))
  val sBamFile4 = new File(resourcePath("/single04.bam"))

  val pBamFile1 = new File(resourcePath("/paired01.bam"))
  val pBamRecs1 = makeSams(
    "r02\t99\tchrQ\t50\t60\t10M\t=\t90\t50\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001",
    "r02\t147\tchrQ\t90\t60\t10M\t=\t50\t-50\tATGCATGCAT\tEEFFGGHHII\tRG:Z:001",
    "r01\t163\tchrQ\t150\t60\t10M\t=\t190\t50\tAAAAAGGGGG\tGGGGGGGGGG\tRG:Z:001",
    "r01\t83\tchrQ\t190\t60\t10M\t=\t150\t-50\tGGGGGAAAAA\tGGGGGGGGGG\tRG:Z:001",
    "r01\t163\tchrQ\t250\t60\t10M\t=\t290\t50\tAAAAAGGGGG\tGGGGGGGGGG\tRG:Z:001",
    "r01\t83\tchrQ\t290\t60\t10M\t=\t250\t-50\tGGGGGAAAAA\tGGGGGGGGGG\tRG:Z:001",
    "r04\t99\tchrQ\t450\t60\t10M\t=\t490\t50\tCGTACGTACG\tEEFFGGHHII\tRG:Z:001",
    "r04\t147\tchrQ\t490\t60\t10M\t=\t450\t-50\tGCATGCATGC\tEEFFGGHHII\tRG:Z:001",
    "r03\t163\tchrQ\t650\t60\t10M\t=\t690\t50\tTTTTTCCCCC\tHHHHHHHHHH\tRG:Z:001",
    "r03\t83\tchrQ\t690\t60\t10M\t=\t650\t-50\tCCCCCTTTTT\tHHHHHHHHHH\tRG:Z:001",
    "r05\t99\tchrQ\t890\t60\t5M200N5M\t=\t1140\t50\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001",
    "r05\t147\tchrQ\t1140\t60\t10M\t=\t890\t-50\tATGCATGCAT\tEEFFGGHHII\tRG:Z:001",
    "r06\t4\t*\t0\t0\t*\t*\t0\t0\tATATATATAT\tHIHIHIHIHI\tRG:Z:001",
    "r06\t4\t*\t0\t0\t*\t*\t0\t0\tGCGCGCGCGC\tHIHIHIHIHI\tRG:Z:001"
  )

  val pBamFile2 = new File(resourcePath("/paired02.bam"))
  val pBamRecs2 = makeSams(
    "r02\t99\tchrQ\t50\t60\t10M\t=\t90\t50\tTACGTACGTA\tEEFFGGHHII\tRG:Z:001",
    "r02\t147\tchrQ\t90\t60\t10M\t=\t50\t-50\tATGCATGCAT\tEEFFGGHHII\tRG:Z:001",
    "r01\t163\tchrQ\t150\t30\t10M\t=\t190\t50\tAAAAAGGGGG\tGGGGGGGGGG\tRG:Z:002",
    "r01\t83\tchrQ\t190\t30\t10M\t=\t150\t-50\tGGGGGAAAAA\tGGGGGGGGGG\tRG:Z:002",
    "r01\t163\tchrQ\t250\t30\t10M\t=\t290\t50\tAAAAAGGGGG\tGGGGGGGGGG\tRG:Z:002",
    "r01\t83\tchrQ\t290\t30\t10M\t=\t250\t-50\tGGGGGAAAAA\tGGGGGGGGGG\tRG:Z:002",
    "r04\t99\tchrQ\t450\t60\t10M\t=\t490\t50\tCGTACGTACG\tEEFFGGHHII\tRG:Z:001",
    "r04\t147\tchrQ\t490\t60\t10M\t=\t450\t-50\tGCATGCATGC\tEEFFGGHHII\tRG:Z:001",
    "r06\t4\t*\t0\t0\t*\t*\t0\t0\tATATATATAT\tHIHIHIHIHI\tRG:Z:001",
    "r08\t4\t*\t0\t0\t*\t*\t0\t0\tGCGCGCGCGC\tHIHIHIHIHI\tRG:Z:002"
  )

  val pBamFile3 = new File(resourcePath("/paired03.bam"))
  val BedFile1 = new File(resourcePath("/rrna01.bed"))
  val minArgList = List("-I", sBamFile1.toString, "-l", BedFile1.toString, "-o", "mock.bam")

  @Test def testMakeFeatureFromBed() = {
    val intervals: List[Interval] = makeIntervalFromFile(BedFile1)
    intervals.length should be(3)
    intervals.head.getSequence should ===("chrQ")
    intervals.head.getStart should be(991)
    intervals.head.getEnd should be(1000)
    intervals.last.getSequence should ===("chrQ")
    intervals.last.getStart should be(291)
    intervals.last.getEnd should be(320)
  }

  @Test def testSingleBamDefault() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320), // overlaps r01, second hit,
      new Interval("chrQ", 451, 480), // overlaps r04
      new Interval("chrQ", 991, 1000) // overlaps nothing; lies in the spliced region of r05
    )
    // NOTE: while it's possible to have our filter produce false positives
    //       it is highly unlikely in our test cases as we are setting a very low FP rate
    //       and only filling the filter with a few items
    val filterNotFunc = makeFilterNotFunction(intervals, sBamFile1, bloomSize = bloomSize, bloomFp = bloomFp)
    // by default, set elements are SAM record read names
    filterNotFunc(sBamRecs1(0)) shouldBe false
    filterNotFunc(sBamRecs1(1)) shouldBe true
    filterNotFunc(sBamRecs1(2)) shouldBe true
    filterNotFunc(sBamRecs1(3)) shouldBe true
    filterNotFunc(sBamRecs1(4)) shouldBe false
    filterNotFunc(sBamRecs1(5)) shouldBe false
    filterNotFunc(sBamRecs1(6)) shouldBe false
  }

  @Test def testSingleBamIntervalWithoutChr() = {
    val intervals: List[Interval] = List(
      new Interval("Q", 291, 320),
      new Interval("chrQ", 451, 480),
      new Interval("P", 191, 480)
    )
    val filterNotFunc = makeFilterNotFunction(intervals, sBamFile1, bloomSize = bloomSize, bloomFp = bloomFp)
    filterNotFunc(sBamRecs1(0)) shouldBe false
    filterNotFunc(sBamRecs1(1)) shouldBe true
    filterNotFunc(sBamRecs1(2)) shouldBe true
    filterNotFunc(sBamRecs1(3)) shouldBe true
    filterNotFunc(sBamRecs1(4)) shouldBe false
    filterNotFunc(sBamRecs1(5)) shouldBe false
    filterNotFunc(sBamRecs1(6)) shouldBe false
  }

  @Test def testSingleBamDefaultPartialExonOverlap() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 881, 1000) // overlaps first exon of r05
    )
    val filterNotFunc = makeFilterNotFunction(intervals, sBamFile1, bloomSize = bloomSize, bloomFp = bloomFp)
    filterNotFunc(sBamRecs1(0)) shouldBe false
    filterNotFunc(sBamRecs1(1)) shouldBe false
    filterNotFunc(sBamRecs1(2)) shouldBe false
    filterNotFunc(sBamRecs1(3)) shouldBe false
    filterNotFunc(sBamRecs1(4)) shouldBe false
    filterNotFunc(sBamRecs1(5)) shouldBe true
    filterNotFunc(sBamRecs1(6)) shouldBe false
  }

  @Test def testSingleBamDefaultNoExonOverlap() = {
    val intervals: List[Interval] = List(
      new Interval("chrP", 881, 1000),
      new Interval("chrQ", 900, 920)
    )
    val filterNotFunc = makeFilterNotFunction(intervals, sBamFile1, bloomSize = bloomSize, bloomFp = bloomFp)
    filterNotFunc(sBamRecs1(0)) shouldBe false
    filterNotFunc(sBamRecs1(1)) shouldBe false
    filterNotFunc(sBamRecs1(2)) shouldBe false
    filterNotFunc(sBamRecs1(3)) shouldBe false
    filterNotFunc(sBamRecs1(4)) shouldBe false
    filterNotFunc(sBamRecs1(5)) shouldBe false
    filterNotFunc(sBamRecs1(5)) shouldBe false
    filterNotFunc(sBamRecs1(6)) shouldBe false
  }

  @Test def testSingleBamFilterOutMultiNotSet() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320), // overlaps r01, second hit,
      new Interval("chrQ", 451, 480), // overlaps r04
      new Interval("chrQ", 991, 1000) // overlaps nothing; lies in the spliced region of r05
    )
    val filterNotFunc = makeFilterNotFunction(intervals, sBamFile1, bloomSize = bloomSize, bloomFp = bloomFp,
      filterOutMulti = false)
    filterNotFunc(sBamRecs1(0)) shouldBe false
    filterNotFunc(sBamRecs1(1)) shouldBe false
    filterNotFunc(sBamRecs1(2)) shouldBe true
    filterNotFunc(sBamRecs1(3)) shouldBe true
    filterNotFunc(sBamRecs1(4)) shouldBe false
    filterNotFunc(sBamRecs1(5)) shouldBe false
    filterNotFunc(sBamRecs1(6)) shouldBe false
  }

  @Test def testSingleBamFilterMinMapQ() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320),
      new Interval("chrQ", 451, 480)
    )
    val filterNotFunc = makeFilterNotFunction(intervals, sBamFile2, bloomSize = bloomSize, bloomFp = bloomFp,
      minMapQ = 60)
    filterNotFunc(sBamRecs2(0)) shouldBe false
    // r01 is not in since it is below the MAPQ threshold
    filterNotFunc(sBamRecs2(1)) shouldBe false
    filterNotFunc(sBamRecs2(2)) shouldBe false
    filterNotFunc(sBamRecs2(3)) shouldBe true
    filterNotFunc(sBamRecs2(4)) shouldBe true
    filterNotFunc(sBamRecs2(5)) shouldBe true
    filterNotFunc(sBamRecs2(6)) shouldBe false
    filterNotFunc(sBamRecs2(7)) shouldBe false
  }

  @Test def testSingleBamFilterMinMapQFilterOutMultiNotSet() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320),
      new Interval("chrQ", 451, 480)
    )
    val filterNotFunc = makeFilterNotFunction(intervals, sBamFile2, bloomSize = bloomSize, bloomFp = bloomFp,
      minMapQ = 60, filterOutMulti = false)
    filterNotFunc(sBamRecs2(0)) shouldBe false
    filterNotFunc(sBamRecs2(1)) shouldBe false
    // this r01 is not in since it is below the MAPQ threshold
    filterNotFunc(sBamRecs2(2)) shouldBe false
    filterNotFunc(sBamRecs2(3)) shouldBe true
    filterNotFunc(sBamRecs2(4)) shouldBe true
    // this r07 is not in since filterOuMulti is false
    filterNotFunc(sBamRecs2(5)) shouldBe false
    filterNotFunc(sBamRecs2(6)) shouldBe false
    filterNotFunc(sBamRecs2(7)) shouldBe false
  }

  @Test def testSingleBamFilterReadGroupIDs() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320),
      new Interval("chrQ", 451, 480)
    )
    val filterNotFunc = makeFilterNotFunction(intervals, sBamFile2, bloomSize = bloomSize, bloomFp = bloomFp,
      readGroupIds = Set("002", "003"))
    filterNotFunc(sBamRecs2(0)) shouldBe false
    // only r01 is in the set since it is RG 002
    filterNotFunc(sBamRecs2(1)) shouldBe true
    filterNotFunc(sBamRecs2(2)) shouldBe true
    filterNotFunc(sBamRecs2(3)) shouldBe false
    filterNotFunc(sBamRecs2(4)) shouldBe false
    filterNotFunc(sBamRecs2(5)) shouldBe false
    filterNotFunc(sBamRecs2(6)) shouldBe false
    filterNotFunc(sBamRecs2(7)) shouldBe false
  }

  @Test def testPairBamDefault() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320), // overlaps r01, second hit,
      new Interval("chrQ", 451, 480), // overlaps r04
      new Interval("chrQ", 991, 1000) // overlaps nothing; lies in the spliced region of r05
    )
    val filterNotFunc = makeFilterNotFunction(intervals, pBamFile1, bloomSize = bloomSize, bloomFp = bloomFp)
    filterNotFunc(pBamRecs1(0)) shouldBe false
    filterNotFunc(pBamRecs1(1)) shouldBe false
    filterNotFunc(pBamRecs1(2)) shouldBe true
    filterNotFunc(pBamRecs1(3)) shouldBe true
    filterNotFunc(pBamRecs1(4)) shouldBe true
    filterNotFunc(pBamRecs1(5)) shouldBe true
    filterNotFunc(pBamRecs1(6)) shouldBe true
    filterNotFunc(pBamRecs1(7)) shouldBe true
    filterNotFunc(pBamRecs1(8)) shouldBe false
    filterNotFunc(pBamRecs1(9)) shouldBe false
    filterNotFunc(pBamRecs1(10)) shouldBe false
    filterNotFunc(pBamRecs1(11)) shouldBe false
    filterNotFunc(pBamRecs1(12)) shouldBe false
    filterNotFunc(pBamRecs1(13)) shouldBe false
  }

  @Test def testPairBamPartialExonOverlap() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 891, 1000)
    )
    val filterNotFunc = makeFilterNotFunction(intervals, pBamFile1, bloomSize = bloomSize, bloomFp = bloomFp)
    filterNotFunc(pBamRecs1(0)) shouldBe false
    filterNotFunc(pBamRecs1(1)) shouldBe false
    filterNotFunc(pBamRecs1(2)) shouldBe false
    filterNotFunc(pBamRecs1(3)) shouldBe false
    filterNotFunc(pBamRecs1(4)) shouldBe false
    filterNotFunc(pBamRecs1(5)) shouldBe false
    filterNotFunc(pBamRecs1(6)) shouldBe false
    filterNotFunc(pBamRecs1(7)) shouldBe false
    filterNotFunc(pBamRecs1(8)) shouldBe false
    filterNotFunc(pBamRecs1(9)) shouldBe false
    filterNotFunc(pBamRecs1(10)) shouldBe true
    filterNotFunc(pBamRecs1(11)) shouldBe true
    filterNotFunc(pBamRecs1(12)) shouldBe false
    filterNotFunc(pBamRecs1(13)) shouldBe false
  }

  @Test def testPairBamFilterOutMultiNotSet() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320), // overlaps r01, second hit,
      new Interval("chrQ", 451, 480), // overlaps r04
      new Interval("chrQ", 991, 1000) // overlaps nothing; lies in the spliced region of r05
    )
    val filterNotFunc = makeFilterNotFunction(intervals, pBamFile1, bloomSize = bloomSize, bloomFp = bloomFp,
      filterOutMulti = false)
    filterNotFunc(pBamRecs1(0)) shouldBe false
    filterNotFunc(pBamRecs1(1)) shouldBe false
    filterNotFunc(pBamRecs1(2)) shouldBe false
    filterNotFunc(pBamRecs1(3)) shouldBe false
    filterNotFunc(pBamRecs1(4)) shouldBe true
    filterNotFunc(pBamRecs1(5)) shouldBe true
    filterNotFunc(pBamRecs1(6)) shouldBe true
    filterNotFunc(pBamRecs1(7)) shouldBe true
    filterNotFunc(pBamRecs1(8)) shouldBe false
    filterNotFunc(pBamRecs1(9)) shouldBe false
    filterNotFunc(pBamRecs1(10)) shouldBe false
    filterNotFunc(pBamRecs1(11)) shouldBe false
    filterNotFunc(pBamRecs1(12)) shouldBe false
    filterNotFunc(pBamRecs1(13)) shouldBe false
  }

  @Test def testPairBamFilterMinMapQ() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320),
      new Interval("chrQ", 451, 480)
    )
    val filterNotFunc = makeFilterNotFunction(intervals, pBamFile2, bloomSize = bloomSize, bloomFp = bloomFp,
      minMapQ = 60)
    // r01 is not in since it is below the MAPQ threshold
    filterNotFunc(pBamRecs2(0)) shouldBe false
    filterNotFunc(pBamRecs2(1)) shouldBe false
    filterNotFunc(pBamRecs2(2)) shouldBe false
    filterNotFunc(pBamRecs2(3)) shouldBe false
    filterNotFunc(pBamRecs2(4)) shouldBe false
    filterNotFunc(pBamRecs2(5)) shouldBe false
    filterNotFunc(pBamRecs2(6)) shouldBe true
    filterNotFunc(pBamRecs2(7)) shouldBe true
    filterNotFunc(pBamRecs2(8)) shouldBe false
    filterNotFunc(pBamRecs2(9)) shouldBe false
  }

  @Test def testPairBamFilterReadGroupIDs() = {
    val intervals: List[Interval] = List(
      new Interval("chrQ", 291, 320),
      new Interval("chrQ", 451, 480)
    )
    val filterNotFunc = makeFilterNotFunction(intervals, pBamFile2, bloomSize = bloomSize, bloomFp = bloomFp,
      readGroupIds = Set("002", "003"))
    // only r01 is in the set since it is RG 002
    filterNotFunc(pBamRecs2(0)) shouldBe false
    filterNotFunc(pBamRecs2(1)) shouldBe false
    filterNotFunc(pBamRecs2(2)) shouldBe true
    filterNotFunc(pBamRecs2(3)) shouldBe true
    filterNotFunc(pBamRecs2(4)) shouldBe true
    filterNotFunc(pBamRecs2(5)) shouldBe true
    filterNotFunc(pBamRecs2(6)) shouldBe false
    filterNotFunc(pBamRecs2(7)) shouldBe false
    filterNotFunc(pBamRecs2(8)) shouldBe false
    filterNotFunc(pBamRecs2(9)) shouldBe false
  }

  @Test def testWriteSingleBamDefault() = {
    val mockFilterOutFunc = (r: SAMRecord) => Set("r03", "r04", "r05").contains(r.getReadName)
    val outBam = makeTempBam()
    val outBamIndex = makeTempBamIndex(outBam)
    outBam.deleteOnExit()
    outBamIndex.deleteOnExit()

    val stdout = new java.io.ByteArrayOutputStream
    Console.withOut(stdout) {
      writeFilteredBam(mockFilterOutFunc, sBamFile1, outBam)
    }
    stdout.toString should ===(
      "input_bam\toutput_bam\tcount_included\tcount_excluded\n%s\t%s\t%d\t%d\n"
        .format(sBamFile1.getName, outBam.getName, 4, 3)
    )

    val exp = makeSamReader(sBamFile3).asScala
    val obs = makeSamReader(outBam).asScala
    for ((e, o) <- exp.zip(obs))
      e.getSAMString should ===(o.getSAMString)
    outBam should be('exists)
    outBamIndex should be('exists)
  }

  @Test def testWriteSingleBamAndFilteredBAM() = {
    val mockFilterOutFunc = (r: SAMRecord) => Set("r03", "r04", "r05").contains(r.getReadName)
    val outBam = makeTempBam()
    val outBamIndex = makeTempBamIndex(outBam)
    outBam.deleteOnExit()
    outBamIndex.deleteOnExit()
    val filteredOutBam = makeTempBam()
    val filteredOutBamIndex = makeTempBamIndex(filteredOutBam)
    filteredOutBam.deleteOnExit()
    filteredOutBamIndex.deleteOnExit()

    val stdout = new java.io.ByteArrayOutputStream
    Console.withOut(stdout) {
      writeFilteredBam(mockFilterOutFunc, sBamFile1, outBam, filteredOutBam = Some(filteredOutBam))
    }
    stdout.toString should ===(
      "input_bam\toutput_bam\tcount_included\tcount_excluded\n%s\t%s\t%d\t%d\n"
        .format(sBamFile1.getName, outBam.getName, 4, 3)
    )

    val exp = makeSamReader(sBamFile4).asScala
    val obs = makeSamReader(filteredOutBam).asScala
    for ((e, o) <- exp.zip(obs))
      e.getSAMString should ===(o.getSAMString)
    outBam should be('exists)
    outBamIndex should be('exists)
    filteredOutBam should be('exists)
    filteredOutBamIndex should be('exists)
  }

  @Test def testWritePairBamDefault() = {
    val mockFilterOutFunc = (r: SAMRecord) => Set("r03", "r04", "r05").contains(r.getReadName)
    val outBam = makeTempBam()
    val outBamIndex = makeTempBamIndex(outBam)
    outBam.deleteOnExit()
    outBamIndex.deleteOnExit()

    val stdout = new java.io.ByteArrayOutputStream
    Console.withOut(stdout) {
      writeFilteredBam(mockFilterOutFunc, pBamFile1, outBam)
    }
    stdout.toString should ===(
      "input_bam\toutput_bam\tcount_included\tcount_excluded\n%s\t%s\t%d\t%d\n"
        .format(pBamFile1.getName, outBam.getName, 8, 6)
    )
    val exp = makeSamReader(pBamFile3).asScala
    val obs = makeSamReader(outBam).asScala
    for ((e, o) <- exp.zip(obs))
      e.getSAMString should ===(o.getSAMString)
    outBam should be('exists)
    outBamIndex should be('exists)
  }
}