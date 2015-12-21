package edu.cmu.ml.rtw.users.matt.util

import org.scalatest._

import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function
import java.io.File
import java.io.BufferedReader
import java.io.StringReader

import scala.collection.JavaConverters._
import scala.io.Source

// This class actually interacts with the filesystem, so we are going to make use of
// src/test/resources/ for this, reading from and writing to there as we test things.
class FileUtilSpec extends FlatSpecLike with Matchers {

  val fileUtil = new FileUtil()

  "mkdirOrDie" should "make a directory" in {
    val dir = "src/test/resources/tmpdir/"
    fileUtil.mkdirOrDie(dir)
    new File(dir).exists() should be(true)
    new File(dir).delete()
  }

  it should "still work even with no trailing /" in {
    val dir = "src/test/resources/tmpdir"
    fileUtil.mkdirOrDie(dir)
    new File(dir + "/").exists() should be(true)
    new File(dir + "/").delete()
  }

  it should "throw an exception if the directory already exists" in {
    TestUtil.expectError(classOf[RuntimeException], "already exists", new Function() {
      override def call() {
        fileUtil.mkdirOrDie("src/test/resources/")
      }
    })
  }

  "listDirectoryContents" should "correctly list the contents of a directory" in {
    val contents = fileUtil.listDirectoryContents("src/test/resources/directory_test/")
    contents.size should be(3)
    contents should contain allOf("test1", "test2", "test3")
  }

  it should "return an empty list if there is no directory contents" in {
    val dir = "src/test/resources/tmpdir/"
    fileUtil.mkdirOrDie(dir)
    fileUtil.listDirectoryContents(dir).size should be(0)
    new File(dir).delete()
  }

  it should "throw an error when the given file is not a directory" in {
    TestUtil.expectError(classOf[RuntimeException], "not a directory", new Function() {
      override def call() {
        fileUtil.listDirectoryContents("src/test/resources/empty_file.txt")
      }
    })
  }

  it should "throw an error when the given file does not exist" in {
    TestUtil.expectError(classOf[RuntimeException], "does not exist", new Function() {
      override def call() {
        fileUtil.listDirectoryContents("/bad file name")
      }
    })
  }

  "recursiveListFile" should "list all files when no regex is given" in {
    val dir = "src/test/resources/directory_test/"
    val regex = "".r
    val contents = fileUtil.recursiveListFiles(new File(dir), regex).map(_.getPath)
    contents.size should be(8)
    contents should contain allOf(
      "src/test/resources/directory_test/test1",
      "src/test/resources/directory_test/test1/README.md",
      "src/test/resources/directory_test/test2",
      "src/test/resources/directory_test/test2/recursive_test",
      "src/test/resources/directory_test/test2/recursive_test/file3.test",
      "src/test/resources/directory_test/test2/file2.test",
      "src/test/resources/directory_test/test3",
      "src/test/resources/directory_test/test3/file1.test"
    )
  }

  it should "correctly filter a directory structure" in {
    val dir = "src/test/resources/directory_test/"
    val regex = """.*\.test$""".r
    val contents = fileUtil.recursiveListFiles(new File(dir), regex).map(_.getPath)
    contents.size should be(3)
    contents should contain allOf(
      "src/test/resources/directory_test/test2/recursive_test/file3.test",
      "src/test/resources/directory_test/test2/file2.test",
      "src/test/resources/directory_test/test3/file1.test"
    )
  }

  "touchFile" should "create a new file" in {
    val file = "src/test/resources/tmpfile.txt"
    fileUtil.touchFile(file)
    new File(file).exists() should be(true)
    new File(file).delete()
  }

  it should "update timestamp on an existing file" in {
    val file = "src/test/resources/empty_file.txt"
    val modified = new File(file).lastModified
    fileUtil.touchFile(file)
    new File(file).lastModified should be > modified
  }

  "deleteFile" should "delete a file" in {
    val file = "src/test/resources/empty_file.txt"
    fileUtil.deleteFile(file)
    new File(file).exists() should be(false)
    new File(file).createNewFile()
  }

  "writeLinesToFile" should "write the lines to the file" in {
    val lines = Seq("line 1", "line 2", "line 3")
    val file = "src/test/resources/test_write_file.txt"
    fileUtil.writeLinesToFile(file, lines)
    val written = Source.fromFile(file).getLines.toSeq
    written should be(lines)
    new File(file).delete()
  }

  "addDirectorySeparatorIfNecessary" should "leave good paths alone" in {
    fileUtil.addDirectorySeparatorIfNecessary("src/test/resources/") should be("src/test/resources/")
  }

  it should "add a separator" in {
    fileUtil.addDirectorySeparatorIfNecessary("src/test/resources") should be("src/test/resources/")
  }

  // The writeLinesToFile test comes before this, so I am going to use that method in the read*
  // tests that follow, instead of relying on a file that you would have to open separately to see
  // what it contains.
  "readLinesFromFile" should "correctly read the lines from the file" in {
    val lines = Seq("line 1", "line 2", "line 3")
    val file = "src/test/resources/test_read_lines.txt"
    fileUtil.writeLinesToFile(file, lines)
    val read = fileUtil.readLinesFromFile(file)
    read should be(lines)
    new File(file).delete()
  }

