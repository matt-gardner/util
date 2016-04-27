package com.mattg.util

import org.scalatest._

import com.mattg.util.TestUtil.Function

import org.json4s._
import org.json4s.JsonDSL._

class JsonHelperSpec extends FlatSpecLike with Matchers {

  val params: JValue = ("param1" -> "val1") ~ ("param list" -> List("item 1", "item 2"))

  "extractWithDefault" should "return the parameter when it is present" in {
    JsonHelper.extractWithDefault(params, "param1", "default") should be("val1")
  }

  it should "return the default when the parameter is not present" in {
    JsonHelper.extractWithDefault(params, "not present", "default key") should be("default key")
  }

  it should "parse lists correctly" in {
    JsonHelper.extractWithDefault(params, "param list", Seq[String]()) should be(Seq("item 1", "item 2"))
  }

  it should "return default lists when the given value isn't present" in {
    JsonHelper.extractWithDefault(params, "not present", Seq("default")) should be(Seq("default"))
  }

  "extractChoice" should "return the parameter when it is present" in {
    JsonHelper.extractChoice(params, "param1", Seq("val1")) should be("val1")
  }

  it should "throw an error when the parameter is not in the allowed options" in {
    TestUtil.expectError(classOf[IllegalStateException], "not a member of", new Function() {
      override def call() {
        JsonHelper.extractChoice(params, "param1", Seq("option 1", "option 2"))
      }
    })
  }

  "extractChoiceWithDefault" should "return the parameter when it is present" in {
    JsonHelper.extractChoiceWithDefault(params, "param1", Seq("val1"), "default") should be("val1")
  }

  it should "return the default when the parameter is not present" in {
    JsonHelper.extractChoiceWithDefault(params, "not present", Seq("val1", "default"), "default") should be("default")
  }

  it should "throw an error when the parameter is not in the allowed options" in {
    TestUtil.expectError(classOf[IllegalStateException], "not a member of", new Function() {
      override def call() {
        JsonHelper.extractChoiceWithDefault(params, "param1", Seq("option 1", "option 2"), "default")
      }
    })
  }
}
