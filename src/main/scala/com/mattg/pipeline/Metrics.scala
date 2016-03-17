package com.mattg.pipeline

import collection.mutable
import scala.math.Ordering.Implicits._

import com.mattg.stats.Metrics
import com.mattg.stats.PairedPermutationTest
import com.mattg.stats.SignificanceTest
import com.mattg.stats.TwoSidedPairedSignTest
import com.mattg.util.FileUtil

trait MetricComputer {
  def computeDatasetMetrics[T](
    rankings: Rankings[T],
    tasks: Seq[Task[T]],
    alreadyComputedMetrics: DatasetMetrics
  ): DatasetMetrics

  def computeTaskMetrics[T](ranking: Ranking[T], taskAnswers: Set[T]): Metrics
  def collectionMetricsComputed: Seq[String]
  def taskMetricsComputed: Seq[String]
}

object MapAndMrrComputer extends MetricComputer {
  override def computeDatasetMetrics[T](
    rankings: Seq[Ranking[T]],
    tasks: Seq[Task[T]],
    alreadyComputedMetrics: DatasetMetrics
  ): DatasetMetrics = {
    val taskMetrics = rankings.zip(tasks).par.map(taskRanking => {
      val ranking = taskRanking._1
      val task = taskRanking._2
      val taskName = task._1
      val taskAnswers = task._2
      val metrics = computeTaskMetrics(ranking, taskAnswers)
      (taskName -> metrics)
    }).toMap.seq
    val collectionMetrics = EmptyMetrics
    collectionMetrics("MAP") = taskMetrics.map(_._2("AP")).sum / rankings.size
    collectionMetrics("MRR") = taskMetrics.map(_._2("RR")).sum / rankings.size
    (collectionMetrics.toMap, taskMetrics)
  }

  override def computeRelationMetrics[T](ranking: Ranking[T], taskAnswers: Set[T]) = {
    val metrics = EmptyMetrics
    metrics("AP") = Metrics.computeAveragePrecision(ranking, taskAnswers)
    metrics("RR") = Metrics.computeReciprocalRank(ranking, taskAnswers)
    metrics.toMap
  }

  override def collectionMetricsComputed = Seq("MAP", "MRR")

  override def taskMetricsComputed = Seq("AP", "RR")
}

// Must be included AFTER MapAndMrrComputer, as this does not compute AP for each task.
class WeightedMapComputer(taskWeights: Seq[Double]) extends MetricComputer {
  override def computeDatasetMetrics[T](
    rankings: Seq[Ranking[T]],
    tasks: Seq[Task[T]],
    alreadyComputedMetrics: DatasetMetrics
  ): DatasetMetrics = {
    val taskMetrics = alreadyComputedMetrics._2
    val weightedAps = tasks.zip(taskWeights).map(taskWithWeight => {
      val taskName = taskWithWeight._1._1
      val weight = taskWithWeight._2
      val ap = taskMetrics(taskName)("AP")
      weight * ap
    })
    val collectionMetrics = EmptyMetrics
    collectionMetrics("Weighted MAP") = weightedAps.sum / taskWeights.sum
    (collectionMetrics.toMap, EmptyImmutableTaskMetrics)
  }

  override def computeRelationMetrics[T](ranking: Ranking[T], taskAnswers: Set[T]) = {
    val metrics = EmptyMetrics
    metrics("AP") = Metrics.computeAveragePrecision(ranking, taskAnswers)
    metrics("RR") = Metrics.computeReciprocalRank(ranking, taskAnswers)
    metrics.toMap
  }

  override def collectionMetricsComputed = Seq("Weighted MAP")

  override def taskMetricsComputed = Seq()
}

object MetricOutputter {
  val defaultMetricComputers = Seq(MapAndMrrComputer)
  val defaultMethodMetricsToDisplay = Seq("MAP", "MRR")
  val defaultTaskMetricsToDisplay = Seq()
  val defaultSignificanceTests = Seq(
    ("AP", PairedPermutationTest),
    ("AP", TwoSidedPairedSignTest),
    ("RR", PairedPermutationTest),
    ("RR", TwoSidedPairedSignTest)
  )
  val defaultSortResultsBy = Seq("-MAP", "-MRR")
  val defaultSignificanceThreshold = 0.05
}

