package com.mattg.util

import org.scalatest._

import com.mattg.util.TestUtil.Function

class StepSpec extends FlatSpecLike with Matchers {
  val fileUtil = new FileUtil
  class Step0 extends Step {
    override def runStep() = fileUtil.touchFile("src/test/resources/step0")
    override def name() = "step 0"
    override def provided() = Set("src/test/resources/step0")
    override def required() = Set(("src/test/resources/empty_file.txt", None))
  }
  class Step1 extends Step {
    override def runStep() = fileUtil.touchFile("src/test/resources/step1")
    override def name() = "step 1"
    override def provided() = Set("src/test/resources/step1")
    override def required() = Set()
  }
  class Step2 extends Step {
    override def runStep() = fileUtil.touchFile("src/test/resources/step2")
    override def name() = "step 2"
    override def provided() = Set("src/test/resources/step2")
    override def required() = Set(("src/test/resources/step1", Some(new Step1)))
  }
  class Step3 extends Step {
    override def runStep() = fileUtil.touchFile("src/test/resources/step3")
    override def name() = "step 3"
    override def provided() = Set("src/test/resources/step3")
    override def required() = Set(("src/test/resources/step2", Some(new Step2)))
  }

  "runPipeline" should "succeed when a required input file is already present" in {
    new Step0().runPipeline()
    fileUtil.fileExists("src/test/resources/step0") should be(true)
    fileUtil.deleteFile("src/test/resources/step0")
  }

  it should "fail when a required input file is missing" in {
    val step = new Step0() { override def required() = Set(("src/test/non-existant-file", None)) }
    TestUtil.expectError(classOf[IllegalStateException], "No step given", new Function() {
      override def call() {
        step.runPipeline()
      }
    })
  }

  it should "succeed when there are no required input files" in {
    new Step1().runPipeline()
    fileUtil.fileExists("src/test/resources/step1") should be(true)
    fileUtil.deleteFile("src/test/resources/step1")
  }

  it should "run substeps when necessary" in {
    new Step3().runPipeline()
    fileUtil.fileExists("src/test/resources/step3") should be(true)
    fileUtil.fileExists("src/test/resources/step2") should be(true)
    fileUtil.fileExists("src/test/resources/step1") should be(true)
    fileUtil.deleteFile("src/test/resources/step3")
    fileUtil.deleteFile("src/test/resources/step2")
    fileUtil.deleteFile("src/test/resources/step1")
  }

  it should "fail when a substep doesn't provide the correct file" in {
    val step = new Step3() { override def required() = Set(("wrong file", Some(new Step2))) }
    TestUtil.expectError(classOf[IllegalStateException], "not produce correct file", new Function() {
      override def call() {
        step.runPipeline()
      }
    })
  }
}
