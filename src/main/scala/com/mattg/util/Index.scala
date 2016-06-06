package com.mattg.util

import scala.collection.mutable
import scala.collection.concurrent
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger

import gnu.trove.{TObjectIntHashMap => TMap}

/**
 * A mapping from some object to integers, for any application where such a mapping is useful
 * (generally because working with integers is much faster and less memory-intensive than working
 * with objects).
 */
trait Index[T >: Null] {
  def hasKey(key: T): Boolean
  def getIndex(key: T): Int
  def getKey(index: Int): T
  def size(): Int

  def writeToFile(filename: String)

  def writeToWriter(writer: FileWriter) {
    if (size() > 20000000) {
      // This is approaching the size of something that can't fit in a String object, so we
      // have to write it directly to disk, not use the printToString() method.
      var builder = new StringBuilder()
      for (i <- 1 until size()) {
        if (i % 1000000 == 0) {
          writer.write(builder.toString())
          builder = new StringBuilder()
        }
        builder.append(i)
        builder.append("\t")
        builder.append(getKey(i).toString())
        builder.append("\n")
      }
      writer.write(builder.toString())
    } else {
      writer.write(printToString())
    }
    writer.close()
  }

  def printToString(): String = {
    val builder = new StringBuilder()
    for (i <- 1 until size) {
      builder.append(i)
      builder.append("\t")
      val key = getKey(i)
      if (key == null) {
        builder.append("__@NULL KEY@__")
      } else {
        builder.append(key.toString())
      }
      builder.append("\n")
    }
    return builder.toString()
  }
}

class MutableConcurrentIndex[T >: Null](
  factory: ObjectParser[T],
  verbose: Boolean = false,
  fileUtil: FileUtil = new FileUtil
) extends Index[T] {
  val map = new concurrent.TrieMap[T, Int]
  val reverse_map = new concurrent.TrieMap[Int, T]
  val nextIndex = new AtomicInteger(1)

  /**
   * Test if key is already in the dictionary
   */
  override def hasKey(key: T): Boolean = map.contains(key)

  /**
   * Returns the index for key, adding to the dictionary if necessary.
   */
  override def getIndex(key: T): Int = {
    if (key == null) {
      throw new RuntimeException("A null key was passed to the dictionary!")
    }
    map.get(key) match {
      case Some(i) => {
        ensureReverseIsPresent(i)
        i
      }
      case None => {
        if (verbose) {
          System.out.println("Key not in index: " + key)
        }
        val new_i = nextIndex.getAndIncrement()
        val i_added_concurrently = map.putIfAbsent(key, new_i)
        i_added_concurrently match {
          case Some(j) => {
            ensureReverseIsPresent(j)
            j
          }
          case None => {
            if (verbose) {
              System.out.println(s"Key added to index at position ${new_i}: ${key}")
              System.out.println(s"next index is ${nextIndex.get()}\n")
            }
            reverse_map.put(new_i, key)
            new_i
          }
        }
      }
    }
  }

  override def size() = getNextIndex()
  override def writeToFile(filename: String) {
    writeToWriter(fileUtil.getFileWriter(filename))
  }

  // I don't like this!  But I'm not sure how else to guarantee that this actually works right.  I
  // need the put in the map and the reverse_map to happen atomically, but I don't know how to do
  // that.  So instead, we just take this hit here...
  def ensureReverseIsPresent(i: Int) = while (reverse_map.getOrElse(i, null) == null) Thread.sleep(1)

  override def getKey(index: Int): T = {
    val key = reverse_map.getOrElse(index, null)
    if (verbose) println(s"Key for ${index}: ${key}\n")
    key
  }

  def getNextIndex(): Int = nextIndex.get()

  def clear() {
    map.clear()
    reverse_map.clear()
    nextIndex.set(1)
  }

  def setFromFile(filename: String) {
    map.clear()
    reverse_map.clear()
    var max_index = 0
    for (line <- fileUtil.getLineIterator(filename)) {
      val parts = line.split("\t")
      val num = parts(0).toInt
      if (num > max_index) {
        max_index = num
      }
      val key = factory.fromString(parts(1))
      map.put(key, num)
      reverse_map.put(num, key)
    }
    nextIndex.set(max_index+1)
  }
}

class ImmutableIndex[T >: Null](
  indices: TMap[T],
  entries: Array[T],
  fileUtil: FileUtil = new FileUtil
) extends Index[T] {
  override def hasKey(key: T): Boolean = indices.containsKey(key)
  override def getIndex(key: T): Int = {
    // TMap will return 0 here instead of null or throwing an exception, so we need to make the
    // failure more visible.
    if (!hasKey(key)) throw new NoSuchElementException(key.toString)
    indices.get(key)
  }
  override def getKey(index: Int): T = entries(index)
  override def size() = entries.size
  override def writeToFile(filename: String) {
    writeToWriter(fileUtil.getFileWriter(filename))
  }
}

object ImmutableIndex {
  def instance[T >: Null](
    indices: TMap[T],
    entries: Array[T],
    fileUtil: FileUtil
  ): ImmutableIndex[T] = {
    new ImmutableIndex(indices, entries, fileUtil)
  }

  def readFromFile[T >: Null, R <: ImmutableIndex[T]](
    filename: String,
    factory: ObjectParser[T],
    createInstance: (TMap[T], Array[T], FileUtil) => R,
    fileUtil: FileUtil = new FileUtil
  )(implicit tag: reflect.ClassTag[T]): R = {
    val indices = new TMap[T]
    val entries = new mutable.ArrayBuffer[T]
    // Trying to make reading the file more efficient by avoiding creating extra strings, and by
    // avoiding the boxing of integers (and creating of unnecessary Integer objects) when returning
    // Ints in a tuple.
    def parseFun(line: String): Unit = {
      var index = 0
      var i = 0
      while (line.charAt(i) != '\t') {
        // This is reading an ASCII digit and adding it as part of a base-10 integer to `index`.
        index *= 10  // each additional character means what we've read so far is multiplied by 10
        index += line.charAt(i) - 48  // this goes from an ASCII digit to an integer
        i += 1
      }
      i += 1
      // It turns out that leaving out the java.lang here creates an _additional_ StringBuilder
      // object, a scala wrapper around the java one.
      val builder = new java.lang.StringBuilder
      while (i < line.length) {
        builder.append(line.charAt(i))
        i += 1
      }
      val key = factory.fromString(builder.toString)
      indices.put(key, index)
      while (index > entries.size) {
        entries += null
      }
      entries += key
    }
    fileUtil.processFile(filename, parseFun _)
    createInstance(indices, entries.toArray, fileUtil)
  }
}