class MetricOutputter[T](
  methodNamesAndTimestamps: Seq[(String, Long)],
  methodOutputs: String => Rankings[T],  // called if the method output is not cached
  taskComputer: => Seq[Task[T]],  // called if there is some method for which we need to compute metrics
  cacheFile: String,
  metricComputers: Seq[MetricComputer] = MetricOutputter.defaultMetricComputers,
  methodMetricsToDisplay: Seq[String] = MetricOutputter.defaultMethodMetricsToDisplay,
  taskMetricsToDisplay: Seq[String] = MetricOutputter.defaultTaskMetricsToDisplay,
  significanceTests: Seq[(String, SignificanceTest)] = MetricOutputter.defaultSignificanceTests,
  significanceThreshold: Double = MetricOutputter.defaultSignificanceThreshold,
  sortResultsBy: Seq[String] = MetricOutputter.defaultSortResultsBy,
  fileUtil: FileUtil = new FileUtil
) {

  val DATASET_TASK_NAME = "__DATASET__"
  val TIMESTAMP = "__TIMESTAMP__"

  lazy val tasks = taskComputer

  lazy val savedMetrics: mutable.Map[String, MutableDatasetMetrics] = {
    val metrics = new mutable.HashMap[String, MutableDatasetMetrics]
    if (fileUtil.fileExists(cacheFile)) {
      for (line <- fileUtil.getLineIterator(cacheFile)) {
        val fields = line.split("\t")
        val methodName = fields(0)
        val taskName = fields(1)
        val metric = fields(2)
        val value = fields(3).toDouble
        val datasetMetrics = metrics.getOrElseUpdate(methodName, EmptyDatasetMetrics)
        if (taskName == DATASET_TASK_NAME) {
          datasetMetrics._1(metric) = value
        } else {
          val taskMetrics = datasetMetrics._2.getOrElseUpdate(taskName, EmptyMetrics)
          taskMetrics(metric) = value
        }
      }
    }
    metrics
  }

  def scoreMethods() {
    val metrics = new mutable.HashMap[String, MutableDatasetMetrics]

    // First, we compute whatever metrics are not in our cache (or that have been updated).
    for ((methodName, timestamp) <- methodNamesAndTimestamps) {
      savedMetrics.get(methodName) match {
        case Some(datasetMetrics) => {
          if (datasetMetrics._1(TIMESTAMP) < timestamp) {
            updateMetricsForMethod(methodName, metrics.getOrElseUpdate(methodName, EmptyDatasetMetrics))
          } else {
            metrics(methodName) = datasetMetrics
          }
        }
        case None => {
          updateMetricsForMethod(methodName, metrics.getOrElseUpdate(methodName, EmptyDatasetMetrics))
        }
      }
    }
    val finalMetrics = metrics.mapValues(makeImmutable).toMap

    // Now, save all of the metrics we've computed (while keeping those that we've already cached).
    for ((method, datasetMetrics) <- finalMetrics) {
      mergeDatasetMetrics(savedMetrics.getOrElseUpdate(method, EmptyDatasetMetrics), datasetMetrics)
    }
    saveMetrics(makeImmutable(savedMetrics))

    // Finally, display the metrics.  We first display overall metrics on the whole dataset for
    // each method, then we display significance test results, and finally we give a table of
    // indidivual task results for any metrics that were asked for (though this last one could be
    // really big, if you have a lot of tasks, so we don't do this by default).
    val sortedMethods = displayDatasetMetrics(finalMetrics)
    for (test <- significanceTests) {
      displaySignificanceTests(finalMetrics, sortedMethods, test)
    }
    for (metric <- taskMetricsToDisplay) {
      displayTaskMetrics(finalMetrics, sortedMethods, metric)
    }
  }

  def displayDatasetMetrics(metrics: Map[String, DatasetMetrics]) = {
    val methods = metrics.map(_._1).toList.sortBy(x => sortKeyFunction(metrics(x)))
    val methodHeader = "Method"
    print(f"$methodHeader%-45s")
    for (metricHeader <- methodMetricsToDisplay) {
      print(f"$metricHeader%15s")
    }
    println()
    for ((method, i) <- methods.zipWithIndex) {
      print(f"(${i+1}%2d) ${method}%-41s")
      for (displayMetric <- methodMetricsToDisplay) {
        try {
          print(f"${metrics(method)._1(displayMetric)}%15.4f")
        } catch {
          case e: java.util.NoSuchElementException => {
            val message = "No value"
            print(f"${message}%15s")
          }
        }
      }
      println()
    }
    methods
  }

  def displaySignificanceTests(
    metrics: Map[String, DatasetMetrics],
    sortedMethods: Seq[String],
    significanceTest: (String, SignificanceTest)
  ) {
    val metric = significanceTest._1
    val test = significanceTest._2
    println(s"\nSignificance tests for metric: $metric, with test: ${test.name}")
    print("   ")
    for ((method, i) <- sortedMethods.zipWithIndex) {
      print(f" ${i+1}%2d      ")
    }
    println()
    for ((method1, i) <- sortedMethods.zipWithIndex) {
      print(f"${i+1}%2d  ")
      val method1Values = metrics(method1)._2.toList.sortBy(_._1).map(_._2(metric))
      for ((method2, j) <- sortedMethods.zipWithIndex) {
        if (j == 0) {
          print("   ")
        } else if (j <= i) {
          print("         ")
        } else {
          val method2Values = metrics(method2)._2.toList.sortBy(_._1).map(_._2(metric))
          val p_value = test.computePValue(method1Values, method2Values)
          if (p_value < significanceThreshold) {
            setColor(Console.GREEN)
          }
          print(f" $p_value%7.5f ")
          resetColor()
        }
      }
      println()
    }
  }

  def displayTaskMetrics(
    metrics: Map[String, DatasetMetrics],
    sortedMethods: Seq[String],
    metric: String
  ) {
    val tasks = new mutable.HashSet[String]
    for (method <- sortedMethods;
         task <- metrics(method)._2.map(_._1)
         if metrics(method)._2(task).isDefinedAt(metric)) {
      tasks += task
    }
    val sortedTasks = tasks.toList.sorted
    println(s"\nPer-task $metric:")
    val header = "Task"
    print(f"$header%-60s")
    for ((method, i) <- sortedMethods.zipWithIndex) {
      print(f"      ${i+1}%2d ")
    }
    println()
    for (task <- sortedTasks) {
      print(f"${task}%-60s")
      for (method <- sortedMethods) {
        if (!metrics(method)._2.isDefinedAt(task)) {
          print("         ")
        } else {
          val value = metrics(method)._2(task)(metric)
          print(f" $value%7.5f ")
        }
      }
      println()
    }
  }

  def sortKeyFunction(metrics: DatasetMetrics) = {
    val entries = new mutable.ListBuffer[Double]
    for (key <- sortResultsBy) {
      if (key.charAt(0) == '-') {
        entries += -metrics._1(key.substring(1))
      } else {
        entries += metrics._1(key)
      }
    }
    entries.toList
  }

  def updateMetricsForMethod(methodName: String, metrics: MutableDatasetMetrics) {
    val rankings = methodOutputs(methodName)
    for (metricComputer <- metricComputers) {
      val computedMetrics = metricComputer.computeDatasetMetrics(rankings, tasks, makeImmutable(metrics))
      mergeDatasetMetrics(metrics, computedMetrics)
    }
  }

  def saveMetrics(metrics: Map[String, DatasetMetrics]) {
    val out = fileUtil.getFileWriter(cacheFile)
    for (methodOutput <- metrics) {
      val methodName = methodOutput._1
      val methodMetrics = methodOutput._2
      val datasetMetrics = methodMetrics._1
      for ((metric, value) <- datasetMetrics) {
        out.write(s"$methodName\t$DATASET_TASK_NAME\t$metric\t$value\n")
      }
      for ((taskName, metrics) <- methodMetrics._2;
           (metric, value) <- metrics) {
             out.write(s"$methodName\t$taskName\t$metric\t$value\n")
      }
    }
    out.close()
  }

  def setColor(color: String) {
    print(color)
  }

  def resetColor() {
    print(Console.BLACK_B)
    print(Console.WHITE)
  }
}
