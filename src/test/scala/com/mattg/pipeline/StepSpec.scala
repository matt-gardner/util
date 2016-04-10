package com.mattg.pipeline

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._

import com.mattg.util.FileUtil
import com.mattg.util.TestUtil
import com.mattg.util.TestUtil.Function

class StepSpec extends FlatSpecLike with Matchers {
  val fileUtil = new FileUtil
  implicit val formats = DefaultFormats

  class Step0(params: Option[JValue]) extends Step(params, fileUtil) {
    override def _runStep() = fileUtil.touchFile("src/test/resources/step0")
    override val name = "step 0"
    override val outputs = Set("src/test/resources/step0")
    override val inputs: Set[(String, Option[Step])] = Set(("src/test/resources/empty_file.txt", None))
    override val inProgressFile = outputs.head + "_in_progress"
  }

  class Step1(params: Option[JValue]) extends Step(params, fileUtil) {
    override def _runStep() = fileUtil.touchFile("src/test/resources/step1")
    override val name = "step 1"
    override val outputs = Set("src/test/resources/step1")
    override val inputs: Set[(String, Option[Step])] = Set()
    override val inProgressFile = outputs.head + "_in_progress"
  }

  class Step2(params: Option[JValue]) extends Step(params, fileUtil) {
    override def _runStep() = fileUtil.touchFile("src/test/resources/step2")
    override val name = "step 2"
    override val outputs = Set("src/test/resources/step2")
    override val inputs: Set[(String, Option[Step])] = Set(("src/test/resources/step1", Some(new Step1(None))))
    override val inProgressFile = outputs.head + "_in_progress"
  }

  class Step3(params: Option[JValue]) extends Step(params, fileUtil) {
    override def _runStep() = fileUtil.touchFile("src/test/resources/step3")
    override val name = "step 3"
    override val outputs = Set("src/test/resources/step3")
    override val inputs: Set[(String, Option[Step])] = Set(("src/test/resources/step2", Some(new Step2(None))))
    override val inProgressFile = outputs.head + "_in_progress"
  }

  class StepWithPriorParams(params: Option[JValue]) extends Step(params, fileUtil) {
    val filename = (params.get \ "filename").extract[String]
    override def _runStep() = fileUtil.touchFile(s"src/test/resources/$filename")
    override val paramFile = "src/test/resources/param_file2"
    override val name = "step with prior params"
    override val outputs = Set(s"src/test/resources/$filename")
    override val inputs: Set[(String, Option[Step])] = Set()
    override val inProgressFile = outputs.head + "_in_progress"
  }

  class StepWithParams(params: Option[JValue]) extends Step(params, fileUtil) {
    val filename = (params.get \ "filename").extract[String]
    val priorStepParams = (params.get \ "prior params")
    val priorFilename = (priorStepParams \ "filename").extract[String]
    override def _runStep() = fileUtil.touchFile(s"src/test/resources/$filename")
    override val paramFile = "src/test/resources/param_file"
    override val name = "step with params"
    override val outputs = Set(s"src/test/resources/$filename")
    override val inputs: Set[(String, Option[Step])] = Set(
      (s"src/test/resources/$priorFilename", Some(new StepWithPriorParams(Some(priorStepParams)))))
    override val inProgressFile = outputs.head + "_in_progress"
  }

  class MultipleProvides(params: Option[JValue]) extends Step(params, fileUtil) {
    MultipleProvides.timesRun = 0
    override def _runStep() = {
      fileUtil.touchFile(s"src/test/resources/multiple_outputs_1")
      fileUtil.touchFile(s"src/test/resources/multiple_outputs_2")
      fileUtil.touchFile(s"src/test/resources/multiple_outputs_3")
      MultipleProvides synchronized { MultipleProvides.timesRun += 1 }
    }
    override val name = "multiple outputs"
    override val outputs = Set(
      "src/test/resources/multiple_outputs_1",
      "src/test/resources/multiple_outputs_2",
      "src/test/resources/multiple_outputs_3"
    )
    override val inProgressFile = outputs.head + "_in_progress"
    override val inputs: Set[(String, Option[Step])] = Set()
  }
  object MultipleProvides {
    var timesRun = 0
  }

  class MultipleRequires(params: Option[JValue]) extends Step(params, fileUtil) {
    val subStep = new MultipleProvides(None)
    override def _runStep() = fileUtil.touchFile(s"src/test/resources/multiple_inputs")
    override val name = "multiple_inputs"
    override val outputs = Set(s"src/test/resources/multiple_inputs")
    override val inputs: Set[(String, Option[Step])] = Set(
      ("src/test/resources/multiple_outputs_1", Some(subStep)),
      ("src/test/resources/multiple_outputs_2", Some(subStep)),
      ("src/test/resources/multiple_outputs_3", Some(subStep))
    )
    override val inProgressFile = outputs.head + "_in_progress"
  }

  "runPipeline" should "succeed when a required input file is already present" in {
    val step = new Step0(None)
    step.runPipeline()
    fileUtil.fileExists("src/test/resources/step0") should be(true)
    fileUtil.fileExists(step.inProgressFile) should be(false)
    fileUtil.deleteFile("src/test/resources/step0")
  }

