package com.mattg.stats

object Metrics {

  // A helper method that really could belong in a different class.  Just computing average
  // precision from a list of scored objects.  We group and sort this list, just in case it wasn't
  // done previously.  I don't expect that the inputs will ever be long enough that that's a
  // problem.
  def computeAveragePrecision[T](scores: Seq[(Double, T)], correctInstances: Set[T]): Double = {
    if (correctInstances.size == 0) return 0
    var numPredictionsSoFar = 0
    var totalPrecision = 0.0
    var correctSoFar = 0.0  // this is a double to avoid casting later

    // These are double scores, so ties should be uncommon, but they do happen.
    val grouped = scores.groupBy(_._1).toSeq.sortBy(-_._1)
    for (resultsWithScore <- grouped) {
      val score = resultsWithScore._1
      for ((score, instance) <- resultsWithScore._2) {
        numPredictionsSoFar += 1
        if (correctInstances.contains(instance)) {
          correctSoFar += 1.0
        }
      }
      for ((score, instance) <- resultsWithScore._2) {
        if (correctInstances.contains(instance)) {
          totalPrecision += (correctSoFar / numPredictionsSoFar)
        }
      }
    }
    totalPrecision / correctInstances.size
  }

  // Another helper method, this time computing reciprocal rank.  Same notes apply here as apply to
  // computeAveragePrecision.
  def computeReciprocalRank[T](scores: Seq[(Double, T)], correctInstances: Set[T]): Double = {
    if (correctInstances.size == 0) return 0
    var numPredictionsSoFar = 0
    var correctSoFar = 0.0  // this is a double to avoid casting later
    var rankAtFirstCorrect = -1

    // These are double scores, so ties should be uncommon, but they do happen.
    val grouped = scores.groupBy(_._1).toSeq.sortBy(-_._1)
    for (resultsWithScore <- grouped) {
      val score = resultsWithScore._1
      var found = false
      for ((score, instance) <- resultsWithScore._2) {
        numPredictionsSoFar += 1
        if (correctInstances.contains(instance)) {
          found = true
        }
      }
      if (found && rankAtFirstCorrect == -1) {
        rankAtFirstCorrect = numPredictionsSoFar
      }
    }
    if (rankAtFirstCorrect == -1) 0 else 1.0 / rankAtFirstCorrect
  }

  // Another helper method that could belong in a different class.  Here's we're computing a
  // simplified precision recall curve with just 11 points (though, really, we could output the
  // whole list and get a finer-grained curve...).  The returned Seq[Double] will have 11 entries.
  def compute11PointPrecision[T](scores: Seq[(Double, T)], correctInstances: Set[T]): Seq[Double] = {
    var correctSoFar = 0.0  // this is a double to avoid casting later
    val precisionRecall = scores.zipWithIndex.map(instanceScoreIndex => {
      val ((score, instance), index) = instanceScoreIndex
      if (correctInstances.contains(instance)) {
        correctSoFar += 1.0
      }
      val precision = correctSoFar / (index + 1)
      val recall = if (correctInstances.size == 0) 0 else correctSoFar / correctInstances.size
      (precision, recall)
    })

    var maxPrecision = 0.0
    val interpolatedPrecision = precisionRecall.map(_._1).toArray
    for (i <- 0 until precisionRecall.size) {
      val index = precisionRecall.size - (i + 1)
      maxPrecision = Math.max(maxPrecision, precisionRecall(index)._1)
      interpolatedPrecision(index) = maxPrecision
    }

    (0 to 10).map(i => {
      val point = i * .1
      val recallIndex = precisionRecall.indexWhere(_._2 >= point)
      if (recallIndex >= 0) interpolatedPrecision(recallIndex) else 0.0
    })
  }
}
