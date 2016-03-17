package com.mattg.stats

import org.apache.commons.math3.distribution.BinomialDistribution

trait SignificanceTest {
  val name: String
  def computePValue(method1Results: Seq[Double], method2Results: Seq[Double]): Double
}

object PairedPermutationTest extends SignificanceTest {
  override val name = "paired permutation test"
  override def computePValue(method1Results: Seq[Double], method2Results: Seq[Double]) = {
    if (method1Results.size != method2Results.size) {
      throw new IllegalStateException("This is a paired test, and sizes don't match")
    }
    val values = method1Results.zip(method2Results)
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
    var n = 0.0
    val iters = 10000
    for (i <- 1 to iters) {
      val diff = getDiffForSample(diffs, math.abs(random.nextInt))
      if (diff >= mean_diff) n += 1
    }
    n / iters
  }

  def getDiffForSample(diffs: Seq[Double], signs: Int) = {
    var a = signs
    var diff = 0.0
    for (index <- 1 to diffs.length) {
      if (a % 2 == 1) {
        diff -= diffs(diffs.length - index)
      }
      else {
        diff += diffs(diffs.length - index)
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
  override def computePValue(method1Results: Seq[Double], method2Results: Seq[Double]) = {
    if (method1Results.size != method2Results.size) {
      throw new IllegalStateException("This is a paired test, and sizes don't match")
    }
    val values = method1Results.zip(method2Results)

    // We do both methods this way to account for ties.
    val method1Wins = values.filter(r => r._1 > r._2).size
    val method2Wins = values.filter(r => r._2 > r._1).size

    // The binomial is symmetric when p = 0.5, so we make our lives easier by taking a min here.
    val method1Test = math.min(method1Wins, values.size - method1Wins)
    val method2Test = math.min(method2Wins, values.size - method2Wins)

    val binomial = new BinomialDistribution(values.size, 0.5)
    val cumProb1 = binomial.cumulativeProbability(method1Test)
    val cumProb2 = binomial.cumulativeProbability(method2Test)
    val pValue = cumProb1 + cumProb2
    pValue
  }
}
