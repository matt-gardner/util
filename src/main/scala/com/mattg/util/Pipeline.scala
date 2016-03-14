package com.mattg.util

/**
 * A Step is one piece of a pipeline designed to carry out some experiment workflow.  A Step
 * requires certain inputs, and it might know what other steps provide those inputs.  A Step also
 * produces some outputs.  This class contains logic for hooking together Steps, so that you can
 * tell the final Step in a pipeline that it should run, and it will run whatever pre-requisites
 * are necessary, then run its own work.
 *
 * Note that these Steps are defined in terms of their inputs and outputs in the filesystem.  It's
 * up to the caller to determine whether to use absolute or relative paths in these steps.
 */
abstract class Step(fileUtil: FileUtil = new FileUtil) {

  /**
   * Run the pipeline up to and including this step.  If there are required input files that are
   * not already present, we try to compute them using the Steps given by the required() method.
   */
  def runPipeline() {
    println(s"Running pipeline for step: $name()")
    for ((filename, stepOption) <- required()) {
      // We only need to do anything here if the required files do not already exist.
      if (!fileUtil.fileExists(filename)) {
        println(s"Missing required file $filename")
        stepOption match {
          case None => throw new IllegalStateException(s"No step given to produce required file $filename")
          case Some(step) => {
            // Make sure that this step actually provides the file.  If it does, run its pipeline.
            if (step.provided().contains(filename)) {
              step.runPipeline()
            } else {
              throw new IllegalStateException("Given substep does not produce correct file: $filename not in ${step.provided()}")
            }
          }
        }
      }
    }
    println("All prerequisites should now be present.  Running this step $name")
    runStep()
  }

  /**
   * Once we've determined that all of the required input files are present, run the work defined
   * by this step of the pipeline.
   */
  protected def runStep()

  def name(): String

  def provided(): Set[String]

  def required(): Set[(String, Option[Step])]
}
