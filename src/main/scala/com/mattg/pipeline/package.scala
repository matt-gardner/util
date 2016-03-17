package com.mattg

import scala.collection.mutable

package object pipeline {

  // A Ranking is a scoring of some collection of objects.  These are not necessarily sorted,
  // though the score obviously defines an ordering.
  type Ranking[T] = Seq[(Double, T)]
  type Rankings[T] = Seq[Ranking[T]]

  // A Task is a name (the string) and a set of correct answers (the Set[T]).  If your tasks don't
  // have any name, just give it an integer it, converted to strings.  We need this so that we can
  // properly cache metric results.
  type Task[T] = (String, Set[T])

  // Metrics is just a mapping from some metric identifier ("MAP", "MRR", "AP") to a value.
  type Metrics = Map[String, Double]
  type MutableMetrics = mutable.Map[String, Double]

  // TaskMetrics is a mapping from Tasks to Metrics.
  type TaskMetrics = Map[String, Metrics]
  type MutableTaskMetrics = mutable.Map[String, MutableMetrics]

  // DatasetMetrics is a collection of metrics over the whole dataset, plus metrics for each task.
  type DatasetMetrics = (Metrics, TaskMetrics)
  type MutableDatasetMetrics = (MutableMetrics, MutableTaskMetrics)

  def EmptyMetrics: MutableMetrics = {
    new mutable.HashMap[String, Double]
  }

  def EmptyImmutableTaskMetrics: TaskMetrics = Map()

  def EmptyTaskMetrics: MutableTaskMetrics = {
    new mutable.HashMap[String, MutableMetrics]
  }

  def EmptyDatasetMetrics: MutableDatasetMetrics = {
    (EmptyMetrics, EmptyTaskMetrics)
  }

  def makeImmutable(metrics: MutableDatasetMetrics): DatasetMetrics = {
    (metrics._1.toMap, metrics._2.mapValues(_.toMap).toMap)
  }

  def makeImmutable(metrics: mutable.Map[String, MutableDatasetMetrics]): Map[String, DatasetMetrics] = {
    metrics.mapValues(makeImmutable).toMap
  }

  def mergeDatasetMetrics(metrics: MutableDatasetMetrics, computedMetrics: DatasetMetrics) {
    for ((key, value) <- computedMetrics._1) {
      metrics._1(key) = value
    }
    for ((task, computedTaskMetrics) <- computedMetrics._2) {
      val taskMetrics = metrics._2.getOrElseUpdate(task, EmptyMetrics)
      for ((key, value) <- computedTaskMetrics) {
        taskMetrics(key) = value
      }
    }
  }
}