  it should "fail when a required input file is missing" in {
    val step = new Step0(None) { override val inputs: Set[(String, Option[Step])] = Set(("src/test/non-existant-file", None)) }
    TestUtil.expectError(classOf[IllegalStateException], "No step given", new Function() {
      override def call() {
        step.runPipeline()
      }
    })
    fileUtil.fileExists(step.inProgressFile) should be(false)
  }

  it should "succeed when there are no required input files" in {
    new Step1(None).runPipeline()
    fileUtil.fileExists("src/test/resources/step1") should be(true)
    fileUtil.deleteFile("src/test/resources/step1")
  }

  it should "run substeps when necessary" in {
    new Step3(None).runPipeline()
    fileUtil.fileExists("src/test/resources/step3") should be(true)
    fileUtil.fileExists("src/test/resources/step2") should be(true)
    fileUtil.fileExists("src/test/resources/step1") should be(true)
    fileUtil.deleteFile("src/test/resources/step3")
    fileUtil.deleteFile("src/test/resources/step2")
    fileUtil.deleteFile("src/test/resources/step1")
  }

  it should "fail when a substep doesn't provide the correct file" in {
    val step = new Step3(None) { override val inputs: Set[(String, Option[Step])] = Set(("wrong file", Some(new Step2(None)))) }
    TestUtil.expectError(classOf[IllegalStateException], "not produce correct file", new Function() {
      override def call() {
        step.runPipeline()
      }
    })
  }

  it should "work when parameters are needed" in {
    val params: JValue = ("filename" -> "stepWithParams") ~ ("prior params" -> ("filename" -> "stepWithPriorParams"))
    new StepWithParams(Some(params)).runPipeline()
    fileUtil.fileExists("src/test/resources/stepWithParams") should be(true)
    fileUtil.fileExists("src/test/resources/stepWithPriorParams") should be(true)
    fileUtil.fileExists("src/test/resources/param_file") should be(true)
    fileUtil.fileExists("src/test/resources/param_file2") should be(true)
    fileUtil.deleteFile("src/test/resources/stepWithParams")
    fileUtil.deleteFile("src/test/resources/stepWithPriorParams")
    fileUtil.deleteFile("src/test/resources/param_file")
    fileUtil.deleteFile("src/test/resources/param_file2")
  }

  it should "crash when parameters don't match" in {
    val params: JValue = ("filename" -> "stepWithParams") ~ ("prior params" -> ("filename" -> "stepWithPriorParams"))
    fileUtil.touchFile("src/test/resources/stepWithPriorParams")
    fileUtil.writeContentsToFile("src/test/resources/param_file2", "{\"bad params\": true}\n")
    val step = new StepWithParams(Some(params))
    TestUtil.expectError(classOf[IllegalStateException], "don't match", new Function() {
      override def call() {
        step.runPipeline()
      }
    })
    fileUtil.fileExists("src/test/resources/stepWithParams") should be(false)
    fileUtil.fileExists("src/test/resources/param_file") should be(false)
    fileUtil.fileExists("src/test/resources/stepWithPriorParams") should be(true)
    fileUtil.fileExists("src/test/resources/param_file2") should be(true)
    fileUtil.deleteFile("src/test/resources/param_file2")
    fileUtil.deleteFile("src/test/resources/stepWithPriorParams")
  }

  it should "only run substeps once, even when multiple files are required from the same step" in {
    new MultipleRequires(None).runPipeline()
    fileUtil.fileExists("src/test/resources/multiple_outputs_1") should be(true)
    fileUtil.fileExists("src/test/resources/multiple_outputs_2") should be(true)
    fileUtil.fileExists("src/test/resources/multiple_outputs_3") should be(true)
    fileUtil.fileExists("src/test/resources/multiple_inputs") should be(true)
    fileUtil.deleteFile("src/test/resources/multiple_outputs_1")
    fileUtil.deleteFile("src/test/resources/multiple_outputs_2")
    fileUtil.deleteFile("src/test/resources/multiple_outputs_3")
    fileUtil.deleteFile("src/test/resources/multiple_inputs")
    MultipleProvides.timesRun should be(1)
  }

  it should "only run substeps once, even when running substeps in parallel" in {
    val step = new MultipleRequires(None) { override val runSubstepsInParallel = true }
    step.runPipeline()
    fileUtil.fileExists("src/test/resources/multiple_outputs_1") should be(true)
    fileUtil.fileExists("src/test/resources/multiple_outputs_2") should be(true)
    fileUtil.fileExists("src/test/resources/multiple_outputs_3") should be(true)
    fileUtil.fileExists("src/test/resources/multiple_inputs") should be(true)
    fileUtil.deleteFile("src/test/resources/multiple_outputs_1")
    fileUtil.deleteFile("src/test/resources/multiple_outputs_2")
    fileUtil.deleteFile("src/test/resources/multiple_outputs_3")
    fileUtil.deleteFile("src/test/resources/multiple_inputs")
    MultipleProvides.timesRun should be(1)
  }
}
