package nl.lumc.sasc.biopet.pipelines.gatk

import nl.lumc.sasc.biopet.wrappers._
import nl.lumc.sasc.biopet.core._
import nl.lumc.sasc.biopet.pipelines.flexiprep._
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.extensions.picard._
import org.broadinstitute.sting.queue.function._
import scala.util.parsing.json._
import org.broadinstitute.sting.utils.variant._

class Gatk(private var globalConfig: Config) extends QScript {
  @Argument(doc="Config Json file",shortName="config") var configfiles: List[File] = Nil
  @Argument(doc="Only Sample",shortName="sample", required=false) var onlySample: String = _
  def this() = this(new Config())
  var config: Config = _
  var scatterCount: Int = _
  var referenceFile: File = _
  var dbsnp: File = _
  var gvcfFiles: List[File] = Nil
  
  trait gatkArguments extends CommandLineGATK {
    this.reference_sequence = referenceFile
    this.memoryLimit = 2
    this.jobResourceRequests :+= "h_vmem=4G"
  }
  
  def init() {
    for (file <- configfiles) globalConfig.loadConfigFile(file)
    config = globalConfig.getAsConfig("gatk")
    referenceFile = config.getAsString("referenceFile")
    dbsnp = config.getAsString("dbsnp")
    gvcfFiles = config.getAsListOfStrings("gvcfFiles", Nil)
    scatterCount = config.getAsInt("scatterCount", 1)
  }
  
  def script() {
    this.init()
    if (globalConfig.contains("Samples")) for ((key,value) <- globalConfig.getAsMap("Samples")) {
      if (onlySample == null || onlySample == key) {
        var sample:Config = globalConfig.getAsConfig("Samples").getAsConfig(key)
        if (sample.getAsString("ID") == key) { 
          var files:Map[String,List[File]] = sampleJobs(sample)
          if (files.contains("gvcf")) for (file <- files("gvcf")) gvcfFiles :+= file
        } else logger.warn("Key is not the same as ID on value for sample")
      } else logger.info("Skipping Sample: " + key)
    } else logger.warn("No Samples found in config")
    
    if (onlySample == null) {
      //SampleWide jobs
      val genotypeGVCFs = new GenotypeGVCFs() with gatkArguments
      genotypeGVCFs.variant = gvcfFiles
      genotypeGVCFs.scatterCount = scatterCount
      genotypeGVCFs.out = new File("final.vcf")
      if (genotypeGVCFs.variant.size > 0) add(genotypeGVCFs) else logger.warn("No gVCFs to genotype")
    }
  }
  
  // Called for each sample
  def sampleJobs(sampleConfig:Config) : Map[String,List[File]] = {
    var outputFiles:Map[String,List[File]] = Map()
    outputFiles += ("FinalBams" -> List())
    var runs:List[Map[String,File]] = Nil
    if (sampleConfig.contains("ID")) {
      var sampleID: String = sampleConfig.getAsString("ID")
      this.logger.info("Starting generate jobs for sample: " + sampleID)
      for (key <- sampleConfig.getAsMap("Runs").keySet) {
        var runConfig = sampleConfig.getAsConfig("Runs").getAsConfig(key)
        var run: Map[String,File] = runJobs(runConfig, sampleConfig)
        var FinalBams:List[File] = outputFiles("FinalBams") 
        if (run.contains("FinalBam")) FinalBams :+= run("FinalBam")
        else logger.warn("No Final bam for Sample: " + sampleID + "  Run: " + runConfig)
        outputFiles += ("FinalBams" -> FinalBams)
        runs +:= run
      }
      
      // Variant calling
      val haplotypeCaller = new HaplotypeCaller with gatkArguments
      if (scatterCount > 1) haplotypeCaller.scatterCount = scatterCount * 15
      haplotypeCaller.input_file = outputFiles("FinalBams")
      haplotypeCaller.out = new File(sampleID + "/" + sampleID + ".gvcf.vcf")
      if (dbsnp != null) haplotypeCaller.dbsnp = dbsnp
      haplotypeCaller.nct = 3
      haplotypeCaller.memoryLimit = haplotypeCaller.nct * 2
      
      // GVCF options
      haplotypeCaller.emitRefConfidence = org.broadinstitute.sting.gatk.walkers.haplotypecaller.HaplotypeCaller.ReferenceConfidenceMode.GVCF
      haplotypeCaller.variant_index_type = GATKVCFIndexType.LINEAR
      haplotypeCaller.variant_index_parameter = 128000
      
      if (haplotypeCaller.input_file.size > 0) {
        add(haplotypeCaller)
        outputFiles += ("gvcf" -> List(haplotypeCaller.out))
      }
    } else {
      this.logger.warn("Sample in config missing ID, skipping sample")
    }
    return outputFiles
  }
  
