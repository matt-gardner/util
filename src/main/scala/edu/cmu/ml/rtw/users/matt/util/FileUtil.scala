package edu.cmu.ml.rtw.users.matt.util

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.matching.Regex

/**
 * This class serves two main purposes:
 *  - It abstracts away a lot of file manipulation code so that I can use it in a number of
 *    different places (like reading in a list of Integers from a file, or whatever).
 *  - It serves as an overridable interface between my code and the file system, allowing for the
 *    use of a fake file system during testing.  So when I'm trying to make my code testable, I
 *    tend to use this class instead of using scala.io or java.io directly.
 *
 * It might make sense to split these two purposes out into separate classes, but that's not a big
 * deal to me right now, so they will stay as they are.  There's also some overlap - if this were
 * two classes, the one that does file manipulation would need to call the file system interface.
 */
class FileUtil {
  // These logEvery methods fit here, for now, because I only ever use them when I'm parsing
  // through a really long file and want to see progress updates as I go.
  def logEvery(logFrequency: Int, current: Int) {
    // We don't want to just call logEvery with current.toString, because that would create a
    // string at every call, which is a total waste.  So we deal with a tiny amount of code
    // duplication for efficiency's sake.
    if (current % logFrequency == 0) println(current)
  }

  def logEvery(logFrequency: Int, current: Int, toLog: String) {
    if (current % logFrequency == 0) println(toLog)
  }

  /**
   * Attempts to create the directory dirName, and exits if the directory already exists.
   */
  def mkdirOrDie(dirName: String) {
    if (new File(dirName).exists()) {
      throw new RuntimeException(s"Directory ${dirName} already exists!")
    }
    mkdirs(dirName)
  }

  /**
   * Calls new File(dirName).mkdirs().
   */
  def mkdirs(dirName: String) {
    new File(addDirectorySeparatorIfNecessary(dirName)).mkdirs()
  }

  def listDirectoryContents(filename: String): Seq[String] = {
    val file = new File(filename)
    if (!file.exists()) throw new RuntimeException(s"$filename does not exist")
    if (!file.isDirectory()) throw new RuntimeException(s"$filename is not a directory")
    file.listFiles() match {
      case empty if (empty == null) => Seq()
      case contents => contents.map(_.getName())
    }
  }

  def recursiveListFiles(f: File, r: Regex): Seq[File] = {
    val these = f.listFiles
    if (these == null) {
      Nil
    } else {
      val good = these.filter(f => r.findFirstIn(f.getName).isDefined)
      good ++ these.filter(_.isDirectory).flatMap(recursiveListFiles(_, r))
    }
  }

  def touchFile(filename: String) {
    val file = new File(filename)
    if (file.exists()) {
      file.setLastModified(System.currentTimeMillis)
    } else {
      file.createNewFile()
    }
  }

  def deleteFile(filename: String) {
    new File(filename).delete()
  }

  def getFileWriter(filename: String, append: Boolean = false) = new FileWriter(filename, append)

  def writeLinesToFile(filename: String, lines: Iterable[String], append: Boolean = false) {
    val writer = getFileWriter(filename, append)
    for (line <- lines) {
      writer.write(line)
      writer.write("\n")
    }
    writer.close()
  }

  def writeContentsToFile(filename: String, contents: String, append: Boolean = false) {
    val writer = getFileWriter(filename, append)
    writer.write(contents)
    writer.close()
  }

  def addDirectorySeparatorIfNecessary(dirName: String) = {
    if (dirName.endsWith(File.separator)) {
      dirName
    } else {
      dirName + File.separator
    }
  }

  def getLineIterator(filename: String): Iterator[String] = Source.fromFile(filename).getLines
  def getLineIterator(file: File) = Source.fromFile(file).getLines
  def getLineIterator(stream: InputStream) = Source.fromInputStream(stream).getLines

  def getLineIterator[T](filename: String, f: String => T): Iterator[T] = getLineIterator(filename).map(f)
  def getLineIterator[T](file: File, f: String => T): Iterator[T] = getLineIterator(file).map(f)
  def getLineIterator[T](stream: InputStream, f: String => T): Iterator[T] = getLineIterator(stream).map(f)

  def readLinesFromFile(filename: String) = getLineIterator(filename).toSeq
  def readLinesFromFile(file: File) = getLineIterator(file).toSeq
  def readLinesFromBZ2File(filename: String) = getLineIterator(
        new BZip2CompressorInputStream(new FileInputStream(filename))).toSeq

  def mapLinesFromFile[T](filename: String, function: String => T): Seq[T] = {
    getLineIterator(filename).map(function).toSeq
  }

  def flatMapLinesFromFile[T](filename: String, function: String => Seq[T]): Seq[T] = {
    getLineIterator(filename).flatMap(function).toSeq
  }

  val _chunkSize = 128 * 1024
  def parMapLinesFromFile[T](filename: String, function: String => T, chunkSize: Int = _chunkSize): Seq[T] = {
    val iterator = getLineIterator(filename).grouped(chunkSize)
    iterator.flatMap(lines => {
      lines.par.map(function).seq
    }).toSeq
  }

  def parFlatMapLinesFromFile[T](filename: String, function: String => Seq[T], chunkSize: Int = _chunkSize): Seq[T] = {
    val iterator = getLineIterator(filename).grouped(chunkSize)
    iterator.flatMap(lines => {
      lines.par.flatMap(function).seq
    }).toSeq
  }

