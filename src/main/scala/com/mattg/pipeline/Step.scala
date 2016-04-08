package com.mattg.pipeline

import com.mattg.util.FileUtil

import org.json4s._
import org.json4s.native.JsonMethods._

/**
 * A Step is one piece of a pipeline designed to carry out some experiment workflow.  A Step
 * requires certain inputs, and it might know what other steps provide those inputs.  A Step also
 * produces some outputs.  This class contains logic for hooking together Steps, so that you can
 * tell the final Step in a pipeline that it should run, and it will run whatever pre-requisites
 * are necessary, then run its own work.
 *
 * Steps can optionally take parameters.  When the Step takes a set of parameters, it will save
 * those parameters to the filesystem, and check that the parameters match when a file in the
 * pipeline already exists.  Because of the way inputs() works, this also means that Steps later
 * in the pipeline need to have information in their parameters about _all_ of the Steps prior to
 * them in the pipeline (or you need something like Guice so that later Steps can construct earlier
 * Steps without having those parameters themselves).  Personally, I think this is a feature, not a
 * bug, for experiment pipelines - you have a single file or block of code that specifies an
 * experiment in its entirety, which the final Step (probably something that computes metrics) has
 * access to.  This has the nice property of a single, clear parameter file being associated with
 * every output file in your experiments.  No more wondering what the parameters were that produced
 * a particular output you're looking at.  (You still have to worry about code versions, though...
 * TODO(matt): put some git hash logging into this.)
 *
 * Note that there's a difference between passing None and Some(JNothing) to a step.  The semantics
 * of passing None means that _there are no configurable parameters_, so none will be checked for,
 * and none will be saved.  Passing Some(JNothing) just means (typically) that default values
 * will be used for all configurable parameters.
 *
 * TODO(matt): figure out the right way to do parallel execution of Steps.
 *
 * Note that these Steps are defined in terms of their inputs and outputs in the filesystem.  It's
 * up to the caller to determine whether to use absolute or relative paths in these steps.
 *
 * Another important point: in the typical use case for this pipeline (i.e., unless you decide to
 * use Guice), _all_ Step objects in the whole pipeline will be constructed when the endpoint is
 * constructed, because of how the inputs() method works.  Make sure that your constructors are all
 * very lightweight - do NOT load any data or other resources in the constructor; if you want the
 * data to be a class variable, make sure it's a lazy val.
 */
abstract class Step(val params: Option[JValue], fileUtil: FileUtil = new FileUtil) {

  /**
   * Run the pipeline up to and including this step.  If there are required input files that are
   * not already present, we try to compute them using the Steps given by the inputs() method.
   *
   * Note that we do NOT check if the files provided by this step already exist.  We assume that if
   * you're calling this method on this object, you want to run this step no matter what.  Best
   * practice is to have a main method in your code that calls runPipeline on a summary class that
   * just prints some stuff to stdout (or, in general, just has no output files).
   */
  def runPipeline() {
    println(s"Running pipeline for step: $name")
    // Because this is currently sequential, we don't need to do anything fancy to make sure steps
    // are only run once - if a step provides multiple files, it'll produce them after it's run the
    // first time, and the fileExists() check will return true for all subsequent files.  If we run
    // this is parallel, we need to be smarter to be sure that any given step only runs once.
    for ((filename, stepOption) <- inputs) {
      if (!fileUtil.fileExists(filename)) {
        println(s"Missing required file $filename; trying to create it")
        stepOption match {
          case None => throw new IllegalStateException(s"No step given to produce required file $filename")
          case Some(step) => {
            // Make sure that this step actually provides the file.  If it does, run its pipeline.
            if (step.outputs.contains(filename)) {
              step.runPipeline()
            } else {
              throw new IllegalStateException(s"Given substep (${step.name}) does not produce correct file: $filename not in ${step.outputs}")
            }
          }
        }
      } else {
        println(s"Required file $filename already exists, checking parameters")
        stepOption match {
          case None => { }  // nothing to do here; this file was created outside of this pipeline
          case Some(step) => {
            step.params match {
              case None => { }  // nothing to do here; there were no parameters for this step
              case Some(p) => {
                // Make sure that the Step's parameters match the saved parameters for this file.
                val savedParams = parse(fileUtil.readFileContents(step.paramFile))
                if (!parametersMatch(savedParams, p)) {
                  println(s"saved params: ${pretty(render(savedParams))}")
                  println(s"params: ${pretty(render(p))}")
                  println(s"diff: ${p diff savedParams}")
                  throw new IllegalStateException(s"Saved parameters for step ${step.name} don't match!")
                }
              }
            }
          }
        }
      }
    }
    println(s"All prerequisites should now be present.  Running step: $name")
    runStep()
  }

  /**
   * json4s crashes if you try to render JNothing.  But, I think JNothing is a better
   * representation of "use all default parameters" than JObject(List()), so I'm going to stick
   * with JNothing.  That means I have to do this silly mapping from JNothing to JObject(List())
   * when saving the parameters, and the reverse mapping when loading them.
   *
   * An additional benefit of doing it this way, though, is that you now have the option to
   * override this method, if there are some parameters you want to ignore when deciding if the
   * saved parameters match the current parameters.
   */
  def parametersMatch(loadedParams: JValue, currentParams: JValue): Boolean = {
    val normalized = loadedParams match {
      case JObject(List()) => JNothing
      case jval => jval
    }
    currentParams == normalized
  }

  /**
   * Once we've determined that all of the required input files are present, run the work defined
   * by this step of the pipeline.  In this method, we save a parameter file, then call the
   * (abstract) method that actually does the computation.
   *
   * TODO(matt): add checks for parallel execution in here (like, e.g., checking for an
   * "in_progress" file; in that case, we wait for the file to be removed, then skip _runStep()).
   */
  def runStep() {
    params match {
      case None => { }
      case Some(p) => {
        fileUtil.mkdirsForFile(paramFile)
        val normalized = p match {
          case JNothing => JObject(List())
          case jval => jval
        }
        fileUtil.writeContentsToFile(paramFile, pretty(render(normalized)))
      }
    }
    _runStep()
  }

  /**
   * This is what you override to actually do stuff in this step.
   */
  protected def _runStep()

  /**
   * If this step takes parameters, we save them to this location.  This is for two reasons: (1)
   * you may run an experiment one day, then two months later come back and want to know what
   * parameters were used to produce a particular output.  Having a paramFile ensures that you know
   * what parameters were used.  (2) You might change some parameters, but forget to change others,
   * or use the same output filenames for a step even though you changed the parameters.  This can
   * cause enormous confusion and silent errors.  Having a paramFile lets us ensure that you have
   * no such errors in your experiments.
   */
  def paramFile: String = throw new IllegalStateException("I have parameters, but no param file!")

  /**
   * The step name isn't really used anywhere except for logging things.  You can override it if
   * you want, or just ignore it.
   */
  def name: String = "no name"

  /**
   * What are the output files of this step?
   */
  def outputs: Set[String]

  /**
   * What file inputs does this step require, and where do you expect to get them from?  If you
   * expect them to be inputs from outside this pipeline, you use None instead of Some(Step).
   */
  def inputs: Set[(String, Option[Step])]
}