  // Called for each run from a sample
  def runJobs(runConfig:Config,sampleConfig:Config) : Map[String,File] = {
    var outputFiles:Map[String,File] = Map()
    var paired: Boolean = false
    var runID: String = ""
    var fastq_R1: String = ""
    var fastq_R2: String = ""
    var sampleID: String = sampleConfig.get("ID").toString
    if (runConfig.contains("R1")) {
      fastq_R1 = runConfig.get("R1").toString
      if (runConfig.contains("R2")) {
        fastq_R2 = runConfig.get("R2").toString
        paired = true
      }
      if (runConfig.contains("ID")) runID = runConfig.get("ID").toString
      else throw new IllegalStateException("Missing ID on run for sample: " + sampleID)
      var runDir: String = sampleID + "/run_" + runID + "/"
      
      val flexiprep = new Flexiprep(config)
      flexiprep.input_R1 = fastq_R1
      if (paired) flexiprep.input_R2 = fastq_R2
      flexiprep.outputDir = runDir + "flexiprep/"
      flexiprep.script
      addAll(flexiprep.functions)
      
      val bwaCommand = new Bwa(config)
      bwaCommand.R1 = flexiprep.outputFiles("output_R1")
      if (paired) bwaCommand.R2 = flexiprep.outputFiles("output_R2")
      bwaCommand.referenceFile = referenceFile
      bwaCommand.nCoresRequest = 8
      bwaCommand.jobResourceRequests :+= "h_vmem=6G"
      bwaCommand.RG = "@RG\\t" +
    		  "ID:" + sampleID + "_" + runID + "\\t" +
    		  "LB:" + sampleID + "_" + runID + "\\t" +
    		  "PL:illumina\\t" +
    		  "CN:SASC\\t" +
    		  "SM:" + sampleID + "\\t" +
    		  "PU:na"
      bwaCommand.output = new File(runDir + sampleID + "-run_" + runID + ".sam")
      add(bwaCommand)
      
      val sortSam = new SortSam
      sortSam.input :+= bwaCommand.output
      sortSam.createIndex = true
      sortSam.output = swapExt(runDir,bwaCommand.output,".sam",".bam")
      sortSam.memoryLimit = 2
      sortSam.nCoresRequest = 2
      sortSam.jobResourceRequests :+= "h_vmem=4G"
      add(sortSam)
      
      val markDuplicates = new MarkDuplicates
      markDuplicates.input :+= sortSam.output
      markDuplicates.output = swapExt(runDir,sortSam.output,".bam",".dedup.bam")
      markDuplicates.REMOVE_DUPLICATES = false
      markDuplicates.metrics = swapExt(runDir,markDuplicates.output,".bam",".metrics")
      markDuplicates.outputIndex = swapExt(runDir,markDuplicates.output,".bam",".bai")
      markDuplicates.memoryLimit = 2
      markDuplicates.jobResourceRequests :+= "h_vmem=4G"
      add(markDuplicates)
      
      val realignerTargetCreator = new RealignerTargetCreator with gatkArguments
      realignerTargetCreator.I :+= markDuplicates.output
      realignerTargetCreator.o = swapExt(runDir,markDuplicates.output,".bam",".realign.intervals")
      //realignerTargetCreator.nt = 1
      realignerTargetCreator.jobResourceRequests :+= "h_vmem=5G"
      if (scatterCount > 1) realignerTargetCreator.scatterCount = scatterCount
      add(realignerTargetCreator)

      val indelRealigner = new IndelRealigner with gatkArguments
      indelRealigner.I :+= markDuplicates.output
      indelRealigner.targetIntervals = realignerTargetCreator.o
      indelRealigner.o = swapExt(runDir,markDuplicates.output,".bam",".realign.bam")
      if (scatterCount > 1) indelRealigner.scatterCount = scatterCount
      add(indelRealigner)

      val baseRecalibrator = new BaseRecalibrator with gatkArguments
      baseRecalibrator.I :+= indelRealigner.o
      baseRecalibrator.o = swapExt(runDir,indelRealigner.o,".bam",".baserecal")
      baseRecalibrator.knownSites :+= dbsnp
      if (scatterCount > 1) baseRecalibrator.scatterCount = scatterCount
      baseRecalibrator.nct = 2
      add(baseRecalibrator)

      val printReads = new PrintReads with gatkArguments
      printReads.I :+= indelRealigner.o
      printReads.o = swapExt(runDir,indelRealigner.o,".bam",".baserecal.bam")
      printReads.BQSR = baseRecalibrator.o
      if (scatterCount > 1) printReads.scatterCount = scatterCount
      add(printReads)
      
      outputFiles += ("FinalBam" -> printReads.o)
    } else this.logger.error("Sample: " + sampleID + ": No R1 found for runs: " + runConfig)    
    return outputFiles
  }  
}