  def readStringPairsFromFile(filename: String): Seq[(String, String)] = {
    val f = (line: String) => {
      val fields = line.split("\t")
      if (fields.length != 2) {
        println(s"Offending line: $line")
        throw new RuntimeException("readStringPairsFromReader called on file that didn't have two columns")
      }
      (fields(0), fields(1))
    }
    mapLinesFromFile(filename, f)
  }

  def readMapFromTsvFile(filename: String, skipErrors: Boolean = false): Map[String, String] = {
    val f: String => Seq[(String, String)] = (line: String) => {
      val fields = line.split("\t")
      if (fields.length != 2) {
        if (skipErrors) {
          Seq[(String, String)]()
        } else {
          println(s"Offending line: $line")
          throw new RuntimeException("readMapFromTsvFile called on file that didn't have two columns")
        }
      } else {
        Seq((fields(0), fields(1)))
      }
    }
    flatMapLinesFromFile(filename, f).toMap
  }

  /**
   * Reads a tab-separated file and puts the contents into a map.
   *
   * We give a few options:
   * - You can set the index for the key to the map.  If the key is not zero, we only add the first
   *   column as a value to this map (and so setting overwrite to true in this case doesn't make a
   *   whole lot of sense - just use readMapFromTsv instead).
   * - If overwrite is true, we don't bother checking to see if the key is already in the map.
   *   This will speed up the processing if you know that your file only has one line per unique
   *   key.
   * - You can provide a LineFilter object that wlil be called with each line to determine if it
   *   should be skipped.
   */
  def readMapListFromTsvFile(
      filename: String,
      keyIndex: Int = 0,
      overwrite: Boolean = false,
      filter: LineFilter = null): Map[String, Seq[String]] = {
    val map = new mutable.HashMap[String, mutable.ArrayBuffer[String]]
    for (line <- getLineIterator(filename)) {
      val fields = line.split("\t")
      if (filter == null || !filter.filter(fields)) {
        val key = fields(keyIndex)
        val list = if (overwrite) {
          val tmp = new mutable.ArrayBuffer[String]
          map(key) = tmp
          tmp
        } else {
          if (map.contains(key)) map(key) else {
            val tmp = new mutable.ArrayBuffer[String]
            map(key) = tmp
            tmp
          }
        }
        if (keyIndex == 0) {
          for (field <- fields.drop(1)) {
            list.append(field)
          }
        } else {
          list.append(fields(0))
        }
      }
    }
    map.toMap
  }

  def readInvertedMapListFromTsvFile(filename: String, logFrequency: Int = -1): Map[String, Seq[String]] = {
    val map = new mutable.HashMap[String, mutable.ArrayBuffer[String]]
    var line_number = 0
    for (line <- getLineIterator(filename)) {
      line_number += 1
      if (logFrequency != -1) logEvery(logFrequency, line_number)
      val fields = line.split("\t")
      val key = fields(0)
      for (value <- fields.drop(1)) {
        map.getOrElseUpdate(value, new mutable.ArrayBuffer[String]).append(key)
      }
    }
    map.toMap
  }

  /**
   * The file is assumed to be a series of lines, one string per line.  If the provided dictionary
   * is null, we parse the string to an integer before adding it to a set.  Otherwise, we look up
   * the string in the dictionary to convert the string to an integer.
   */
  def readIntegerSetFromFile(filename: String, dict: Dictionary = null): Set[Int] = {
    readIntegerListFromFile(filename, dict).toSet
  }

  /**
   * The file is assumed to be a series of lines, one string per line.  If the provided dictionary
   * is null, we parse the string to an integer before adding it to a list.  Otherwise, we look up
   * the string in the dictionary to convert the string to an integer.
   */
  def readIntegerListFromFile(filename: String, dict: Dictionary = null): Seq[Int] = {
    val f: String => Int = (line: String) => {
      if (dict == null) line.toInt else dict.getIndex(line)
    }
    mapLinesFromFile(filename, f)
  }

  def readDoubleListFromFile(filename: String): Seq[Double] = {
    val f: String => Double = (line: String) => line.toDouble
    mapLinesFromFile(filename, f)
  }

  def intTripleFromLine(line: String): (Int, Int, Int) = {
    var int1, int2, int3 = 0
    var i = 0
    while (line.charAt(i) != '\t') {
      int1 *= 10
      int1 += line.charAt(i) - 48
      i += 1
    }
    i += 1
    while (line.charAt(i) != '\t') {
      int2 *= 10
      int2 += line.charAt(i) - 48
      i += 1
    }
    i += 1
    while (i < line.length) {
      int3 *= 10
      int3 += line.charAt(i) - 48
      i += 1
    }
    (int1, int2, int3)
  }

  def fileExists(path: String): Boolean = {
    new File(path).exists()
  }

  /**
   * Copies the lines in reader to writer.  Does not close writer.
   */
  def copyLines(reader: BufferedReader, writer: FileWriter) {
    var line = reader.readLine
    while (line != null) {
      writer.write(line + "\n")
      line = reader.readLine
    }
  }

  def blockOnFileDeletion(filename: String) {
    if (!new File(filename).exists()) return
    println(s"Waiting for file $filename to be deleted")
    val watchService = FileSystems.getDefault().newWatchService()
    val parent = Paths.get(filename).getParent()
    val watchKey = parent.register(watchService, StandardWatchEventKinds.ENTRY_DELETE)
    val key = watchService.take()
    for (event <- key.pollEvents.asScala) {
      if (filename.endsWith(event.context().toString())) return
    }
  }

  def copy(from: String, to: String) {
    Files.copy(new File(from).toPath(), new File(to).toPath())
  }
}

trait LineFilter {
  def filter(fields: Array[String]): Boolean
}
