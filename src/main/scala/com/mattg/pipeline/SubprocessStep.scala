package com.mattg.pipeline

import org.json4s.JValue

import com.mattg.util.FileUtil

import scala.sys.process.Process

/**
 * This Step calls a subprocess.
 *
 * When you need to include JVM-incompatible code as part of a pipeline, you can use this Step.
 * The basic assumption is that you call some executable binary, perhaps giving it a path to a
 * script (like `python src/main/python/script_name.py`), and some arguments.  This class will use
 * scala.sys.process to run the command as it's given, wait for a return code, and throw an
 * exception if the return code is not 0.
 *
 * There are three methods / vals to override here, defining the binary, the scriptFile, and the
 * arguments.  These will be put together in that order in order to run the subprocess.
 */
abstract class SubprocessStep(
  params: Option[JValue],
  fileUtil: FileUtil
) extends Step(params, fileUtil) {
  def binary: String
  def scriptFile: Option[String]
  def arguments: Seq[String]

  override def _runStep() {
    val commandToExecute = Seq(binary) ++ scriptFile.toSeq ++ arguments
    val commandStr = commandToExecute.mkString(" ")
    logger.info(s"Starting subprocess with command: $commandStr")

    val process = Process(commandToExecute)
    val exitCode = process.!
    if (exitCode != 0) {
      throw new RuntimeException(s"Subprocess returned non-zero exit code: $exitCode")
    }
  }
}
