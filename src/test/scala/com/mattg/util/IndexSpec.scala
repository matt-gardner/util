package com.mattg.util

import org.scalatest._

class IndexSpec extends FlatSpecLike with Matchers {
  val index = new MutableConcurrentIndex[String](new StringParser(), new FileUtil());

  "MutableConcurrentIndex.getIndex" should "insert if element isn't present" in {
    index.clear
    index.getIndex("string 1") should be(1)
    index.getIndex("string 2") should be(2)
    index.getIndex("string 3") should be(3)
    index.getIndex("string 4") should be(4)
    index.getIndex("string 1") should be(1)
    index.getIndex("string 2") should be(2)
    index.getIndex("string 3") should be(3)
    index.getIndex("string 4") should be(4)
    index.getKey(1) should be("string 1")
    index.getKey(2) should be("string 2")
    index.getKey(3) should be("string 3")
    index.getKey(4) should be("string 4")
  }

  it should "work will multiple concurrent inserts" in {
    index.clear
    (1 to 1000).toList.par.foreach(i => {
      val j = index.getIndex("string")
      index.getKey(j) should be("string")
    })
    (1 to 1000).toList.par.foreach(i => {
      val j = index.getIndex(s"string $i")
      index.getKey(j) should be(s"string $i")
    })
  }

  "MutableConcurrentIndex.clear" should "clear the index" in {
    index.clear
    index.getIndex("string 1") should be(1)
    index.clear
    index.getIndex("string 2") should be(1)
  }

  "ImmutableDictionary" should "read a file and give a correct dictionary" in {
    val dictionaryFile = "/dictionary/file"
    val dictionaryFileContents = "1\tone\n3\tthree\n4\tfour\n5\tfive\n"
    val fileUtil = new FakeFileUtil
    fileUtil.addFileToBeRead(dictionaryFile, dictionaryFileContents)
    val dictionary = ImmutableDictionary.readFromFile(dictionaryFile, fileUtil)
    dictionary.getString(0) should be(null)
    dictionary.getString(1) should be("one")
    dictionary.getString(2) should be(null)
    dictionary.getString(3) should be("three")
    dictionary.getString(4) should be("four")
    dictionary.getString(5) should be("five")
    TestUtil.expectError(classOf[ArrayIndexOutOfBoundsException], new TestUtil.Function() {
      override def call() {
        dictionary.getString(6)
      }
    })
    dictionary.getIndex("one") should be(1)
    dictionary.getIndex("three") should be(3)
    dictionary.getIndex("four") should be(4)
    dictionary.getIndex("five") should be(5)
    TestUtil.expectError(classOf[NoSuchElementException], new TestUtil.Function() {
      override def call() {
        dictionary.getIndex("two")
      }
    })
  }
}
