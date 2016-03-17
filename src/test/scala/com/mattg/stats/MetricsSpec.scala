package com.mattg.util

import org.scalatest._

import com.mattg.util.TestUtil.Function

class StatsSpec extends FlatSpecLike with Matchers {
  "computeAveragePrecision" should "give the correct answer with a simple example" in {
    val scores = Seq((1.0, "a"), (.8, "b"), (.4, "c"))
    Stats.computeAveragePrecision(scores, Set("b")) should be(.5)
    Stats.computeAveragePrecision(scores, Set("b", "c")) should be(.5833 +- .0001)
    Stats.computeAveragePrecision(scores, Set("a", "b", "c")) should be(1)
    Stats.computeAveragePrecision(scores, Set("a", "b")) should be(1)
    Stats.computeAveragePrecision(scores, Set("a", "b", "d", "e", "f")) should be(.4 +- .0001)
    Stats.computeAveragePrecision(scores, Set()) should be(0)
  }

  it should "respect ties in scores, too" in {
    val scores = Seq((1.0, "a"), (1.0, "b"), (.8, "c"), (.8, "d"))
    Stats.computeAveragePrecision(scores, Set("a")) should be(.5 +- .0001)
    Stats.computeAveragePrecision(scores, Set("b")) should be(.5 +- .0001)
    Stats.computeAveragePrecision(scores, Set("c")) should be(.25 +- .0001)
    Stats.computeAveragePrecision(scores, Set("d")) should be(.25 +- .0001)
  }

  "computeReciprocalRank" should "give the correct answer with a simple example" in {
    val scores = Seq((1.0, "a"), (.8, "b"), (.4, "c"))
    Stats.computeReciprocalRank(scores, Set("a", "b", "c")) should be(1)
    Stats.computeReciprocalRank(scores, Set("b", "c")) should be(.5 +- .0001)
    Stats.computeReciprocalRank(scores, Set("b", "d", "e", "f")) should be(.5 +- .0001)
    Stats.computeReciprocalRank(scores, Set("c")) should be(.3333 +- .0001)
    Stats.computeReciprocalRank(scores, Set("d", "e")) should be(0)
    Stats.computeReciprocalRank(scores, Set()) should be(0)
  }

  it should "respect ties in scores, too" in {
    val scores = Seq((1.0, "a"), (1.0, "b"), (.8, "c"), (.8, "d"))
    Stats.computeReciprocalRank(scores, Set("a")) should be(.5 +- .0001)
    Stats.computeReciprocalRank(scores, Set("b")) should be(.5 +- .0001)
    Stats.computeReciprocalRank(scores, Set("c")) should be(.25 +- .0001)
    Stats.computeReciprocalRank(scores, Set("d")) should be(.25 +- .0001)
  }
}
