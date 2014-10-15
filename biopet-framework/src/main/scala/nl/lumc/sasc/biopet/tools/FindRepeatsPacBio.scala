/*
 * Copyright 2014 pjvan_thof.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.lumc.sasc.biopet.tools

import htsjdk.samtools.SAMFileReader
import htsjdk.samtools.SAMRecord
import java.io.File
import nl.lumc.sasc.biopet.core.ToolCommand
import scala.io.Source
import scala.collection.JavaConversions._

object FindRepeatsPacBio extends ToolCommand {
  case class Args (inputBam:File = null, inputBed:File = null) extends AbstractArgs

  class OptParser extends AbstractOptParser {
    opt[File]('I', "inputBam") required() maxOccurs(1) valueName("<file>") action { (x, c) =>
      c.copy(inputBam = x) }
    opt[File]('b', "inputBed") required() maxOccurs(1) valueName("<file>") action { (x, c) =>
      c.copy(inputBed = x) } text("output file, default to stdout")
  }
  
  /**
   * @param args the command line arguments
   */
  def main(args: Array[String]): Unit = {
    val argsParser = new OptParser
    val commandArgs: Args = argsParser.parse(args, Args()) getOrElse sys.exit(1)
    
    val bamReader = new SAMFileReader(commandArgs.inputBam)
    bamReader.setValidationStringency(SAMFileReader.ValidationStringency.SILENT)
    val bamHeader = bamReader.getFileHeader
    
    for (bedLine <- Source.fromFile(commandArgs.inputBed).getLines;
      val values = bedLine.split("\t"); if values.size >= 3) {
      val interval = new SAMFileReader.QueryInterval(bamHeader.getSequenceIndex(values(0)), values(1).toInt, values(2).toInt)
      val bamIter = bamReader.query(Array(interval), false)
      val results = for (samRecord <-bamIter) yield procesSamrecord(samRecord, interval)
      val chr = values(0)
      val startPos = values(1)
      val oriRepeatLength = values(2).toInt - values(1).toInt + 1
      var calcRepeatLength: List[Int] = Nil
      var minLength = -1
      var maxLength = -1
      var inserts: List[String] = Nil
      var deletions: List[String] = Nil
      var notSpan = 0
      
      for (result <- results) {
        if (result.isEmpty) notSpan += 1
        else {
          inserts ::= result.get.ins.map(_.insert).mkString(",")
          deletions ::= result.get.dels.map(_.length).mkString(",")
          val length = oriRepeatLength - result.get.beginDel - result.get.endDel - 
                ((0 /: result.get.dels.map(_.length)) (_ + _)) + ((0 /: result.get.ins.map(_.insert.size)) (_ + _))
          calcRepeatLength ::= length
          if (length > maxLength) maxLength = length
          if (length < minLength || minLength == -1) minLength = length
        }
      }
      println(List(chr, startPos, oriRepeatLength, calcRepeatLength.mkString(","), minLength, 
                      maxLength, inserts.mkString("/"), deletions.mkString("/"), notSpan).mkString("\t"))
      bamIter.close
    }
  }
  
  case class Del(pos:Int, length:Int)
  case class Ins(pos:Int, insert:String)
  
  class Result() {
    var beginDel = 0
    var endDel = 0
    var dels: List[Del] = Nil
    var ins: List[Ins] = Nil
    var samRecord: SAMRecord = _
    
    override def toString = {
      "id: " + samRecord.getReadName + "  beginDel: " + beginDel + "  endDel: " + endDel + "  dels: "  + dels + "  ins: "  + ins
    }
  }
  
  def procesSamrecord(samRecord:SAMRecord, interval:SAMFileReader.QueryInterval): Option[Result] = {
    val readStartPos = List.range(0, samRecord.getReadBases.length)
          .find(x => samRecord.getReferencePositionAtReadPosition(x) >= interval.start)
    var readPos = if (readStartPos.isEmpty) return None else readStartPos.get
    if (samRecord.getAlignmentEnd < interval.end) return None
    if (samRecord.getAlignmentStart > interval.start) return None
    var refPos = samRecord.getReferencePositionAtReadPosition(readPos)
    
    val result = new Result
    result.samRecord = samRecord
    result.beginDel = interval.start - refPos
    while (refPos < interval.end) {
      val oldRefPos = refPos
      val oldReadPos = readPos
      do {
        readPos += 1
        refPos = samRecord.getReferencePositionAtReadPosition(readPos)
      } while(refPos < oldReadPos)
      val readDiff = readPos - oldReadPos
      val refDiff = refPos - oldRefPos
      if (refPos > interval.end) {
        result.endDel = interval.end - oldRefPos
      } else if (readDiff > refDiff) { //Insertion
        val insert = for (t <- oldReadPos+1 until readPos) yield samRecord.getReadBases()(t-1).toChar
        result.ins ::= Ins(oldRefPos, insert.mkString)
      } else if (readDiff < refDiff) { // Deletion
        result.dels ::= Del(oldRefPos, refDiff - readDiff)
      }
    }
    
    return Some(result)
  }
}
