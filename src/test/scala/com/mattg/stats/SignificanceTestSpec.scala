package com.mattg.stats

import org.scalatest._

class SignificanceTestSpec extends FlatSpecLike with Matchers {

  "PairedPermutationTest" should "compute correct p values with an exact test" in {
    val list1 = Seq(1, 1, 1, 1)
    val list2 = Seq(0, 0, 0, 1)
    // The p-value here is: 4 out of the 16 sign permutations give an average difference greater
    // than or equal to .75, which is the sampled average difference.
    PairedPermutationTest().computePValue(list1, list2) should be((4.0/16) +- 0.0001)

    PairedPermutationTest().computePValue(List.fill(2)(list1).flatten, List.fill(2)(list2).flatten) should be((8.0/256) +- 0.0001)
  }

  it should "approximate correct p values when only sampling permutations" in {
    val list1 = Seq(1, 1, 1, 1)
    val list2 = Seq(0, 0, 0, 1)
    PairedPermutationTest(300000).computePValue(List.fill(4)(list1).flatten, List.fill(4)(list2).flatten) should be((32.0/(256*256)) +- 0.0001)
  }

  "TwoSidedPairedSignTest" should "compute correct p values" in {
    // Example taken from Wikipedia
    val list1 = Seq(142, 140, 144, 144, 142, 146, 149, 150, 142, 148)
    val list2 = Seq(138, 136, 147, 139, 143, 141, 143, 145, 136, 146)
    TwoSidedPairedSignTest.computePValue(list1, list2) should be(.109375 +- .00001)
  }

  it should "ignore ties when computing p values" in {
    val list1 = Seq(142, 140, 144, 144, 142, 146, 149, 150, 142, 148, 0, 0, 0, 0, 0, 0, 0)
    val list2 = Seq(138, 136, 147, 139, 143, 141, 143, 145, 136, 146, 0, 0, 0, 0, 0, 0, 0)
    TwoSidedPairedSignTest.computePValue(list1, list2) should be(.109375 +- .00001)
  }

}
