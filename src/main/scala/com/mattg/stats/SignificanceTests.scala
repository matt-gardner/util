package com.mattg.stats

import Numeric.Implicits._
import org.apache.commons.math3.distribution.BinomialDistribution

trait SignificanceTest {
  val name: String
  def computePValue[T:Numeric](method1Results: Seq[T], method2Results: Seq[T]): Double
}

case class PairedPermutationTest(iters: Int = 100000) extends SignificanceTest {
  override val name = "paired permutation test"
  override def computePValue[T:Numeric](method1Results: Seq[T], method2Results: Seq[T]) = {
    if (method1Results.size != method2Results.size) {
      throw new IllegalStateException("This is a paired test, and sizes don't match")
    }
    val values = method1Results.map(_.toDouble).zip(method2Results.map(_.toDouble))
    if (values.size < 15) {
      getExactPValue(values)
    } else {
      getSampledPValue(values)
    }
  }

  def getExactPValue(values: Seq[(Double, Double)]) = {
    val diffs = values.map(x => x._1 - x._2)
    val mean_diff = math.abs(diffs.sum) / diffs.length
    var n = 0.0
    val iters = math.pow(2, diffs.length).toInt
    for (i <- 1 to iters) {
      val diff = getDiffForSample(diffs, i)
      if (diff >= mean_diff) n += 1
    }
    n / iters
  }

  def getSampledPValue(values: Seq[(Double, Double)]) = {
    import scala.util.Random
    val random = new Random
    val diffs = values.map(x => x._1 - x._2)
    val mean_diff = math.abs(diffs.sum) / diffs.length
    val n = (1 to iters).par.map(i => {
      val diff = getDiffForSample(diffs, math.abs(random.nextInt))
      if (diff >= mean_diff) {
        1
      } else {
        0
      }
    }).sum
    n.toDouble / iters
  }

  def getDiffForSample(diffs: Seq[Double], signs: Int) = {
    var a = signs
    var diff = 0.0
    for (index <- 1 to diffs.length) {
      val currentDiff = diffs(diffs.length - index).toDouble
      if (a % 2 == 1) {
        diff -= currentDiff
      }
      else {
        diff += currentDiff
      }
      if (a > 0) {
        a = a >> 1
      }
    }
    math.abs(diff / diffs.length)
  }
}

object TwoSidedPairedSignTest extends SignificanceTest {
  override val name = "two-sided paired sign test"

  override def computePValue[T:Numeric](method1Results: Seq[T], method2Results: Seq[T]) = {
    if (method1Results.size != method2Results.size) {
      throw new IllegalStateException("This is a paired test, and sizes don't match")
    }
    val values = method1Results.map(_.toDouble).zip(method2Results.map(_.toDouble))

    // We do both methods this way to account for ties.
    val method1Wins = values.filter(r => r._1 > r._2).size
    val method2Wins = values.filter(r => r._2 > r._1).size
    val ties = values.filter(r => r._1 == r._2).size

    // The binomial is symmetric when p = 0.5, so we make our lives easier by taking a min here.
    val method1Test = math.min(method1Wins, values.size - ties - method1Wins)
    val method2Test = math.min(method2Wins, values.size - ties - method2Wins)

    val binomial = new BinomialDistribution(values.size - ties, 0.5)
    val cumProb1 = binomial.cumulativeProbability(method1Test)
    val cumProb2 = binomial.cumulativeProbability(method2Test)
    val pValue = cumProb1 + cumProb2
    pValue
  }
}
