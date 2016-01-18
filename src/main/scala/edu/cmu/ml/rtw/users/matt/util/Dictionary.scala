package edu.cmu.ml.rtw.users.matt.util

/**
 * A String index is called a dictionary, because that's what I originally called it.
 */
trait Dictionary extends Index[String] {
  def hasString(string: String) = hasKey(string)
  def getString(index: Int) = getKey(index)
}

class MutableConcurrentDictionary(
  verbose: Boolean = false,
  fileUtil: FileUtil = new FileUtil
) extends MutableConcurrentIndex[String](new StringParser, verbose, fileUtil) with Dictionary {
}

class ImmutableDictionary(
  indices: Map[String, Int],
  entries: Array[String],
  fileUtil: FileUtil = new FileUtil
) extends ImmutableIndex[String](indices, entries, fileUtil) with Dictionary {
}

object ImmutableDictionary {
  def instance(
    indices: Map[String, Int],
    entries: Array[String],
    fileUtil: FileUtil
  ): ImmutableDictionary = {
    new ImmutableDictionary(indices, entries, fileUtil)
  }

  def readFromFile(filename: String, fileUtil: FileUtil = new FileUtil): ImmutableDictionary = {
    ImmutableIndex.readFromFile(filename, new StringParser, instance, fileUtil)
  }
}
