package edu.cmu.ml.rtw.users.matt.util

import java.io.File
import java.io.IOException

import scala.collection.mutable
import scala.io.Source

class FakeFileUtil extends FileUtil {

  private val existingPaths = new mutable.HashSet[String]
  private val doubleList = new mutable.ArrayBuffer[Double]
  private val expectedFileContents = new mutable.HashMap[String, String]
  private val fileWriters = new mutable.HashMap[String, FakeFileWriter]
  private val readerFileContents = new mutable.HashMap[String, String]
  private var _onlyAllowExpectedFiles = false
  private var _throwIOExceptionOnWrite = false

  private def allFiles(): Set[String] = {
    readerFileContents.keys.toSet ++ existingPaths
  }

  override def mkdirOrDie(dirName: String) { }
  override def mkdirs(dirName: String) { }

  override def fileExists(path: String) = allFiles.contains(path)

  def getFileContents(filename: String) = {
    readerFileContents.get(filename) match {
      case None => throw new RuntimeException(s"Unexpected file read: $filename")
      case Some(contents) => contents
    }
  }

  override def getLineIterator(filename: String) =
    Source.fromString(getFileContents(filename)).getLines

  override def getLineIterator(file: File) =
    Source.fromString(getFileContents(file.getName)).getLines

  override def readDoubleListFromFile(filename: String) = doubleList

  override def listDirectoryContents(dirname: String) = {
    val fixed = if (dirname.endsWith("/")) dirname.substring(0, dirname.length() - 1) else dirname
    allFiles.filter(file => {
      new File(file).getParent().equals(dirname)
    }).toSeq
  }

  override def getFileWriter(filename: String, append: Boolean = false) = {
    if (_throwIOExceptionOnWrite) throw new IOException("Writing not allowed")
    if (_onlyAllowExpectedFiles && !expectedFileContents.contains(filename)) {
      throw new RuntimeException(s"Unexpected file written: $filename")
    }
    if (!append || !fileWriters.contains(filename)) {
      fileWriters.put(filename, new FakeFileWriter(filename))
    }
    fileWriters(filename)
  }

  override def touchFile(filename: String) {
    if (_throwIOExceptionOnWrite) throw new IOException("Writing not allowed")
    if (_onlyAllowExpectedFiles && !expectedFileContents.contains(filename)) {
      throw new RuntimeException(s"Unexpected file written: $filename")
    }
    existingPaths.add(filename)
    val writer = new FakeFileWriter(filename)
    writer.close()
    fileWriters.put(filename, writer)
  }

  override def deleteFile(filename: String) {
    // TODO(matt): This isn't very complete, but I don't have a test that needs this yet.  This
    // should probably do something more interesting, but I'll write it when I need it for a test.
    if (_throwIOExceptionOnWrite) throw new IOException("Writing not allowed")
    existingPaths.remove(filename)
  }

  override def blockOnFileDeletion(filename: String) { }

  def addFileToBeRead(filename: String, contents: String) {
    readerFileContents.put(filename, contents)
  }

  def addExpectedFileWritten(filename: String, expectedContents: String) {
    expectedFileContents.put(filename, expectedContents)
  }

  def expectFilesWritten() {
    for (entry <- expectedFileContents) {
      fileWriters.get(entry._1) match {
        case None => throw new RuntimeException(s"Expected file not written: ${entry._1}")
        case Some(writer) => {
          writer.expectWritten(entry._2)
        }
      }
    }
  }

  def addExistingFile(path: String) {
    existingPaths.add(path)
  }

  def setDoubleList(_doubleList: Seq[Double]) {
    doubleList.clear()
    doubleList.appendAll(_doubleList)
  }

  /**
   * If getFileWriter gets called with a path that was not given with addExpectedFileWritten, this
   * will check fail.
   */
  def onlyAllowExpectedFiles() {
    _onlyAllowExpectedFiles = true
  }

  /**
   * Do not check fail if getFileWriter is called on an unexpected path.
   */
  def allowUnexpectedFiles() {
    _onlyAllowExpectedFiles = false
  }

  def throwIOExceptionOnWrite() {
    _throwIOExceptionOnWrite = true
  }

  def unsetThrowIOExceptionOnWrite() {
    _throwIOExceptionOnWrite = false
  }
}
