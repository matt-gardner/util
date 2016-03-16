package com.mattg.util

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._

import com.mattg.util.TestUtil.Function

class StepSpec extends FlatSpecLike with Matchers {
  val fileUtil = new FileUtil
  implicit val formats = DefaultFormats

  class Step0(params: Option[JValue]) extends Step(params) {
    override def _runStep() = fileUtil.touchFile("src/test/resources/step0")
    override def name() = "step 0"
    override def outputs() = Set("src/test/resources/step0")
    override def inputs() = Set(("src/test/resources/empty_file.txt", None))
  }

  class Step1(params: Option[JValue]) extends Step(params) {
    override def _runStep() = fileUtil.touchFile("src/test/resources/step1")
    override def name() = "step 1"
    override def outputs() = Set("src/test/resources/step1")
    override def inputs() = Set()
  }

  class Step2(params: Option[JValue]) extends Step(params) {
    override def _runStep() = fileUtil.touchFile("src/test/resources/step2")
    override def name() = "step 2"
    override def outputs() = Set("src/test/resources/step2")
    override def inputs() = Set(("src/test/resources/step1", Some(new Step1(None))))
  }

  class Step3(params: Option[JValue]) extends Step(params) {
    override def _runStep() = fileUtil.touchFile("src/test/resources/step3")
    override def name() = "step 3"
    override def outputs() = Set("src/test/resources/step3")
    override def inputs() = Set(("src/test/resources/step2", Some(new Step2(None))))
  }

  class StepWithPriorParams(params: Option[JValue]) extends Step(params) {
    val filename = (params.get \ "filename").extract[String]
    override def _runStep() = fileUtil.touchFile(s"src/test/resources/$filename")
    override def paramFile() = "src/test/resources/param_file2"
    override def name() = "step with prior params"
    override def outputs() = Set(s"src/test/resources/$filename")
    override def inputs() = Set()
  }

  class StepWithParams(params: Option[JValue]) extends Step(params) {
    val filename = (params.get \ "filename").extract[String]
    val priorStepParams = (params.get \ "prior params")
    val priorFilename = (priorStepParams \ "filename").extract[String]
    override def _runStep() = fileUtil.touchFile(s"src/test/resources/$filename")
    override def paramFile() = "src/test/resources/param_file"
    override def name() = "step with params"
    override def outputs() = Set(s"src/test/resources/$filename")
    override def inputs() = Set(
      (s"src/test/resources/$priorFilename", Some(new StepWithPriorParams(Some(priorStepParams)))))
  }

  class MultipleProvides(params: Option[JValue]) extends Step(params) {
    MultipleProvides.timesRun = 0
    override def _runStep() = {
      fileUtil.touchFile(s"src/test/resources/multiple_outputs_1")
      fileUtil.touchFile(s"src/test/resources/multiple_outputs_2")
      fileUtil.touchFile(s"src/test/resources/multiple_outputs_3")
      MultipleProvides synchronized { MultipleProvides.timesRun += 1 }
    }
    override def name() = "multiple outputs"
    override def outputs() = Set(
      "src/test/resources/multiple_outputs_1",
      "src/test/resources/multiple_outputs_2",
      "src/test/resources/multiple_outputs_3"
    )
    override def inputs() = Set()
  }
  object MultipleProvides {
    var timesRun = 0
  }

  class MultipleRequires(params: Option[JValue]) extends Step(params) {
    val subStep = new MultipleProvides(None)
    override def _runStep() = fileUtil.touchFile(s"src/test/resources/multiple_inputs")
    override def name() = "multiple_inputs"
    override def outputs() = Set(s"src/test/resources/multiple_inputs")
    override def inputs() = Set(
      ("src/test/resources/multiple_outputs_1", Some(subStep)),
      ("src/test/resources/multiple_outputs_2", Some(subStep)),
      ("src/test/resources/multiple_outputs_3", Some(subStep))
    )
  }

  "runPipeline" should "succeed when a required input file is already present" in {
    new Step0(None).runPipeline()
    fileUtil.fileExists("src/test/resources/step0") should be(true)
    fileUtil.deleteFile("src/test/resources/step0")
  }

  it should "fail when a required input file is missing" in {
    val step = new Step0(None) { override def inputs() = Set(("src/test/non-existant-file", None)) }
    TestUtil.expectError(classOf[IllegalStateException], "No step given", new Function() {
      override def call() {
        step.runPipeline()
      }
    })
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
    val step = new Step3(None) { override def inputs() = Set(("wrong file", Some(new Step2(None)))) }
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
}