  it should "still work when given a file object instead of a String filename" in {
    val lines = Seq("line 1", "line 2", "line 3")
    val file = "src/test/resources/test_read_lines.txt"
    fileUtil.writeLinesToFile(file, lines)
    val read = fileUtil.readLinesFromFile(new File(file))
    read should be(lines)
    new File(file).delete()
  }

  "readStringPairsFromFile" should "get string pairs from a two-column file" in {
    val pairs = Seq(("line 1 1", "line 1 2"), ("line 2 1", "line 2 2"), ("line 3 1", "line 3 2"))
    val lines = pairs.map(pair => pair._1 + "\t" + pair._2)
    val file = "src/test/resources/test_read_pairs.txt"
    fileUtil.writeLinesToFile(file, lines)
    val read = fileUtil.readStringPairsFromFile(file)
    read should be(pairs)
    new File(file).delete()
  }

  "readMapFromTsvFile" should "read a map correctly" in {
    val map = Map(("line 1 1" -> "line 1 2"), ("line 2 1" -> "line 2 2"), ("line 3 1" -> "line 3 2"))
    val lines = map.map(entry => entry._1 + "\t" + entry._2).toSeq
    val file = "src/test/resources/test_read_map.txt"
    fileUtil.writeLinesToFile(file, lines)
    val read = fileUtil.readMapFromTsvFile(file)
    read should be(map)
    new File(file).delete()
  }

  it should "throw an error when the file is formatted incorrectly" in {
    val lines = Seq("key 1\textra column\tvalue 1", "key 2\tvalue 2")
    val file = "src/test/resources/test_read_map.txt"
    fileUtil.writeLinesToFile(file, lines)
    TestUtil.expectError(classOf[RuntimeException], "didn't have two columns", new Function() {
      override def call() {
        fileUtil.readMapFromTsvFile(file)
      }
    })
    new File(file).delete()
  }

  it should "not throw an error when skipErrors is true" in {
    val lines = Seq("key 1\textra column\tvalue 1", "key 2\tvalue 2")
    val file = "src/test/resources/test_read_map.txt"
    fileUtil.writeLinesToFile(file, lines)
    val map = fileUtil.readMapFromTsvFile(file, skipErrors=true)
    map.size should be(1)
    map("key 2") should be("value 2")
    new File(file).delete()
  }

  "readMapListFromTsvFile" should "read a simple map list with keyIndex 0" in {
    val lines = Seq("key 1\tvalue 1\tvalue 2\tvalue 3", "key 2\tvalue 4\tvalue 5")
    val file = "src/test/resources/test_read_map_list.txt"
    fileUtil.writeLinesToFile(file, lines)
    val map = fileUtil.readMapListFromTsvFile(file, keyIndex=0, overwrite=false, filter=null)
    map.size should be(2)
    map("key 1") should be(Seq("value 1", "value 2", "value 3"))
    map("key 2") should be(Seq("value 4", "value 5"))
    new File(file).delete()
  }

  it should "work with a keyIndex other than 0" in {
    val lines = Seq("key 1\tvalue 1", "key 2\tvalue 2", "key 3\tvalue 1")
    val file = "src/test/resources/test_read_map_list.txt"
    fileUtil.writeLinesToFile(file, lines)
    val map = fileUtil.readMapListFromTsvFile(file, keyIndex=1, overwrite=false, filter=null)
    map.size should be(2)
    map("value 1") should be(Seq("key 1", "key 3"))
    map("value 2") should be(Seq("key 2"))
    new File(file).delete()
  }

  it should "work with overwrite" in {
    val lines = Seq("key 1\tvalue 1", "key 2\tvalue 2", "key 3\tvalue 1")
    val file = "src/test/resources/test_read_map_list.txt"
    fileUtil.writeLinesToFile(file, lines)
    val map = fileUtil.readMapListFromTsvFile(file, keyIndex=1, overwrite=true, filter=null)
    map.size should be(2)
    map("value 1") should be(Seq("key 3"))
    map("value 2") should be(Seq("key 2"))
    new File(file).delete()
  }

  it should "work with a line filter" in {
    val lines = Seq("key 1\tvalue 1", "key 2\tvalue 2", "key 3\tvalue 1")
    val file = "src/test/resources/test_read_map_list.txt"
    fileUtil.writeLinesToFile(file, lines)
    val map = fileUtil.readMapListFromTsvFile(file, keyIndex=1, filter=new LineFilter() {
      override def filter(fields: Array[String]) = fields(0) == "key 3"
    })
    map.size should be(2)
    map("value 1") should be(Seq("key 1"))
    map("value 2") should be(Seq("key 2"))
    new File(file).delete()
  }

  "readInvertedMapListFromTsvFile" should "read an inverted map list" in {
    val lines = Seq("key 1\tvalue 1\tvalue 2\tvalue 3", "key 2\tvalue 4\tvalue 1")
    val file = "src/test/resources/test_read_inverted_map_list.txt"
    fileUtil.writeLinesToFile(file, lines)
    val map = fileUtil.readInvertedMapListFromTsvFile(file)
    map.size should be(4)
    map("value 1") should be(Seq("key 1", "key 2"))
    map("value 2") should be(Seq("key 1"))
    map("value 3") should be(Seq("key 1"))
    map("value 4") should be(Seq("key 2"))
    new File(file).delete()
  }

