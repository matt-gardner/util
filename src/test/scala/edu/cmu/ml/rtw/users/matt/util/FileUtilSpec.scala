package edu.cmu.ml.rtw.users.matt.util

import org.scalatest._

// This class actually interacts with the filesystem, so we are going to make use of
// src/test/resources/ for this, reading from and writing to there as we test things.
class FileUtilSpec extends FlatSpecLike with Matchers {

  val fileUtil = new FileUtil()

  "mkdirOrDie" should "make a directory" in {
    true should be(false)
  }

  it should "still work even with no trailing /" in {
    true should be(false)
  }

  it should "throw an exception if the directory already exists" in {
    true should be(false)
  }

  "listDirectoryContents" should "correctly list the contents of a directory" in {
    true should be(false)
  }

  it should "return an empty list if there is no directory contents" in {
    true should be(false)
  }

  "recursiveListFile" should "list all files when no regex is given" in {
    true should be(false)
  }

  it should "correctly filter a directory structure" in {
    true should be(false)
  }

  "touchFile" should "create a new file" in {
    true should be(false)
  }

  it should "update timestamp on an existing file" in {
    true should be(false)
  }

  "deleteFile" should "delete a file" in {
    true should be(false)
  }

  "writeLinesToFile" should "write the lines to the file" in {
    true should be(false)
  }

  "addDirectorySeparatorIfNecessary" should "leave good paths alone" in {
    true should be(false)
  }

  it should "add a separator" in {
    true should be(false)
  }

  "readLinesFromReader" should "correctly get the lines from the reader" in {
    true should be(false)
  }

  "readLinesFromFile" should "correctly read the lines from the file" in {
    true should be(false)
  }

  it should "still work when given a file object instead of a String filename" in {
    true should be(false)
  }

  "readStringPairsFromFile" should "get string pairs from a two-column file" in {
    true should be(false)
  }

  "readMapFromTsvFile" should "read a map correctly" in {
    true should be(false)
  }

  it should "throw an error when the file is formatted incorrectly" in {
    true should be(false)
  }

  it should "except when skipErrors is true" in {
    true should be(false)
  }

  "readMapListFromTsvFile" should "read a simple map list" in {
    true should be(false)
  }

  "readInvertedMapListFromTsvFile" should "read an inverted map list" in {
    true should be(false)
  }

  "readIntegerSetFromFile" should "get an integer set correctly without a dictionary" in {
    true should be(false)
  }

  it should "work correctly with a dictionary" in {
    true should be(false)
  }

  "readIntegerListFromFile" should "get an integer list correctly without a dictionary" in {
    true should be(false)
  }

  it should "work correctly with a dictionary" in {
    true should be(false)
  }

  "readDoubleListFromFile" should "read a list of doubles from a file" in {
    true should be(false)
  }

  "fileExists" should "correctly tell if a file exists" in {
    true should be(false)
  }

  "copyLines" should "copy the lines from the reader to the writer" in {
    true should be(false)
  }

  "copy" should "copy a file from one location to another" in {
    true should be(false)
  }
}
