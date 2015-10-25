package edu.cmu.ml.rtw.users.matt.util

import junit.framework.ComparisonFailure

import java.io.File

import org.scalatest._


import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

class FakeFileUtilSpec extends FlatSpecLike with Matchers {


  "fileExists" should "return true for files added in the right way" in {
    val fileUtil = new FakeFileUtil()
    val file = "/test file"
    fileUtil.fileExists(file) should be(false)
    fileUtil.addExistingFile(file)
    fileUtil.fileExists(file) should be(true)
    val file2 = "/test file2"
    fileUtil.fileExists(file2) should be(false)
    fileUtil.addFileToBeRead(file2, "contents")
    fileUtil.fileExists(file2) should be(true)
  }

  "getFileContents" should "throw an expection when reading an unexpected file" in {
    TestUtil.expectError(classOf[RuntimeException], "Unexpected file read", new Function() {
      override def call() {
        new FakeFileUtil().getFileContents("/test file")
      }
    })
  }

  it should "return a the file contents when it has been given" in {
    val fileUtil = new FakeFileUtil()
    val file = "/test file"
    val contents = "contents"
    fileUtil.addFileToBeRead(file, contents)
    fileUtil.getFileContents(file) should be(contents)
  }

  "getLineIterator" should "return a list of the right lines" in {
    val fileUtil = new FakeFileUtil()
    val file = "/test file"
    val lines = Seq("line 1", "line 2", "line 3")
    fileUtil.addFileToBeRead(file, lines.mkString("\n"))
    fileUtil.getLineIterator(file).toSeq should be(lines)
  }

  it should "work with a File argument also" in {
    val fileUtil = new FakeFileUtil()
    val file = "/test_file"
    val lines = Seq("line 1", "line 2", "line 3")
    fileUtil.addFileToBeRead(file, lines.mkString("\n"))
    fileUtil.getLineIterator(new File(file)).toSeq should be(lines)
  }

  "listDirectoryContents" should "give the right directory contents" in {
    val fileUtil = new FakeFileUtil()
    val dir = "/dir/"
    val files = (1 to 5).map(i => dir + "file_" + i).toSeq
    val expectedFiles = (1 to 5).map(i => "file_" + i).toSeq
    files.foreach(file => fileUtil.addExistingFile(file))
    val contents = fileUtil.listDirectoryContents(dir)
    contents.size should be(files.size)
    // For some reason contents should contain only(files) and allOf(files) aren't working...
    expectedFiles.foreach(file => contents should contain(file))
  }

  "expectFilesWritten" should "do nothing if the right files have all been written" in {
    val fileUtil = new FakeFileUtil()
    val file1 = "/test_file1"
    val contents1 = "contents1"
    val file2 = "/test_file2"
    val contents2 = "contents2"
    val file3 = "/test_file3"
    val contents3 = "contents3"
    fileUtil.addExpectedFileWritten(file1, contents1)
    fileUtil.addExpectedFileWritten(file2, contents2)
    fileUtil.addExpectedFileWritten(file3, contents3)
    val writer1 = fileUtil.getFileWriter(file1)
    writer1.write(contents1)
    writer1.close()
    val writer2 = fileUtil.getFileWriter(file2)
    writer2.write(contents2)
    writer2.close()
    val writer3 = fileUtil.getFileWriter(file3)
    writer3.write(contents3)
    writer3.close()
    fileUtil.expectFilesWritten()
  }

  it should "throw an exception if a file is missing" in {
    val fileUtil = new FakeFileUtil()
    val file1 = "/test_file1"
    val contents1 = "contents1"
    val file2 = "/test_file2"
    val contents2 = "contents2"
    val file3 = "/test_file3"
    val contents3 = "contents3"
    fileUtil.addExpectedFileWritten(file1, contents1)
    fileUtil.addExpectedFileWritten(file2, contents2)
    fileUtil.addExpectedFileWritten(file3, contents3)
    val writer1 = fileUtil.getFileWriter(file1)
    writer1.write(contents1)
    writer1.close()
    val writer2 = fileUtil.getFileWriter(file2)
    writer2.write(contents2)
    writer2.close()
    TestUtil.expectError(classOf[RuntimeException], "Expected file not written", new Function() {
      override def call() {
        fileUtil.expectFilesWritten()
      }
    })
  }

  it should "also throw an exception if a file has the wrong contents" in {
    val fileUtil = new FakeFileUtil()
    val file1 = "/test_file1"
    val contents1 = "contents1"
    val file2 = "/test_file2"
    val contents2 = "contents2"
    val file3 = "/test_file3"
    val contents3 = "contents3"
    fileUtil.addExpectedFileWritten(file1, contents1)
    fileUtil.addExpectedFileWritten(file2, contents2)
    fileUtil.addExpectedFileWritten(file3, contents3)
    val writer1 = fileUtil.getFileWriter(file1)
    writer1.write(contents1)
    writer1.close()
    val writer2 = fileUtil.getFileWriter(file2)
    writer2.write(contents2)
    writer2.close()
    val writer3 = fileUtil.getFileWriter(file3)
    writer3.write(contents2)
    writer3.close()
    TestUtil.expectError(classOf[ComparisonFailure], new Function() {
      override def call() {
        fileUtil.expectFilesWritten()
      }
    })
  }
}