  "readIntegerSetFromFile" should "get an integer set correctly without a dictionary" in {
    val lines = Seq("1", "10", "100", "1000", "10", "100")
    val file = "src/test/resources/test_read_integer_set.txt"
    fileUtil.writeLinesToFile(file, lines)
    val set = fileUtil.readIntegerSetFromFile(file)
    set.size should be(4)
    set should contain only(1, 10, 100, 1000)
    new File(file).delete()
  }

  it should "work correctly with a dictionary" in {
    val lines = Seq("val1", "val2", "val3", "val2", "val3")
    val file = "src/test/resources/test_read_integer_set.txt"
    fileUtil.writeLinesToFile(file, lines)
    val dict = new Dictionary()
    dict.getIndex("fake1")
    dict.getIndex("fake2")
    val set = fileUtil.readIntegerSetFromFile(file, dict)
    set.size should be(3)
    set should contain only(dict.getIndex("val1"), dict.getIndex("val2"), dict.getIndex("val3"))
    new File(file).delete()
  }

  "readIntegerListFromFile" should "get an integer list correctly without a dictionary" in {
    val lines = Seq("1", "10", "100", "1000", "10", "100")
    val file = "src/test/resources/test_read_integer_list.txt"
    fileUtil.writeLinesToFile(file, lines)
    val list = fileUtil.readIntegerListFromFile(file)
    list should be(Seq(1, 10, 100, 1000, 10, 100))
    new File(file).delete()
  }

  it should "work correctly with a dictionary" in {
    val lines = Seq("val1", "val2", "val3")
    val file = "src/test/resources/test_read_integer_list.txt"
    fileUtil.writeLinesToFile(file, lines)
    val dict = new Dictionary()
    dict.getIndex("fake1")
    dict.getIndex("val3")
    dict.getIndex("fake2")
    val list = fileUtil.readIntegerListFromFile(file, dict)
    list should be(Seq(dict.getIndex("val1"), dict.getIndex("val2"), dict.getIndex("val3")))
    new File(file).delete()
  }

  "readDoubleListFromFile" should "read a list of doubles from a file" in {
    val lines = Seq("1.0", "0.1", "0.01", "0.001")
    val file = "src/test/resources/test_read_double_list.txt"
    fileUtil.writeLinesToFile(file, lines)
    val list = fileUtil.readDoubleListFromFile(file)
    list should be(Seq(1.0, 0.1, 0.01, 0.001))
    new File(file).delete()
  }

  "parMapLinesFromFile" should "produce the same result as mapLinesFromFile" in {
    val lines = (1 to 1000).map(i => s"line $i")
    val file = "src/test/resources/test_par_map_lines.txt"
    fileUtil.writeLinesToFile(file, lines)
    val f: String => Int = (line: String) => line.split(" ")(1).toInt
    val mapLinesResult = fileUtil.mapLinesFromFile(file, f)
    val parMapResult = fileUtil.parMapLinesFromFile(file, f, 10)
    parMapResult should be(mapLinesResult)
  }

  "parFlatMapLinesFromFile" should "produce the same result as flatMapLinesFromFile" in {
    val lines = (1 to 1000).map(i => s"line $i")
    val file = "src/test/resources/test_par_map_lines.txt"
    fileUtil.writeLinesToFile(file, lines)
    val f: String => Seq[Int] = (line: String) => Seq(line.split(" ")(1).toInt, 10)
    val mapLinesResult = fileUtil.mapLinesFromFile(file, f)
    val parMapResult = fileUtil.parMapLinesFromFile(file, f, 23)
    parMapResult should be(mapLinesResult)
  }

  "fileExists" should "correctly tell if a file exists" in {
    fileUtil.fileExists("src/test/resources/") should be(true)
    fileUtil.fileExists("src/test/resources/empty_file.txt") should be(true)
    fileUtil.fileExists("src/test/resources/fake_file") should be(false)
    fileUtil.fileExists("/another/fake/file") should be(false)
  }

  "copyLines" should "copy the lines from the reader to the writer, adding newlines" in {
    val lines = Seq("line 1", "line 2", "line 3")
    val string = lines.mkString("\n")
    val reader = new BufferedReader(new StringReader(string))
    val writer = new FakeFileWriter()
    fileUtil.copyLines(reader, writer)
    writer.expectWrittenInOrder(lines.map(_ + "\n").asJava)
  }

  "copy" should "copy a file from one location to another" in {
    val lines = Seq("line 1", "line 2", "line 3")
    val file = "src/test/resources/test_copy.txt"
    val file2 = "src/test/resources/test_copy2.txt"
    fileUtil.writeLinesToFile(file, lines)
    fileUtil.copy(file, file2)
    val copied = Source.fromFile(file2).getLines.toSeq
    copied should be(lines)
    new File(file).delete()
    new File(file2).delete()
  }
}
